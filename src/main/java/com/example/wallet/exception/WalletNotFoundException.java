package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class WalletNotFoundException extends BusinessException {
    public WalletNotFoundException(Long walletId) {
        super(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found with id " + walletId);
    }
}
