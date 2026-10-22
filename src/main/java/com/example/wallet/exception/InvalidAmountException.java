package com.example.wallet.exception;

import org.springframework.http.HttpStatus;

public class InvalidAmountException extends BusinessException {

    private InvalidAmountException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }

    public static InvalidAmountException forDeposit() {
        return new InvalidAmountException("INVALID_DEPOSIT_AMOUNT", "Deposit amount must be greater than zero");
    }

    public static InvalidAmountException forTransfer() {
        return new InvalidAmountException("INVALID_TRANSFER_AMOUNT", "Transfer amount must be greater than zero");
    }
}
