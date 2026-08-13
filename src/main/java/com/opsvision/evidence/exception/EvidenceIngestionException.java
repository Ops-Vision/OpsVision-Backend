package com.opsvision.evidence.exception;

/**
 * Raised when normalized evidence cannot be ingested due to invalid input.
 */
public class EvidenceIngestionException extends RuntimeException {

    public EvidenceIngestionException(String message) {
        super(message);
    }

    public EvidenceIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
