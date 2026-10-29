package com.example.backend.models;

import java.time.LocalDate;

public record TransactionFilter(LocalDate from, LocalDate to, Long categoryId, String type, String q) {}
