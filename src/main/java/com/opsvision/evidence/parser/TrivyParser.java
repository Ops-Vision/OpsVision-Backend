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
 * Parses Trivy JSON report output into DEPENDENCY_SCAN or CONTAINER_SCAN evidence.
 * <p>
 * Supports the common {@code Results[]} schema with {@code Vulnerabilities[]} entries.
 * Scan class is inferred from result {@code Class}/{@code Type} when present; callers may
 * force the evidence type via {@link #parse(byte[], EvidenceType)}.
 */
@Component
public class TrivyParser implements EvidenceParser {

    public static final String SOURCE = "trivy";

    private final ObjectMapper objectMapper;

    public TrivyParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public NormalizedEvidenceInput parse(byte[] content) {
        return parse(content, null);
    }

    /**
     * @param forcedType optional override; when null, type is inferred (default CONTAINER_SCAN)
     */
    public NormalizedEvidenceInput parse(byte[] content, EvidenceType forcedType) {
        if (content == null || content.length == 0) {
            throw new ScannerParseException("Trivy content must not be empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception ex) {
            throw new ScannerParseException("Invalid Trivy JSON", ex);
        }

        if (root == null || !root.isObject()) {
            throw new ScannerParseException("Trivy JSON root must be an object");
        }

        JsonNode results = root.path("Results");
        if (!results.isArray()) {
            // Some exporters lowercase the key
            results = root.path("results");
        }

        List<NormalizedFindingInput> findings = new ArrayList<>();
        boolean sawOsPkgs = false;
        boolean sawLangPkgs = false;

        if (results.isArray()) {
            for (JsonNode result : results) {
                if (result == null || !result.isObject()) {
                    continue;
                }
                String resultClass = textOrNull(result, "Class");
                if (resultClass == null) {
                    resultClass = textOrNull(result, "class");
                }
                String resultType = textOrNull(result, "Type");
                if (resultType == null) {
                    resultType = textOrNull(result, "type");
                }
                if (resultClass != null && resultClass.toLowerCase(Locale.ROOT).contains("os-pkgs")) {
                    sawOsPkgs = true;
                }
                if (resultClass != null && resultClass.toLowerCase(Locale.ROOT).contains("lang-pkgs")) {
                    sawLangPkgs = true;
                }
                if (resultType != null && isLanguageEcosystem(resultType)) {
                    sawLangPkgs = true;
                }

                JsonNode vulns = result.path("Vulnerabilities");
                if (!vulns.isArray()) {
                    vulns = result.path("vulnerabilities");
                }
                if (!vulns.isArray()) {
                    continue;
                }
                FindingType findingType = resolveFindingType(resultClass, resultType);
                for (JsonNode vuln : vulns) {
                    NormalizedFindingInput finding = mapVulnerability(vuln, findingType);
                    if (finding != null) {
                        findings.add(finding);
                    }
                }
            }
        }

        EvidenceType evidenceType = forcedType != null
                ? forcedType
                : inferEvidenceType(sawOsPkgs, sawLangPkgs);
        EvidenceStatus status = resolveStatus(findings);
        String summary = buildSummary(findings, evidenceType);

        return NormalizedEvidenceInput.builder(evidenceType, status, SOURCE)
                .summary(summary)
                .rawReference("trivy.json")
                .collectedAt(Instant.now())
                .findings(findings)
                .build();
    }

    private static FindingType resolveFindingType(String resultClass, String resultType) {
        if (resultClass != null) {
            String c = resultClass.toLowerCase(Locale.ROOT);
            if (c.contains("lang-pkgs") || c.contains("lang")) {
                return FindingType.DEPENDENCY;
            }
            if (c.contains("os-pkgs") || c.contains("container")) {
                return FindingType.CONTAINER;
            }
        }
        if (resultType != null && isLanguageEcosystem(resultType)) {
            return FindingType.DEPENDENCY;
        }
        return FindingType.SECURITY_VULNERABILITY;
    }

    private static boolean isLanguageEcosystem(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        return t.contains("pom") || t.contains("maven") || t.contains("gradle")
                || t.contains("npm") || t.contains("yarn") || t.contains("pip")
                || t.contains("go") || t.contains("cargo") || t.contains("gem")
                || t.contains("composer") || t.contains("nuget");
    }

    private static EvidenceType inferEvidenceType(boolean sawOsPkgs, boolean sawLangPkgs) {
        if (sawLangPkgs && !sawOsPkgs) {
            return EvidenceType.DEPENDENCY_SCAN;
        }
        // Default to container scan (image / OS packages) when mixed or unknown
        return EvidenceType.CONTAINER_SCAN;
    }

    private NormalizedFindingInput mapVulnerability(JsonNode vuln, FindingType findingType) {
        if (vuln == null || vuln.isNull() || !vuln.isObject()) {
            return null;
        }

        String vulnId = firstText(vuln, "VulnerabilityID", "vulnerabilityID", "vulnerability_id", "id");
        String pkg = firstText(vuln, "PkgName", "pkgName", "package_name", "PkgID");
        String installed = firstText(vuln, "InstalledVersion", "installedVersion", "installed_version");
        String fixed = firstText(vuln, "FixedVersion", "fixedVersion", "fixed_version");
        String severityRaw = firstText(vuln, "Severity", "severity");
        String title = firstText(vuln, "Title", "title");
        String description = firstText(vuln, "Description", "description");

        FindingSeverity severity = SeverityMapper.fromScanner(severityRaw);

        if (title == null || title.isBlank()) {
            if (vulnId != null && pkg != null) {
                title = vulnId + " in " + pkg;
            } else if (vulnId != null) {
                title = vulnId;
            } else if (pkg != null) {
                title = "Vulnerability in " + pkg;
            } else {
                title = "Trivy vulnerability";
            }
        }

        return new NormalizedFindingInput(
                findingType != null ? findingType : FindingType.SECURITY_VULNERABILITY,
                severity,
                vulnId,
                title,
                description,
                null,
                null,
                pkg,
                installed,
                fixed,
                vulnId
        );
    }

    private static EvidenceStatus resolveStatus(List<NormalizedFindingInput> findings) {
        if (findings.isEmpty()) {
            return EvidenceStatus.PASSED;
        }
        boolean blocking = findings.stream().anyMatch(f ->
                f.severity() == FindingSeverity.CRITICAL || f.severity() == FindingSeverity.HIGH);
        if (blocking) {
            return EvidenceStatus.FAILED;
        }
        return EvidenceStatus.WARNING;
    }

    private static String buildSummary(List<NormalizedFindingInput> findings, EvidenceType type) {
        String label = type == EvidenceType.DEPENDENCY_SCAN ? "Trivy dependency scan" : "Trivy container scan";
        if (findings.isEmpty()) {
            return label + " reported no vulnerabilities";
        }
        long critical = count(findings, FindingSeverity.CRITICAL);
        long high = count(findings, FindingSeverity.HIGH);
        long medium = count(findings, FindingSeverity.MEDIUM);
        long low = count(findings, FindingSeverity.LOW);
        return String.format(
                Locale.ROOT,
                "%s reported %d vulnerability(ies) (critical=%d, high=%d, medium=%d, low=%d)",
                label, findings.size(), critical, high, medium, low
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

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = textOrNull(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
