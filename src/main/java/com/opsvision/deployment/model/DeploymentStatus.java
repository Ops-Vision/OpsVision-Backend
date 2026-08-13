package com.opsvision.deployment.model;

/**
 * Lifecycle status of a deployment analysis record.
 */
public enum DeploymentStatus {
    PENDING,
    ANALYZING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
