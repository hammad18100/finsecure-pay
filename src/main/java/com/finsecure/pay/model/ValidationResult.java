package com.finsecure.pay.model;

import java.util.List;

public record ValidationResult(String transactionId, boolean valid, List<String> errors) {
}
