package com.example.backend.models;

import java.math.BigDecimal;

// budget i remaining su null kad kategorija nema budzet u tom mesecu
public record CategorySummary(long categoryId, String name, BigDecimal spent,
        BigDecimal budget, BigDecimal remaining) {
}