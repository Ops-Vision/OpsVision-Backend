package com.opsvision.evidence.exception;

/**
 * Raised when evidence ingestion targets a deployment that does not exist.
 */
public class DeploymentNotFoundException extends RuntimeException {

    private final Long deploymentId;

    public DeploymentNotFoundException(Long deploymentId) {
        super("Deployment not found: " + deploymentId);
        this.deploymentId = deploymentId;
    }

    public Long getDeploymentId() {
        return deploymentId;
    }
}
