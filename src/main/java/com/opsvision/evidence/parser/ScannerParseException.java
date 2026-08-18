package com.opsvision.evidence.parser;

/**
 * Raised when scanner output cannot be parsed into normalized evidence.
 */
public class ScannerParseException extends RuntimeException {

    public ScannerParseException(String message) {
        super(message);
    }

    public ScannerParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
