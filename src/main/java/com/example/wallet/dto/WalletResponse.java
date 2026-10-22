package com.example.wallet.dto;

import java.math.BigDecimal;

public record WalletResponse(Long walletId, String name, String email, BigDecimal balance) {
}
