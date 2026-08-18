package com.opsvision.common.exception;

import com.opsvision.ai.exception.AiProviderException;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.observability.exception.ObservabilityException;
import com.opsvision.evidence.exception.EvidenceIngestionException;
import com.opsvision.github.exception.GitHubAuthenticationException;
import com.opsvision.github.exception.GitHubException;
import com.opsvision.github.exception.GitHubNotFoundException;
import com.opsvision.github.exception.GitHubRateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized RFC 9457 Problem Detail responses for REST APIs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DeploymentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleDeploymentNotFound(
            DeploymentNotFoundException ex,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.NOT_FOUND, "Deployment Not Found", ex.getMessage(), request, Map.of(
                "deploymentId", ex.getDeploymentId()
        ));
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleIncidentNotFound(
            IncidentNotFoundException ex,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.NOT_FOUND, "Incident Not Found", ex.getMessage(), request, Map.of(
                "incidentId", ex.getIncidentId()
        ));
    }

    @ExceptionHandler(EvidenceIngestionException.class)
    public ResponseEntity<ProblemDetail> handleEvidenceIngestion(
            EvidenceIngestionException ex,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.BAD_REQUEST, "Evidence Ingestion Error", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage() != null
                    ? error.getDefaultMessage()
                    : "invalid");
        }
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        detail.setTitle("Validation Failed");
        detail.setType(URI.create("about:blank"));
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("path", request.getRequestURI());
        detail.setProperty("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed Request Body",
                "Request body could not be parsed",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        this::violationPath,
                        v -> v.getMessage() != null ? v.getMessage() : "invalid",
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        detail.setTitle("Validation Failed");
        detail.setType(URI.create("about:blank"));
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("path", request.getRequestURI());
        detail.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String name = ex.getName() != null ? ex.getName() : "parameter";
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid Parameter",
                "Parameter '" + name + "' has an invalid value",
                request,
                Map.of("parameter", name)
        );
    }

    @ExceptionHandler(GitHubAuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleGitHubAuth(
            GitHubAuthenticationException ex,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.BAD_GATEWAY, "GitHub Authentication Failed", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(GitHubNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleGitHubNotFound(
            GitHubNotFoundException ex,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.BAD_GATEWAY, "GitHub Resource Not Found", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(GitHubRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleGitHubRateLimit(
            GitHubRateLimitException ex,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "GitHub Rate Limit", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(GitHubException.class)
    public ResponseEntity<ProblemDetail> handleGitHub(
            GitHubException ex,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.BAD_GATEWAY, "GitHub API Error", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ProblemDetail> handleAiProvider(
            AiProviderException ex,
            HttpServletRequest request
    ) {
        log.warn("AI provider error on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "AI Provider Error", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ObservabilityException.class)
    public ResponseEntity<ProblemDetail> handleObservability(
            ObservabilityException ex,
            HttpServletRequest request
    ) {
        log.warn("Observability error on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Observability Error", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                request,
                Map.of()
        );
    }

    private String violationPath(ConstraintViolation<?> violation) {
        if (violation.getPropertyPath() == null) {
            return "request";
        }
        String path = violation.getPropertyPath().toString();
        return path.isBlank() ? "request" : path;
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request,
            Map<String, Object> properties
    ) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail != null ? detail : title);
        body.setTitle(title);
        body.setType(URI.create("about:blank"));
        body.setProperty("timestamp", Instant.now().toString());
        body.setProperty("path", request.getRequestURI());
        properties.forEach(body::setProperty);
        return ResponseEntity.status(status).body(body);
    }
}
