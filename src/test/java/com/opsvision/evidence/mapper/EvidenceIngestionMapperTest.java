package com.opsvision.evidence.mapper;

import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.dto.NormalizedFindingInput;
import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceIngestionMapperTest {

    private EvidenceIngestionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EvidenceIngestionMapper();
    }

    @Test
    void mapsEvidenceAndFindingFields() {
        Instant collected = Instant.parse("2026-03-01T12:00:00Z");
        NormalizedEvidenceInput input = NormalizedEvidenceInput.builder(
                        EvidenceType.CODE_COVERAGE,
                        EvidenceStatus.PASSED,
                        "jacoco"
                )
                .summary("Line coverage 90%")
                .metricValue(new BigDecimal("90.0000"))
                .metricUnit("percent")
                .rawReference("artifacts/jacoco.csv")
                .collectedAt(collected)
                .build();

        DeploymentEvidence entity = mapper.toEvidenceEntity(input);

        assertThat(entity.getEvidenceType()).isEqualTo(EvidenceType.CODE_COVERAGE);
        assertThat(entity.getStatus()).isEqualTo(EvidenceStatus.PASSED);
        assertThat(entity.getSource()).isEqualTo("jacoco");
        assertThat(entity.getSummary()).isEqualTo("Line coverage 90%");
        assertThat(entity.getMetricValue()).isEqualByComparingTo("90.0000");
        assertThat(entity.getMetricUnit()).isEqualTo("percent");
        assertThat(entity.getRawReference()).isEqualTo("artifacts/jacoco.csv");
        assertThat(entity.getCollectedAt()).isEqualTo(collected);

        NormalizedFindingInput findingInput = new NormalizedFindingInput(
                FindingType.CONTAINER,
                FindingSeverity.HIGH,
                null,
                "CVE-1",
                "desc",
                null,
                null,
                "openssl",
                "1.0",
                "1.1",
                "CVE-1"
        );
        Finding finding = mapper.toFindingEntity(findingInput);
        assertThat(finding.getFindingType()).isEqualTo(FindingType.CONTAINER);
        assertThat(finding.getSeverity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(finding.getPackageName()).isEqualTo("openssl");
        assertThat(finding.getExternalId()).isEqualTo("CVE-1");
    }

    @ParameterizedTest
    @CsvSource({
            "success, PASSED",
            "FAILURE, FAILED",
            "skipped, SKIPPED",
            "neutral, WARNING",
            "in_progress, UNKNOWN",
            "'', UNKNOWN"
    })
    void mapsCiStatusStrings(String raw, EvidenceStatus expected) {
        assertThat(mapper.mapCiStatus(raw.isEmpty() ? "" : raw)).isEqualTo(expected);
    }

    @Test
    void rejectsMissingRequiredEvidenceFields() {
        assertThatThrownBy(() -> mapper.toEvidenceEntity(
                new NormalizedEvidenceInput(null, EvidenceStatus.PASSED, "src", null, null, null, null, null, null)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> mapper.toEvidenceEntity(
                new NormalizedEvidenceInput(EvidenceType.BUILD, EvidenceStatus.PASSED, "  ", null, null, null, null, null, null)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultFindingTypeMatchesEvidenceCategory() {
        assertThat(mapper.defaultFindingTypeFor(EvidenceType.STATIC_ANALYSIS))
                .isEqualTo(FindingType.STATIC_ANALYSIS);
        assertThat(mapper.defaultFindingTypeFor(EvidenceType.CONTAINER_SCAN))
                .isEqualTo(FindingType.CONTAINER);
        assertThat(mapper.defaultFindingTypeFor(EvidenceType.TEST))
                .isEqualTo(FindingType.TEST_FAILURE);
    }
}
