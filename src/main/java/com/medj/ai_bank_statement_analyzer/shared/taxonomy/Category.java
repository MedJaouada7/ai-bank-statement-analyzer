package com.medj.ai_bank_statement_analyzer.shared.taxonomy;

/**
 * Enum representing different categories for bank statement transactions. For type safety and
 * schema generation (the taxonomy yml file owns the definitions).
 */
public enum Category {
    GROCERIES,
    DINING,
    TRANSPORT,
    FUEL,
    SHOPPING,
    UTILITIES,
    TELECOM,
    HOUSING,
    SUBSCRIPTIONS,
    ENTERTAINMENT,
    HEALTH,
    TRAVEL,
    EDUCATION,
    INSURANCE,
    INCOME,
    TRANSFERS,
    CASH_WITHDRAWAL,
    FEES,
    OTHER
}
