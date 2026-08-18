package com.opsvision.evidence.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.dto.NormalizedFindingInput;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import com.opsvision.evidence.model.FindingSeverity;
import com.opsvision.evidence.model.FindingType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses Semgrep JSON report output ({@code results[]} entries) into STATIC_ANALYSIS evidence.
 */
@Component
public class SemgrepParser implements EvidenceParser {

    public static final String SOURCE = "semgrep";

    private final ObjectMapper objectMapper;

    public SemgrepParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public NormalizedEvidenceInput parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ScannerParseException("Semgrep content must not be empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception ex) {
            throw new ScannerParseException("Invalid Semgrep JSON", ex);
        }

        if (root == null || !root.isObject()) {
            throw new ScannerParseException("Semgrep JSON root must be an object");
        }

        JsonNode results = root.path("results");
        List<NormalizedFindingInput> findings = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode result : results) {
                NormalizedFindingInput finding = mapResult(result);
                if (finding != null) {
                    findings.add(finding);
                }
            }
        }

        EvidenceStatus status = resolveStatus(findings);
        String summary = buildSummary(findings);

        return NormalizedEvidenceInput.builder(EvidenceType.STATIC_ANALYSIS, status, SOURCE)
                .summary(summary)
                .rawReference("semgrep.json")
                .collectedAt(Instant.now())
                .findings(findings)
                .build();
    }

    private NormalizedFindingInput mapResult(JsonNode result) {
        if (result == null || result.isNull() || !result.isObject()) {
            return null;
        }

        String ruleId = textOrNull(result, "check_id");
        String path = textOrNull(result, "path");
        Integer line = null;
        JsonNode start = result.path("start");
        if (start.isObject() && start.has("line") && start.get("line").canConvertToInt()) {
            line = start.get("line").asInt();
        }

        JsonNode extra = result.path("extra");
        String message = null;
        FindingSeverity severity = FindingSeverity.UNKNOWN;
        if (extra.isObject()) {
            message = textOrNull(extra, "message");
            severity = SeverityMapper.fromScanner(textOrNull(extra, "severity"));
            if (severity == FindingSeverity.UNKNOWN) {
                // Some reports nest severity under metadata
                severity = SeverityMapper.fromScanner(textOrNull(extra.path("metadata"), "severity"));
            }
        }

        String title = ruleId != null ? ruleId : (message != null ? abbreviate(message, 120) : "Semgrep finding");
        if (title.isBlank()) {
            title = "Semgrep finding";
        }

        return new NormalizedFindingInput(
                FindingType.STATIC_ANALYSIS,
                severity,
                ruleId,
                title,
                message,
                path,
                line,
                null,
                null,
                null,
                ruleId
        );
    }

    private static EvidenceStatus resolveStatus(List<NormalizedFindingInput> findings) {
        if (findings.isEmpty()) {
            return EvidenceStatus.PASSED;
        }
        boolean hasHighOrCritical = findings.stream().anyMatch(f ->
                f.severity() == FindingSeverity.CRITICAL || f.severity() == FindingSeverity.HIGH);
        if (hasHighOrCritical) {
            return EvidenceStatus.FAILED;
        }
        return EvidenceStatus.WARNING;
    }

    private static String buildSummary(List<NormalizedFindingInput> findings) {
        if (findings.isEmpty()) {
            return "Semgrep reported no findings";
        }
        long critical = count(findings, FindingSeverity.CRITICAL);
        long high = count(findings, FindingSeverity.HIGH);
        long medium = count(findings, FindingSeverity.MEDIUM);
        long low = count(findings, FindingSeverity.LOW);
        long other = findings.size() - critical - high - medium - low;
        return String.format(
                Locale.ROOT,
                "Semgrep reported %d finding(s) (critical=%d, high=%d, medium=%d, low=%d, other=%d)",
                findings.size(), critical, high, medium, low, other
        );
    }

    private static long count(List<NormalizedFindingInput> findings, FindingSeverity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String abbreviate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
