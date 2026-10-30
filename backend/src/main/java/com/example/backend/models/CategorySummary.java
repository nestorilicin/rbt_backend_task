package com.example.backend.models;

import java.math.BigDecimal;

public record CategorySummary(long categoryId, String name, BigDecimal spent,
        BigDecimal budget, BigDecimal remaining) {
}