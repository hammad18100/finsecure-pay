package com.finsecure.pay.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.finsecure.pay.model.Transaction;
import com.finsecure.pay.model.ValidationResult;
import com.finsecure.pay.service.TransactionValidator;

@RestController
public class TransactionController {

    private final TransactionValidator validator;
    private final String version;

    public TransactionController(TransactionValidator validator, @Value("${app.version}") String version) {
        this.validator = validator;
        this.version = version;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "version", version);
    }

    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate(@RequestBody Transaction tx) {
        ValidationResult result = validator.validate(tx);
        HttpStatus status = result.valid() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }
}
