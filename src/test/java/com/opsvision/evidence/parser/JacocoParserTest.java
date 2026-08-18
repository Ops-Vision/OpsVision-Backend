package com.opsvision.evidence.parser;

import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JacocoParserTest {

    private JacocoParser parser;

    @BeforeEach
    void setUp() {
        parser = new JacocoParser();
    }

    @Test
    void parsesCsvAndComputesLineCoverage() throws Exception {
        // lines: missed 1+5+3=9, covered 4+45+22=71 => 71/80 = 88.8%
        NormalizedEvidenceInput evidence = parser.parse(load("fixtures/scanners/jacoco-sample.csv"));

        assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.CODE_COVERAGE);
        assertThat(evidence.source()).isEqualTo("jacoco");
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.PASSED);
        assertThat(evidence.metricUnit()).isEqualTo("percent");
        assertThat(evidence.metricValue()).isCloseTo(new BigDecimal("88.8"), within(new BigDecimal("0.05")));
        assertThat(evidence.summary()).containsIgnoringCase("line");
        assertThat(evidence.rawReference()).isEqualTo("jacoco.csv");
        assertThat(evidence.findings()).isEmpty();
    }

    @Test
    void lowCoverageYieldsWarning() throws Exception {
        // lines: 10 covered / 50 total = 20%
        NormalizedEvidenceInput evidence = parser.parse(load("fixtures/scanners/jacoco-low-coverage.csv"));

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.WARNING);
        assertThat(evidence.metricValue()).isCloseTo(new BigDecimal("20.0"), within(new BigDecimal("0.05")));
    }

    @Test
    void parsesXmlReportLevelCounters() throws Exception {
        // report counters: line missed=9 covered=71 => 88.8%
        NormalizedEvidenceInput evidence = parser.parse(load("fixtures/scanners/jacoco-sample.xml"));

        assertThat(evidence.evidenceType()).isEqualTo(EvidenceType.CODE_COVERAGE);
        assertThat(evidence.rawReference()).isEqualTo("jacoco.xml");
        assertThat(evidence.metricValue()).isCloseTo(new BigDecimal("88.8"), within(new BigDecimal("0.05")));
        assertThat(evidence.summary()).contains("instruction").contains("branch");
    }

    @Test
    void rejectsEmptyCsv() {
        assertThatThrownBy(() -> parser.parse("GROUP,PACKAGE\n".getBytes()))
                .isInstanceOf(ScannerParseException.class);
    }

    @Test
    void rejectsMissingColumns() {
        assertThatThrownBy(() -> parser.parse("A,B\n1,2\n".getBytes()))
                .isInstanceOf(ScannerParseException.class)
                .hasMessageContaining("missing coverage columns");
    }

    private static byte[] load(String classpath) throws IOException {
        try (InputStream in = JacocoParserTest.class.getClassLoader().getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            return in.readAllBytes();
        }
    }
}
