package com.opsvision.evidence.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.dto.NormalizedFindingInput;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrivyParserTest {

    private TrivyParser parser;

    @BeforeEach
    void setUp() {
        parser = new TrivyParser(new ObjectMapper());
    }

    @Test
    void parsesContainerImageVulnerabilities() throws Exception {
        NormalizedEvidenceInput evidence = parser.parse(load("fixtures/scanners/trivy-container.json"));

        assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.CONTAINER_SCAN);
        assertThat(evidence.source()).isEqualTo("trivy");
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(evidence.findings()).hasSize(2);

        NormalizedFindingInput openssl = evidence.findings().stream()
                .filter(f -> "CVE-2024-9999".equals(f.externalId()))
                .findFirst()
                .orElseThrow();
        assertThat(openssl.findingType()).isEqualTo(FindingType.CONTAINER);
        assertThat(openssl.severity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(openssl.packageName()).isEqualTo("openssl");
        assertThat(openssl.installedVersion()).isEqualTo("3.1.4-r5");
        assertThat(openssl.fixedVersion()).isEqualTo("3.1.4-r6");
        assertThat(openssl.title()).contains("openssl");
    }

    @Test
    void parsesFilesystemDependencyVulnerabilities() throws Exception {
        NormalizedEvidenceInput evidence = parser.parse(load("fixtures/scanners/trivy-fs-deps.json"));

        assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.DEPENDENCY_SCAN);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(evidence.findings()).hasSize(1);

        NormalizedFindingInput finding = evidence.findings().getFirst();
        assertThat(finding.findingType()).isEqualTo(FindingType.DEPENDENCY);
        assertThat(finding.severity()).isEqualTo(FindingSeverity.CRITICAL);
        assertThat(finding.externalId()).isEqualTo("CVE-2022-45047");
        assertThat(finding.packageName()).contains("sshd-common");
        assertThat(finding.fixedVersion()).isEqualTo("2.9.2");
    }

    @Test
    void cleanReportIsPassed() throws Exception {
        NormalizedEvidenceInput evidence = parser.parse(load("fixtures/scanners/trivy-clean.json"));

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.PASSED);
        assertThat(evidence.findings()).isEmpty();
        assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.CONTAINER_SCAN);
    }

    @Test
    void forcedEvidenceTypeOverridesInference() throws Exception {
        NormalizedEvidenceInput evidence = parser.parse(
                load("fixtures/scanners/trivy-container.json"),
                EvidenceType.DEPENDENCY_SCAN
        );
        assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.DEPENDENCY_SCAN);
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("[]".getBytes()))
                .isInstanceOf(ScannerParseException.class)
                .hasMessageContaining("root must be an object");
    }

    private static byte[] load(String classpath) throws IOException {
        try (InputStream in = TrivyParserTest.class.getClassLoader().getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            return in.readAllBytes();
        }
    }
}
