package com.example.wallet.dto;

import com.example.wallet.entity.TransactionStatus;

import java.math.BigDecimal;

/** transactionId is the id of the sender's TRANSFER_OUT history entry. */
public record TransferResponse(Long transactionId, Long fromWalletId, Long toWalletId,
                               BigDecimal amount, TransactionStatus status) {
}
