package com.opsvision.evidence.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.model.EvidenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScannerResultParseServiceTest {

    private ScannerResultParseService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        service = new ScannerResultParseService(
                new SemgrepParser(mapper),
                new TrivyParser(mapper),
                new JacocoParser()
        );
    }

    @Test
    void dispatchesByScannerName() throws Exception {
        NormalizedEvidenceInput semgrep = service.parse("semgrep", load("fixtures/scanners/semgrep-clean.json"));
        NormalizedEvidenceInput trivy = service.parse("trivy", load("fixtures/scanners/trivy-clean.json"));
        NormalizedEvidenceInput jacoco = service.parse("jacoco", load("fixtures/scanners/jacoco-sample.csv"));

        assertThat(semgrep.evidenceType()).isEqualTo(EvidenceType.STATIC_ANALYSIS);
        assertThat(trivy.evidenceType()).isEqualTo(EvidenceType.CONTAINER_SCAN);
        assertThat(jacoco.evidenceType()).isEqualTo(EvidenceType.CODE_COVERAGE);
    }

    @Test
    void rejectsUnknownScanner() {
        assertThatThrownBy(() -> service.parse("sonar", new byte[]{1}))
                .isInstanceOf(ScannerParseException.class)
                .hasMessageContaining("Unsupported scanner");
    }

    private static byte[] load(String classpath) throws IOException {
        try (InputStream in = ScannerResultParseServiceTest.class.getClassLoader().getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            return in.readAllBytes();
        }
    }
}
