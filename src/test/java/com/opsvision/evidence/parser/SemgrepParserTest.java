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

class SemgrepParserTest {

    private SemgrepParser parser;

    @BeforeEach
    void setUp() {
        parser = new SemgrepParser(new ObjectMapper());
    }

    @Test
    void parsesFindingsWithRuleSeverityFileAndLine() throws Exception {
        NormalizedEvidenceInput evidence = parser.parse(load("fixtures/scanners/semgrep-sample.json"));

        assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.STATIC_ANALYSIS);
        assertThat(evidence.source()).isEqualTo("semgrep");
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(evidence.findings()).hasSize(2);

        NormalizedFindingInput sqli = evidence.findings().stream()
                .filter(f -> "java.lang.security.audit.sqli.jdbc-sqli".equals(f.ruleId()))
                .findFirst()
                .orElseThrow();
        assertThat(sqli.findingType()).isEqualTo(FindingType.STATIC_ANALYSIS);
        assertThat(sqli.severity()).isEqualTo(FindingSeverity.MEDIUM);
        assertThat(sqli.filePath()).isEqualTo("src/main/java/com/acme/Dao.java");
        assertThat(sqli.lineNumber()).isEqualTo(42);
        assertThat(sqli.description()).contains("SQL");

        NormalizedFindingInput xss = evidence.findings().stream()
                .filter(f -> f.ruleId() != null && f.ruleId().contains("xss"))
                .findFirst()
                .orElseThrow();
        assertThat(xss.severity()).isEqualTo(FindingSeverity.HIGH);
    }

    @Test
    void cleanReportIsPassedWithNoFindings() throws Exception {
        NormalizedEvidenceInput evidence = parser.parse(load("fixtures/scanners/semgrep-clean.json"));

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.PASSED);
        assertThat(evidence.findings()).isEmpty();
        assertThat(evidence.summary()).containsIgnoringCase("no findings");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("{not-json".getBytes()))
                .isInstanceOf(ScannerParseException.class)
                .hasMessageContaining("Invalid Semgrep JSON");
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOf(ScannerParseException.class);
    }

    private static byte[] load(String classpath) throws IOException {
        try (InputStream in = SemgrepParserTest.class.getClassLoader().getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            return in.readAllBytes();
        }
    }
}
