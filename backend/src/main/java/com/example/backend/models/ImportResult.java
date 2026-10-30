package com.example.backend.models;

import java.util.List;

public record ImportResult(int imported, int failed, List<RowError> errors, boolean errorsTruncated) {}