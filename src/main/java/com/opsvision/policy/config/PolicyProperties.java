package com.opsvision.policy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable thresholds and override switches for deployment policy.
 * <p>
 * Default score bands:
 * <ul>
 *   <li>deployMinScore–100 → DEPLOY</li>
 *   <li>reviewMinScore–(deployMinScore-1) → REVIEW</li>
 *   <li>0–(reviewMinScore-1) → BLOCK</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "opsvision.policy")
public class PolicyProperties {

    /** Minimum confidence score (inclusive) required for DEPLOY. */
    private int deployMinScore = 80;

    /** Minimum confidence score (inclusive) required for REVIEW (below → BLOCK). */
    private int reviewMinScore = 60;

    /** When true, any CRITICAL finding forces BLOCK. */
    private boolean blockOnCriticalFinding = true;

    /** When true, failed BUILD evidence forces BLOCK. */
    private boolean blockOnFailedBuild = true;

    /** When true, failed TEST evidence forces BLOCK. */
    private boolean blockOnFailedTests = true;

    /** When true, failed WORKFLOW evidence forces BLOCK. */
    private boolean blockOnFailedWorkflow = true;

    /**
     * When true, HIGH findings (without CRITICAL) force at least REVIEW
     * even if the score would allow DEPLOY.
     */
    private boolean reviewOnHighFinding = true;

    public int getDeployMinScore() {
        return deployMinScore;
    }

    public void setDeployMinScore(int deployMinScore) {
        this.deployMinScore = deployMinScore;
    }

    public int getReviewMinScore() {
        return reviewMinScore;
    }

    public void setReviewMinScore(int reviewMinScore) {
        this.reviewMinScore = reviewMinScore;
    }

    public boolean isBlockOnCriticalFinding() {
        return blockOnCriticalFinding;
    }

    public void setBlockOnCriticalFinding(boolean blockOnCriticalFinding) {
        this.blockOnCriticalFinding = blockOnCriticalFinding;
    }

    public boolean isBlockOnFailedBuild() {
        return blockOnFailedBuild;
    }

    public void setBlockOnFailedBuild(boolean blockOnFailedBuild) {
        this.blockOnFailedBuild = blockOnFailedBuild;
    }

    public boolean isBlockOnFailedTests() {
        return blockOnFailedTests;
    }

    public void setBlockOnFailedTests(boolean blockOnFailedTests) {
        this.blockOnFailedTests = blockOnFailedTests;
    }

    public boolean isBlockOnFailedWorkflow() {
        return blockOnFailedWorkflow;
    }

    public void setBlockOnFailedWorkflow(boolean blockOnFailedWorkflow) {
        this.blockOnFailedWorkflow = blockOnFailedWorkflow;
    }

    public boolean isReviewOnHighFinding() {
        return reviewOnHighFinding;
    }

    public void setReviewOnHighFinding(boolean reviewOnHighFinding) {
        this.reviewOnHighFinding = reviewOnHighFinding;
    }
}
