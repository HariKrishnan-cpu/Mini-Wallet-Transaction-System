package com.example.wallet.service;

import com.example.wallet.dto.CreateWalletRequest;
import com.example.wallet.dto.DepositRequest;
import com.example.wallet.dto.DepositResponse;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.dto.TransferRequest;
import com.example.wallet.dto.TransferResponse;
import com.example.wallet.dto.WalletResponse;
import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.entity.Wallet;
import com.example.wallet.entity.WalletTransaction;
import com.example.wallet.exception.DuplicateEmailException;
import com.example.wallet.exception.InsufficientBalanceException;
import com.example.wallet.exception.InvalidAmountException;
import com.example.wallet.exception.SameWalletTransferException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.repository.WalletTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {

    private static final int MONEY_SCALE = 2;

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    public WalletServiceImpl(WalletRepository walletRepository,
                             WalletTransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    // ------------------------------------------------------------------ create

    @Override
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        BigDecimal initialBalance = request.initialBalance() == null
                ? BigDecimal.ZERO
                : request.initialBalance();
        if (initialBalance.signum() < 0) {
            throw InvalidAmountException.forDeposit(); // defensive: bean validation already blocks this
        }

        if (walletRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        Wallet wallet = new Wallet(request.name().trim(), email, scale(initialBalance), LocalDateTime.now());
        try {
            // flush now so the DB unique constraint fires here (covers the check-then-insert race)
            wallet = walletRepository.saveAndFlush(wallet);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException();
        }
        return toResponse(wallet);
    }

    // ----------------------------------------------------------------- deposit

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepositResponse deposit(Long walletId, DepositRequest request) {
        BigDecimal amount = scale(request.amount());
        if (amount.signum() <= 0) {
            throw InvalidAmountException.forDeposit();
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        BigDecimal previousBalance = wallet.getBalance();
        wallet.credit(amount);
        walletRepository.saveAndFlush(wallet);

        transactionRepository.save(new WalletTransaction(
                wallet, TransactionType.DEPOSIT, amount, TransactionStatus.SUCCESS,
                UUID.randomUUID().toString(), LocalDateTime.now()));

        return new DepositResponse(wallet.getId(), previousBalance, amount, wallet.getBalance());
    }

    // ---------------------------------------------------------------- transfer

    /**
     * Atomic transfer: both balance updates and both history rows commit together or not at all.
     * Any exception (checked or unchecked) rolls the whole database transaction back.
     * Wallets are locked (SELECT ... FOR UPDATE) in ascending id order so that two opposite
     * transfers (A->B and B->A) cannot deadlock and concurrent transfers cannot overdraw a wallet.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferResponse transfer(TransferRequest request) {
        BigDecimal amount = scale(request.amount());
        if (amount.signum() <= 0) {
            throw InvalidAmountException.forTransfer();
        }
        Long fromId = request.fromWalletId();
        Long toId = request.toWalletId();
        if (fromId.equals(toId)) {
            throw new SameWalletTransferException();
        }

        // Lock in a deterministic order (lowest id first) to avoid deadlocks.
        Wallet first = lockWallet(Math.min(fromId, toId));
        Wallet second = lockWallet(Math.max(fromId, toId));
        Wallet sender = fromId.equals(first.getId()) ? first : second;
        Wallet receiver = sender == first ? second : first;

        if (!sender.hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException();
        }

        sender.debit(amount);
        receiver.credit(amount);
        walletRepository.saveAndFlush(sender);
        walletRepository.saveAndFlush(receiver);

        String referenceId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        WalletTransaction out = new WalletTransaction(sender, TransactionType.TRANSFER_OUT, amount,
                TransactionStatus.SUCCESS, referenceId, now);
        WalletTransaction in = new WalletTransaction(receiver, TransactionType.TRANSFER_IN, amount,
                TransactionStatus.SUCCESS, referenceId, now);
        List<WalletTransaction> saved = transactionRepository.saveAll(List.of(out, in));

        return new TransferResponse(saved.get(0).getId(), sender.getId(), receiver.getId(),
                amount, TransactionStatus.SUCCESS);
    }

    // -------------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long walletId) {
        return walletRepository.findById(walletId)
                .map(this::toResponse)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(Long walletId) {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        return transactionRepository.findByWalletIdOrderByCreatedAtDescIdDesc(walletId).stream()
                .map(t -> new TransactionResponse(t.getId(), t.getType(), t.getAmount(),
                        t.getStatus(), t.getCreatedAt()))
                .toList();
    }

    // ----------------------------------------------------------------- helpers

    private Wallet lockWallet(Long walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private WalletResponse toResponse(Wallet w) {
        return new WalletResponse(w.getId(), w.getName(), w.getEmail(), w.getBalance());
    }
}
