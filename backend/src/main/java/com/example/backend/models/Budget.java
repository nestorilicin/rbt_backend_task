package com.example.backend.models;

import java.math.BigDecimal;

public record Budget(String month, long categoryId, BigDecimal amount) {}