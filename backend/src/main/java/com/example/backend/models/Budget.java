package com.example.backend.models;

import java.math.BigDecimal;

// month je String "2026-10" da JSON izgleda predvidivo
public record Budget(String month, long categoryId, BigDecimal amount) {}