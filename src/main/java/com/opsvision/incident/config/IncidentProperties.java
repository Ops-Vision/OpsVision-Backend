package com.opsvision.incident.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thresholds for deterministic incident detection from telemetry.
 */
@ConfigurationProperties(prefix = "opsvision.incident")
public class IncidentProperties {

    /**
     * Error ratio (0–1) at or above which an incident is opened.
     */
    private double errorRatioThreshold = 0.05;

    /**
     * Absolute error rate (req/s) threshold when ratio is unavailable.
     */
    private double errorRateThreshold = 1.0;

    /**
     * Availability ratio below which an incident is opened.
     */
    private double availabilityMinRatio = 0.95;

    /**
     * Minimum total pod restarts that contribute to detection.
     */
    private int podRestartThreshold = 3;

    /**
     * Minimum warning Kubernetes events that contribute to detection.
     */
    private int warningEventThreshold = 1;

    /**
     * When true, unhealthy Deployment rollout (unavailable replicas) is a signal.
     */
    private boolean detectUnhealthyWorkload = true;

    /**
     * When true, missing both K8s and Prometheus does not create incidents.
     */
    private boolean requireTelemetry = true;

    public double getErrorRatioThreshold() {
        return errorRatioThreshold;
    }

    public void setErrorRatioThreshold(double errorRatioThreshold) {
        this.errorRatioThreshold = errorRatioThreshold;
    }

    public double getErrorRateThreshold() {
        return errorRateThreshold;
    }

    public void setErrorRateThreshold(double errorRateThreshold) {
        this.errorRateThreshold = errorRateThreshold;
    }

    public double getAvailabilityMinRatio() {
        return availabilityMinRatio;
    }

    public void setAvailabilityMinRatio(double availabilityMinRatio) {
        this.availabilityMinRatio = availabilityMinRatio;
    }

    public int getPodRestartThreshold() {
        return podRestartThreshold;
    }

    public void setPodRestartThreshold(int podRestartThreshold) {
        this.podRestartThreshold = podRestartThreshold;
    }

    public int getWarningEventThreshold() {
        return warningEventThreshold;
    }

    public void setWarningEventThreshold(int warningEventThreshold) {
        this.warningEventThreshold = warningEventThreshold;
    }

    public boolean isDetectUnhealthyWorkload() {
        return detectUnhealthyWorkload;
    }

    public void setDetectUnhealthyWorkload(boolean detectUnhealthyWorkload) {
        this.detectUnhealthyWorkload = detectUnhealthyWorkload;
    }

    public boolean isRequireTelemetry() {
        return requireTelemetry;
    }

    public void setRequireTelemetry(boolean requireTelemetry) {
        this.requireTelemetry = requireTelemetry;
    }
}
