package com.finsecure.pay.model;

import java.math.BigDecimal;

public record Transaction(
        String transactionId,
        BigDecimal amount,
        String currency,
        String sourceAccount,
        String destinationAccount) {
}
