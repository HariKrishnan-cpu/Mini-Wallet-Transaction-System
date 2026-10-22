package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class SameWalletTransferException extends BusinessException {
    public SameWalletTransferException() {
        super(HttpStatus.BAD_REQUEST, "SAME_WALLET_TRANSFER", "Sender and receiver wallets must be different");
    }
}
