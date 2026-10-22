package com.example.wallet.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** The "greater than zero" rule is enforced in the service so it maps to INVALID_DEPOSIT_AMOUNT. */
public record DepositRequest(
        @NotNull(message = "amount is mandatory")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount
) {
}
