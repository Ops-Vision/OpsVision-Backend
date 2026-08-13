package com.opsvision.evidence.dto;

import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Normalized CI/CD evidence item independent of GitHub or scanner-specific schemas.
 */
public record NormalizedEvidenceInput(
        @NotNull EvidenceType evidenceType,
        @NotNull EvidenceStatus status,
        @NotBlank String source,
        String summary,
        BigDecimal metricValue,
        String metricUnit,
        String rawReference,
        Instant collectedAt,
        @Valid List<NormalizedFindingInput> findings
) {
    public NormalizedEvidenceInput {
        if (findings == null) {
            findings = List.of();
        } else {
            findings = List.copyOf(findings);
        }
    }

    public static Builder builder(EvidenceType evidenceType, EvidenceStatus status, String source) {
        return new Builder(evidenceType, status, source);
    }

    public static final class Builder {
        private final EvidenceType evidenceType;
        private final EvidenceStatus status;
        private final String source;
        private String summary;
        private BigDecimal metricValue;
        private String metricUnit;
        private String rawReference;
        private Instant collectedAt;
        private List<NormalizedFindingInput> findings = List.of();

        private Builder(EvidenceType evidenceType, EvidenceStatus status, String source) {
            this.evidenceType = evidenceType;
            this.status = status;
            this.source = source;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder metricValue(BigDecimal metricValue) {
            this.metricValue = metricValue;
            return this;
        }

        public Builder metricUnit(String metricUnit) {
            this.metricUnit = metricUnit;
            return this;
        }

        public Builder rawReference(String rawReference) {
            this.rawReference = rawReference;
            return this;
        }

        public Builder collectedAt(Instant collectedAt) {
            this.collectedAt = collectedAt;
            return this;
        }

        public Builder findings(List<NormalizedFindingInput> findings) {
            this.findings = findings;
            return this;
        }

        public NormalizedEvidenceInput build() {
            return new NormalizedEvidenceInput(
                    evidenceType,
                    status,
                    source,
                    summary,
                    metricValue,
                    metricUnit,
                    rawReference,
                    collectedAt,
                    findings
            );
        }
    }
}
