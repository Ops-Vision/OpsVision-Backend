package com.opsvision.scoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralized, configurable weights and thresholds for deployment confidence scoring.
 * Defaults sum to 100 points across factors.
 */
@ConfigurationProperties(prefix = "opsvision.scoring")
public class ScoringProperties {

    /** Max points for CI build evidence. */
    private int buildMax = 20;

    /** Max points for automated tests. */
    private int testsMax = 20;

    /** Max points for code coverage. */
    private int coverageMax = 15;

    /** Max points for static analysis (e.g. Semgrep). */
    private int staticAnalysisMax = 15;

    /**
     * Max points for security scans (dependency + container combined).
     */
    private int securityMax = 25;

    /** Max points for overall workflow / pipeline status. */
    private int workflowMax = 5;

    /** Coverage percent at or above which full coverage points are awarded. */
    private double coverageFullThreshold = 80.0;

    /** Coverage percent at or above which partial credit starts (below = 0). */
    private double coverageZeroThreshold = 40.0;

    /** Points deducted from static-analysis budget per CRITICAL finding. */
    private int staticCriticalPenalty = 15;

    /** Points deducted from static-analysis budget per HIGH finding. */
    private int staticHighPenalty = 8;

    /** Points deducted from static-analysis budget per MEDIUM finding. */
    private int staticMediumPenalty = 3;

    /** Points deducted from static-analysis budget per LOW finding. */
    private int staticLowPenalty = 1;

    /** Points deducted from security budget per CRITICAL finding. */
    private int securityCriticalPenalty = 25;

    /** Points deducted from security budget per HIGH finding. */
    private int securityHighPenalty = 10;

    /** Points deducted from security budget per MEDIUM finding. */
    private int securityMediumPenalty = 4;

    /** Points deducted from security budget per LOW finding. */
    private int securityLowPenalty = 1;

    /**
     * When a factor's evidence is missing, award this fraction of max (0.0–1.0).
     * Default 0 keeps missing evidence from inflating confidence.
     */
    private double missingEvidenceCredit = 0.0;

    public int getBuildMax() {
        return buildMax;
    }

    public void setBuildMax(int buildMax) {
        this.buildMax = buildMax;
    }

    public int getTestsMax() {
        return testsMax;
    }

    public void setTestsMax(int testsMax) {
        this.testsMax = testsMax;
    }

    public int getCoverageMax() {
        return coverageMax;
    }

    public void setCoverageMax(int coverageMax) {
        this.coverageMax = coverageMax;
    }

    public int getStaticAnalysisMax() {
        return staticAnalysisMax;
    }

    public void setStaticAnalysisMax(int staticAnalysisMax) {
        this.staticAnalysisMax = staticAnalysisMax;
    }

    public int getSecurityMax() {
        return securityMax;
    }

    public void setSecurityMax(int securityMax) {
        this.securityMax = securityMax;
    }

    public int getWorkflowMax() {
        return workflowMax;
    }

    public void setWorkflowMax(int workflowMax) {
        this.workflowMax = workflowMax;
    }

    public double getCoverageFullThreshold() {
        return coverageFullThreshold;
    }

    public void setCoverageFullThreshold(double coverageFullThreshold) {
        this.coverageFullThreshold = coverageFullThreshold;
    }

    public double getCoverageZeroThreshold() {
        return coverageZeroThreshold;
    }

    public void setCoverageZeroThreshold(double coverageZeroThreshold) {
        this.coverageZeroThreshold = coverageZeroThreshold;
    }

    public int getStaticCriticalPenalty() {
        return staticCriticalPenalty;
    }

    public void setStaticCriticalPenalty(int staticCriticalPenalty) {
        this.staticCriticalPenalty = staticCriticalPenalty;
    }

    public int getStaticHighPenalty() {
        return staticHighPenalty;
    }

    public void setStaticHighPenalty(int staticHighPenalty) {
        this.staticHighPenalty = staticHighPenalty;
    }

    public int getStaticMediumPenalty() {
        return staticMediumPenalty;
    }

    public void setStaticMediumPenalty(int staticMediumPenalty) {
        this.staticMediumPenalty = staticMediumPenalty;
    }

    public int getStaticLowPenalty() {
        return staticLowPenalty;
    }

    public void setStaticLowPenalty(int staticLowPenalty) {
        this.staticLowPenalty = staticLowPenalty;
    }

    public int getSecurityCriticalPenalty() {
        return securityCriticalPenalty;
    }

    public void setSecurityCriticalPenalty(int securityCriticalPenalty) {
        this.securityCriticalPenalty = securityCriticalPenalty;
    }

    public int getSecurityHighPenalty() {
        return securityHighPenalty;
    }

    public void setSecurityHighPenalty(int securityHighPenalty) {
        this.securityHighPenalty = securityHighPenalty;
    }

    public int getSecurityMediumPenalty() {
        return securityMediumPenalty;
    }

    public void setSecurityMediumPenalty(int securityMediumPenalty) {
        this.securityMediumPenalty = securityMediumPenalty;
    }

    public int getSecurityLowPenalty() {
        return securityLowPenalty;
    }

    public void setSecurityLowPenalty(int securityLowPenalty) {
        this.securityLowPenalty = securityLowPenalty;
    }

    public double getMissingEvidenceCredit() {
        return missingEvidenceCredit;
    }

    public void setMissingEvidenceCredit(double missingEvidenceCredit) {
        this.missingEvidenceCredit = missingEvidenceCredit;
    }

    /** Sum of configured factor maxima (expected 100 for a 0–100 score). */
    public int totalMaxPoints() {
        return buildMax + testsMax + coverageMax + staticAnalysisMax + securityMax + workflowMax;
    }
}
