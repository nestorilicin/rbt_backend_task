package com.example.backend.rules;

import java.math.BigDecimal;

public class TransactionRules {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");

    private TransactionRules(){}

    public static String type(String raw){
        String t = raw == null ? "" : raw.strip().toUpperCase();
        if(!t.equals("INCOME") && !t.equals("EXPENSE")){
            throw new IllegalArgumentException("type must be INCOME or EXPENSE");
        }
        return t;
    }

    public static BigDecimal amount(BigDecimal a) {
        if (a == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (a.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
        if (a.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("amount must have at most 2 decimals");
        }
        if (a.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("amount must be at most 1000000.00");
        }
        return a;
    }

    public static String description(String raw) {
        if (raw == null) return null;
        String d = raw.strip();
        if (d.isEmpty()) return null;
        if (d.length() > 200) {
            throw new IllegalArgumentException("description must be at most 200 characters");
        }
        return d;
    }
}
