package com.opsvision.github.exception;

/**
 * Raised when a GitHub resource (repository, run, commit) cannot be found.
 */
public class GitHubNotFoundException extends GitHubException {

    public GitHubNotFoundException(String message, int statusCode) {
        super(message, statusCode);
    }
}
