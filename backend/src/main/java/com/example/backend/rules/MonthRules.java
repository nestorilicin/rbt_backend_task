package com.example.backend.rules;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public final class MonthRules {

    private MonthRules() {
    }

    public static YearMonth parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("month is required (format YYYY-MM)");
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("month must be in the format YYYY-MM, e.g. 2026-10");
        }
        if (ym.getYear() < 1000 || ym.getYear() > 9999) {
            throw new IllegalArgumentException("year must be between 1000 and 9999");
        }
        return ym;
    }
}