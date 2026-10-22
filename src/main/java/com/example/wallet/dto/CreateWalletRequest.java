package com.example.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateWalletRequest(
        @NotBlank(message = "name is mandatory")
        @Size(max = 100, message = "name must not exceed 100 characters")
        String name,

        @NotBlank(message = "email is mandatory")
        @Email(message = "email must be a valid email address")
        @Size(max = 150, message = "email must not exceed 150 characters")
        String email,

        @DecimalMin(value = "0.00", message = "initialBalance cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "initialBalance must have at most 2 decimal places")
        BigDecimal initialBalance   // optional: defaults to 0.00 when omitted
) {
}
