package com.opsvision.evidence.parser;

import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.model.EvidenceType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Objects;

/**
 * Facade for converting Semgrep / Trivy / JaCoCo scanner artifacts into normalized evidence.
 * Does not persist results; pair with {@link com.opsvision.evidence.service.EvidenceIngestionService}.
 */
@Service
public class ScannerResultParseService {

    private final SemgrepParser semgrepParser;
    private final TrivyParser trivyParser;
    private final JacocoParser jacocoParser;

    public ScannerResultParseService(
            SemgrepParser semgrepParser,
            TrivyParser trivyParser,
            JacocoParser jacocoParser
    ) {
        this.semgrepParser = semgrepParser;
        this.trivyParser = trivyParser;
        this.jacocoParser = jacocoParser;
    }

    public NormalizedEvidenceInput parseSemgrep(byte[] content) {
        return semgrepParser.parse(content);
    }

    public NormalizedEvidenceInput parseSemgrep(InputStream content) {
        return semgrepParser.parse(content);
    }

    public NormalizedEvidenceInput parseTrivy(byte[] content) {
        return trivyParser.parse(content);
    }

    public NormalizedEvidenceInput parseTrivy(byte[] content, EvidenceType forcedType) {
        return trivyParser.parse(content, forcedType);
    }

    public NormalizedEvidenceInput parseTrivy(InputStream content) {
        return trivyParser.parse(content);
    }

    public NormalizedEvidenceInput parseJacoco(byte[] content) {
        return jacocoParser.parse(content);
    }

    public NormalizedEvidenceInput parseJacoco(InputStream content) {
        return jacocoParser.parse(content);
    }

    /**
     * Dispatches by a simple scanner name ({@code semgrep}, {@code trivy}, {@code jacoco}).
     */
    public NormalizedEvidenceInput parse(String scanner, byte[] content) {
        Objects.requireNonNull(scanner, "scanner");
        return switch (scanner.trim().toLowerCase()) {
            case "semgrep" -> parseSemgrep(content);
            case "trivy" -> parseTrivy(content);
            case "jacoco", "ja-coco", "code-coverage" -> parseJacoco(content);
            default -> throw new ScannerParseException("Unsupported scanner: " + scanner);
        };
    }
}
