package com.opsvision.evidence.parser;

import com.opsvision.evidence.model.FindingSeverity;

/**
 * Maps scanner-specific severity labels onto the internal {@link FindingSeverity} enum.
 */
final class SeverityMapper {

    private SeverityMapper() {
    }

    static FindingSeverity fromScanner(String raw) {
        if (raw == null || raw.isBlank()) {
            return FindingSeverity.UNKNOWN;
        }
        String value = raw.trim().toUpperCase();
        return switch (value) {
            case "CRITICAL", "CRIT" -> FindingSeverity.CRITICAL;
            case "HIGH", "ERROR" -> FindingSeverity.HIGH;
            case "MEDIUM", "MODERATE", "WARNING", "WARN" -> FindingSeverity.MEDIUM;
            case "LOW" -> FindingSeverity.LOW;
            case "INFO", "INFORMATIONAL", "NOTE" -> FindingSeverity.INFO;
            default -> FindingSeverity.UNKNOWN;
        };
    }
}
