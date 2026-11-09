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
        checkId(tx, errors);
        checkAmount(tx.amount(), errors);
        checkCurrency(tx.currency(), errors);
        checkAccounts(tx, errors);

        boolean valid = errors.isEmpty();
        registry.counter("transactions_validated", "result", valid ? "valid" : "invalid").increment();
        return new ValidationResult(tx.transactionId(), valid, errors);
    }

    private void checkId(Transaction tx, List<String> errors) {
        if (tx.transactionId() == null || tx.transactionId().isBlank()) {
            errors.add("transactionId is required");
        }
    }

    private void checkAmount(BigDecimal amount, List<String> errors) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("amount must be greater than zero");
            return;
        }
        if (amount.compareTo(props.maxAmount()) >= 0) {
            errors.add("amount exceeds the limit of " + props.maxAmount());
        }
        if (amount.scale() > 2) {
            errors.add("amount cannot have more than 2 decimal places");
        }
    }

    private void checkCurrency(String currency, List<String> errors) {
        if (currency == null || !props.allowedCurrencies().contains(currency)) {
            errors.add("currency must be one of " + props.allowedCurrencies());
        }
    }

    private void checkAccounts(Transaction tx, List<String> errors) {
        if (!isValidAccount(tx.sourceAccount())) {
            errors.add("sourceAccount is not a valid IBAN");
        }
        if (!isValidAccount(tx.destinationAccount())) {
            errors.add("destinationAccount is not a valid IBAN");
        }
        if (tx.sourceAccount() != null && tx.sourceAccount().equals(tx.destinationAccount())) {
            errors.add("sourceAccount and destinationAccount must be different");
        }
    }

    private boolean isValidAccount(String account) {
        return account != null && ACCOUNT.matcher(account).matches();
    }
}
