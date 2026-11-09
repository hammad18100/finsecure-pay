package com.finsecure.pay.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.finsecure.pay.model.Transaction;
import com.finsecure.pay.model.ValidationResult;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class TransactionValidator {

    private static final Pattern ACCOUNT = Pattern.compile("^[A-Z]{2}\\d{2}[A-Z0-9]{10,30}$");

    private final ValidationProperties props;
    private final MeterRegistry registry;

    public TransactionValidator(ValidationProperties props, MeterRegistry registry) {
        this.props = props;
        this.registry = registry;
    }

    public ValidationResult validate(Transaction tx) {
        List<String> errors = new ArrayList<>();

        if (tx.transactionId() == null || tx.transactionId().isBlank()) {
            errors.add("transactionId is required");
        }
        if (tx.amount() == null || tx.amount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("amount must be greater than zero");
        } else {
            if (tx.amount().compareTo(props.maxAmount()) > 0) {
                errors.add("amount exceeds the limit of " + props.maxAmount());
            }
            if (tx.amount().scale() > 2) {
                errors.add("amount cannot have more than 2 decimal places");
            }
        }
        if (tx.currency() == null || !props.allowedCurrencies().contains(tx.currency())) {
            errors.add("currency must be one of " + props.allowedCurrencies());
        }
        if (!isValidAccount(tx.sourceAccount())) {
            errors.add("sourceAccount is not a valid IBAN");
        }
        if (!isValidAccount(tx.destinationAccount())) {
            errors.add("destinationAccount is not a valid IBAN");
        }
        if (tx.sourceAccount() != null && tx.sourceAccount().equals(tx.destinationAccount())) {
            errors.add("sourceAccount and destinationAccount must be different");
        }

        boolean valid = errors.isEmpty();
        registry.counter("transactions_validated", "result", valid ? "valid" : "invalid").increment();
        return new ValidationResult(tx.transactionId(), valid, errors);
    }

    private boolean isValidAccount(String account) {
        return account != null && ACCOUNT.matcher(account).matches();
    }
}
