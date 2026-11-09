package com.finsecure.pay.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.finsecure.pay.model.Transaction;
import com.finsecure.pay.model.ValidationResult;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class TransactionValidatorTest {

    private static final String SRC = "AE070331234567890123456";
    private static final String DST = "GB29NWBK60161331926819";

    private TransactionValidator validator;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        validator = new TransactionValidator(
                new ValidationProperties(new BigDecimal("10000"), Set.of("USD", "EUR", "GBP", "AED")), registry);
    }

    private Transaction tx(String amount, String currency, String src, String dst) {
        return new Transaction("TX-1001", amount == null ? null : new BigDecimal(amount), currency, src, dst);
    }

    @Test
    void validTransactionPasses() {
        ValidationResult result = validator.validate(tx("250.00", "AED", SRC, DST));
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void zeroAmountFails() {
        ValidationResult result = validator.validate(tx("0", "USD", SRC, DST));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("amount must be greater than zero");
    }

    @Test
    void negativeAmountFails() {
        assertThat(validator.validate(tx("-50", "USD", SRC, DST)).valid()).isFalse();
    }

    @Test
    void amountOverLimitFails() {
        ValidationResult result = validator.validate(tx("10000.01", "USD", SRC, DST));
        assertThat(result.errors()).contains("amount exceeds the limit of 10000");
    }

    @Test
    void amountAtLimitPasses() {
        assertThat(validator.validate(tx("10000", "USD", SRC, DST)).valid()).isTrue();
    }

    @Test
    void tooManyDecimalsFails() {
        ValidationResult result = validator.validate(tx("10.005", "USD", SRC, DST));
        assertThat(result.errors()).contains("amount cannot have more than 2 decimal places");
    }

    @Test
    void unsupportedCurrencyFails() {
        assertThat(validator.validate(tx("100", "BTC", SRC, DST)).valid()).isFalse();
    }

    @Test
    void invalidAccountFails() {
        ValidationResult result = validator.validate(tx("100", "USD", "12345", DST));
        assertThat(result.errors()).contains("sourceAccount is not a valid IBAN");
    }

    @Test
    void sameAccountFails() {
        ValidationResult result = validator.validate(tx("100", "USD", SRC, SRC));
        assertThat(result.errors()).contains("sourceAccount and destinationAccount must be different");
    }

    @Test
    void missingFieldsReportAllErrors() {
        ValidationResult result = validator.validate(new Transaction(null, null, null, null, null));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(5);
    }

    @Test
    void metricsAreCounted() {
        validator.validate(tx("100", "USD", SRC, DST));
        validator.validate(tx("0", "USD", SRC, DST));
        assertThat(registry.counter("transactions_validated", "result", "valid").count()).isEqualTo(1);
        assertThat(registry.counter("transactions_validated", "result", "invalid").count()).isEqualTo(1);
    }
}
