package com.opsvision.github.exception;

/**
 * Raised when GitHub rejects credentials (HTTP 401/403 authentication failures).
 */
public class GitHubAuthenticationException extends GitHubException {

    public GitHubAuthenticationException(String message, int statusCode) {
        super(message, statusCode);
    }
}
