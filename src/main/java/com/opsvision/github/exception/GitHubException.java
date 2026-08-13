package com.opsvision.github.exception;

/**
 * Base exception for GitHub integration failures.
 */
public class GitHubException extends RuntimeException {

    private final int statusCode;

    public GitHubException(String message) {
        this(message, -1, null);
    }

    public GitHubException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public GitHubException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public GitHubException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
