package com.example.wallet.service;

import com.example.wallet.dto.CreateWalletRequest;
import com.example.wallet.dto.DepositRequest;
import com.example.wallet.dto.DepositResponse;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.dto.TransferRequest;
import com.example.wallet.dto.TransferResponse;
import com.example.wallet.dto.WalletResponse;

import java.util.List;

public interface WalletService {

    WalletResponse createWallet(CreateWalletRequest request);

    DepositResponse deposit(Long walletId, DepositRequest request);

    TransferResponse transfer(TransferRequest request);

    WalletResponse getWallet(Long walletId);

    List<TransactionResponse> getTransactions(Long walletId);
}
