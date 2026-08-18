package com.opsvision.observability.exception;

/**
 * Raised when Kubernetes or Prometheus collection fails in a non-recoverable way.
 */
public class ObservabilityException extends RuntimeException {

    public ObservabilityException(String message) {
        super(message);
    }

    public ObservabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
