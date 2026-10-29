package com.example.backend.models;

import java.math.BigDecimal;
import java.util.List;

public record MonthlySummary(String month, BigDecimal totalIncome, BigDecimal totalExpense,
        BigDecimal balance, List<CategorySummary> categories) {
}