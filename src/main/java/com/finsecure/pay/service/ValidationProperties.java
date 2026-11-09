package com.finsecure.pay.service;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "validation")
public record ValidationProperties(BigDecimal maxAmount, Set<String> allowedCurrencies) {
}
