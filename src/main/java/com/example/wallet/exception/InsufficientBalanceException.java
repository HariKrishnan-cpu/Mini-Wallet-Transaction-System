package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException() {
        super(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "Insufficient balance to complete the transfer");
    }
}
