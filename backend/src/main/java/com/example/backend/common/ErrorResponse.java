package com.example.backend.common;

import java.util.List;

public record ErrorResponse(int status, String error, String message, List<FieldIssue> details) {
    public record FieldIssue(String field, String message) {

    }
}
