package com.opsvision.github.exception;

import java.time.Instant;
import java.util.Optional;

/**
 * Raised when GitHub API rate limits are exceeded (HTTP 403/429 with rate-limit headers).
 */
public class GitHubRateLimitException extends GitHubException {

    private final Instant resetAt;
    private final Integer remaining;
    private final Integer limit;

    public GitHubRateLimitException(
            String message,
            int statusCode,
            Instant resetAt,
            Integer remaining,
            Integer limit
    ) {
        super(message, statusCode);
        this.resetAt = resetAt;
        this.remaining = remaining;
        this.limit = limit;
    }

    public Optional<Instant> getResetAt() {
        return Optional.ofNullable(resetAt);
    }

    public Optional<Integer> getRemaining() {
        return Optional.ofNullable(remaining);
    }

    public Optional<Integer> getLimit() {
        return Optional.ofNullable(limit);
    }
}
