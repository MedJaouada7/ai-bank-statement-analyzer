package com.medj.ai_bank_statement_analyzer.shared.identity;

public record UserId(String value) {
    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }
}
