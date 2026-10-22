package com.example.wallet.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "fromWalletId is mandatory")
        Long fromWalletId,

        @NotNull(message = "toWalletId is mandatory")
        Long toWalletId,

        @NotNull(message = "amount is mandatory")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount
) {
}
