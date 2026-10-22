package com.example.wallet;

import com.example.wallet.dto.CreateWalletRequest;
import com.example.wallet.dto.DepositRequest;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.dto.TransferRequest;
import com.example.wallet.dto.TransferResponse;
import com.example.wallet.dto.WalletResponse;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.exception.DuplicateEmailException;
import com.example.wallet.exception.InsufficientBalanceException;
import com.example.wallet.exception.InvalidAmountException;
import com.example.wallet.exception.SameWalletTransferException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.repository.WalletTransactionRepository;
import com.example.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

/** Not @Transactional on purpose: we want real commits/rollbacks. */
@SpringBootTest
class WalletServiceTransactionTest {

    @Autowired WalletService service;
    @Autowired WalletRepository walletRepository;
    @SpyBean WalletTransactionRepository transactionRepository;

    @BeforeEach
    void clean() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private WalletResponse wallet(String name, String email, String balance) {
        return service.createWallet(new CreateWalletRequest(name, email, bd(balance)));
    }

    @Test
    void transferMovesMoneyAndKeepsTotalUnchanged() {
        WalletResponse rahul = wallet("Rahul", "rahul@example.com", "1000.00");
        WalletResponse priya = wallet("Priya", "priya@example.com", "500.00");

        TransferResponse r = service.transfer(new TransferRequest(rahul.walletId(), priya.walletId(), bd("300.00")));

        assertThat(r.status().name()).isEqualTo("SUCCESS");
        BigDecimal a = service.getWallet(rahul.walletId()).balance();
        BigDecimal b = service.getWallet(priya.walletId()).balance();
        assertThat(a).isEqualByComparingTo("700.00");
        assertThat(b).isEqualByComparingTo("800.00");
        assertThat(a.add(b)).isEqualByComparingTo("1500.00");

        List<TransactionResponse> out = service.getTransactions(rahul.walletId());
        List<TransactionResponse> in = service.getTransactions(priya.walletId());
        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(in).hasSize(1);
        assertThat(in.get(0).type()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(transactionRepository.findAll().stream().map(t -> t.getReferenceId()).distinct()).hasSize(1);
    }

    @Test
    void failureAfterDebitRollsBackEverything() {
        WalletResponse rahul = wallet("Rahul", "rahul@example.com", "1000.00");
        WalletResponse priya = wallet("Priya", "priya@example.com", "500.00");

        // balances are already updated + flushed when history saving blows up
        doThrow(new RuntimeException("simulated failure")).when(transactionRepository).saveAll(anyList());

        assertThatThrownBy(() ->
                service.transfer(new TransferRequest(rahul.walletId(), priya.walletId(), bd("300.00"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(service.getWallet(rahul.walletId()).balance()).isEqualByComparingTo("1000.00");
        assertThat(service.getWallet(priya.walletId()).balance()).isEqualByComparingTo("500.00");
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void insufficientBalanceLeavesStateUntouched() {
        WalletResponse a = wallet("A", "a@example.com", "100.00");
        WalletResponse b = wallet("B", "b@example.com", "0.00");

        assertThatThrownBy(() -> service.transfer(new TransferRequest(a.walletId(), b.walletId(), bd("100.01"))))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(service.getWallet(a.walletId()).balance()).isEqualByComparingTo("100.00");
        assertThat(service.getWallet(b.walletId()).balance()).isEqualByComparingTo("0.00");
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void transferValidationRules() {
        WalletResponse a = wallet("A", "a@example.com", "100.00");
        WalletResponse b = wallet("B", "b@example.com", "0.00");

        assertThatThrownBy(() -> service.transfer(new TransferRequest(a.walletId(), a.walletId(), bd("10.00"))))
                .isInstanceOf(SameWalletTransferException.class);
        assertThatThrownBy(() -> service.transfer(new TransferRequest(a.walletId(), b.walletId(), bd("0.00"))))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> service.transfer(new TransferRequest(a.walletId(), b.walletId(), bd("-5.00"))))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> service.transfer(new TransferRequest(a.walletId(), 9999L, bd("10.00"))))
                .isInstanceOf(WalletNotFoundException.class);
        assertThatThrownBy(() -> service.transfer(new TransferRequest(9999L, b.walletId(), bd("10.00"))))
                .isInstanceOf(WalletNotFoundException.class);
        assertThat(service.getWallet(a.walletId()).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void depositUpdatesBalanceAndHistory() {
        WalletResponse w = wallet("Rahul", "rahul@example.com", "1000.00");

        var r = service.deposit(w.walletId(), new DepositRequest(bd("500.00")));

        assertThat(r.previousBalance()).isEqualByComparingTo("1000.00");
        assertThat(r.currentBalance()).isEqualByComparingTo("1500.00");
        List<TransactionResponse> h = service.getTransactions(w.walletId());
        assertThat(h).hasSize(1);
        assertThat(h.get(0).type()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void depositFailureDoesNotChangeBalance() {
        WalletResponse w = wallet("Rahul", "rahul@example.com", "1000.00");
        doThrow(new RuntimeException("simulated failure")).when(transactionRepository).save(any());

        assertThatThrownBy(() -> service.deposit(w.walletId(), new DepositRequest(bd("500.00"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(service.getWallet(w.walletId()).balance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void invalidDepositAndMissingWallet() {
        WalletResponse w = wallet("Rahul", "rahul@example.com", "10.00");
        assertThatThrownBy(() -> service.deposit(w.walletId(), new DepositRequest(bd("0"))))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> service.deposit(w.walletId(), new DepositRequest(bd("-1.00"))))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> service.deposit(9999L, new DepositRequest(bd("1.00"))))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() {
        wallet("Rahul", "rahul@example.com", "0.00");
        assertThatThrownBy(() -> wallet("Other", "RAHUL@Example.com", "0.00"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void historyIsNewestFirstAndEmptyWhenNoTransactions() {
        WalletResponse w = wallet("Rahul", "rahul@example.com", "0.00");
        assertThat(service.getTransactions(w.walletId())).isEmpty();

        service.deposit(w.walletId(), new DepositRequest(bd("1.00")));
        service.deposit(w.walletId(), new DepositRequest(bd("2.00")));

        List<TransactionResponse> h = service.getTransactions(w.walletId());
        assertThat(h.get(0).amount()).isEqualByComparingTo("2.00");
        assertThat(h.get(1).amount()).isEqualByComparingTo("1.00");
        assertThatThrownBy(() -> service.getTransactions(9999L)).isInstanceOf(WalletNotFoundException.class);
    }
}
