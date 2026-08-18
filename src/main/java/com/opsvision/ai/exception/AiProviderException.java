package com.opsvision.ai.exception;

/**
 * Raised when an AI provider call fails (HTTP errors, parse failures, timeouts).
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
