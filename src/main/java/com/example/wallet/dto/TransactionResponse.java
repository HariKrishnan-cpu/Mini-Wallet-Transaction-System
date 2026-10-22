package com.example.wallet.dto;

import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(Long transactionId, TransactionType type, BigDecimal amount,
                                  TransactionStatus status, LocalDateTime createdAt) {
}
