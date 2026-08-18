package com.opsvision.evidence.parser;

import com.opsvision.evidence.dto.NormalizedEvidenceInput;
import com.opsvision.evidence.model.EvidenceStatus;
import com.opsvision.evidence.model.EvidenceType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

/**
 * Parses JaCoCo coverage reports (CSV or XML) into CODE_COVERAGE evidence.
 * <p>
 * Metric value is overall line coverage percentage when line counters are present;
 * otherwise falls back to instruction coverage, then branch coverage.
 */
@Component
public class JacocoParser implements EvidenceParser {

    public static final String SOURCE = "jacoco";

    /** Default threshold below which status becomes WARNING (not a hard fail). */
    public static final BigDecimal DEFAULT_WARNING_THRESHOLD = new BigDecimal("70.0");

    private final BigDecimal warningThreshold;

    public JacocoParser() {
        this(DEFAULT_WARNING_THRESHOLD);
    }

    public JacocoParser(BigDecimal warningThreshold) {
        this.warningThreshold = warningThreshold != null ? warningThreshold : DEFAULT_WARNING_THRESHOLD;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public NormalizedEvidenceInput parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ScannerParseException("JaCoCo content must not be empty");
        }

        String preview = new String(content, 0, Math.min(content.length, 200), StandardCharsets.UTF_8)
                .stripLeading();
        CoverageTotals totals;
        String rawRef;
        if (preview.startsWith("<") || preview.startsWith("<?xml")) {
            totals = parseXml(content);
            rawRef = "jacoco.xml";
        } else {
            totals = parseCsv(content);
            rawRef = "jacoco.csv";
        }

        CoverageMetric metric = totals.primaryMetric();
        BigDecimal percent = metric.percent();
        EvidenceStatus status = resolveStatus(percent);
        String summary = buildSummary(totals, metric);

        return NormalizedEvidenceInput.builder(EvidenceType.CODE_COVERAGE, status, SOURCE)
                .summary(summary)
                .metricValue(percent)
                .metricUnit("percent")
                .rawReference(rawRef)
                .collectedAt(Instant.now())
                .build();
    }

    private CoverageTotals parseCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        String[] lines = text.split("\\R");
        if (lines.length == 0) {
            throw new ScannerParseException("JaCoCo CSV is empty");
        }

        String headerLine = lines[0].replace("\uFEFF", "");
        String[] headers = splitCsv(headerLine);
        int idxInstructionMissed = indexOf(headers, "INSTRUCTION_MISSED");
        int idxInstructionCovered = indexOf(headers, "INSTRUCTION_COVERED");
        int idxBranchMissed = indexOf(headers, "BRANCH_MISSED");
        int idxBranchCovered = indexOf(headers, "BRANCH_COVERED");
        int idxLineMissed = indexOf(headers, "LINE_MISSED");
        int idxLineCovered = indexOf(headers, "LINE_COVERED");

        if (idxInstructionMissed < 0 && idxLineMissed < 0 && idxBranchMissed < 0) {
            throw new ScannerParseException("JaCoCo CSV missing coverage columns");
        }

        long instructionMissed = 0;
        long instructionCovered = 0;
        long branchMissed = 0;
        long branchCovered = 0;
        long lineMissed = 0;
        long lineCovered = 0;
        int dataRows = 0;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = splitCsv(line);
            dataRows++;
            instructionMissed += readLong(cols, idxInstructionMissed);
            instructionCovered += readLong(cols, idxInstructionCovered);
            branchMissed += readLong(cols, idxBranchMissed);
            branchCovered += readLong(cols, idxBranchCovered);
            lineMissed += readLong(cols, idxLineMissed);
            lineCovered += readLong(cols, idxLineCovered);
        }

        if (dataRows == 0) {
            throw new ScannerParseException("JaCoCo CSV contains no data rows");
        }

        return new CoverageTotals(
                instructionMissed, instructionCovered,
                branchMissed, branchCovered,
                lineMissed, lineCovered
        );
    }

    private CoverageTotals parseXml(byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
            Element root = doc.getDocumentElement();
            if (root == null || !"report".equalsIgnoreCase(root.getNodeName())) {
                throw new ScannerParseException("JaCoCo XML root must be <report>");
            }

            // Prefer report-level counters; aggregate package/class only if absent.
            CounterSet counters = readCounters(root);
            if (counters.isEmpty()) {
                counters = aggregateCounters(root.getElementsByTagName("counter"));
            }

            if (counters.isEmpty()) {
                throw new ScannerParseException("JaCoCo XML contains no coverage counters");
            }

            return new CoverageTotals(
                    counters.instructionMissed, counters.instructionCovered,
                    counters.branchMissed, counters.branchCovered,
                    counters.lineMissed, counters.lineCovered
            );
        } catch (ScannerParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ScannerParseException("Invalid JaCoCo XML", ex);
        }
    }

    private static CounterSet readCounters(Element parent) {
        CounterSet set = new CounterSet();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element el)) {
                continue;
            }
            if (!"counter".equalsIgnoreCase(el.getNodeName())) {
                continue;
            }
            applyCounter(set, el);
        }
        return set;
    }

    private static CounterSet aggregateCounters(NodeList counters) {
        CounterSet set = new CounterSet();
        for (int i = 0; i < counters.getLength(); i++) {
            if (counters.item(i) instanceof Element el) {
                // Only sum CLASS-level counters to avoid double-counting hierarchy
                // Prefer TYPE attribute on counter; skip if parent is not class when mixed.
                // Simpler approach: if we got here, report-level was empty — sum all LINE/INSTRUCTION/BRANCH
                // at the first hierarchical level that has them. Using package-level is safer.
            }
        }
        // Aggregate only counters whose parent element is "package" or "class" — prefer package.
        boolean anyPackage = false;
        for (int i = 0; i < counters.getLength(); i++) {
            if (!(counters.item(i) instanceof Element el)) {
                continue;
            }
            if (el.getParentNode() instanceof Element parent
                    && "package".equalsIgnoreCase(parent.getNodeName())) {
                anyPackage = true;
                break;
            }
        }
        for (int i = 0; i < counters.getLength(); i++) {
            if (!(counters.item(i) instanceof Element el)) {
                continue;
            }
            if (!(el.getParentNode() instanceof Element parent)) {
                continue;
            }
            String parentName = parent.getNodeName();
            if (anyPackage) {
                if (!"package".equalsIgnoreCase(parentName)) {
                    continue;
                }
            } else if (!"class".equalsIgnoreCase(parentName) && !"report".equalsIgnoreCase(parentName)) {
                continue;
            }
            applyCounter(set, el);
        }
        return set;
    }

    private static void applyCounter(CounterSet set, Element el) {
        String type = el.getAttribute("type");
        long missed = parseLongAttr(el, "missed");
        long covered = parseLongAttr(el, "covered");
        if (type == null) {
            return;
        }
        switch (type.toUpperCase(Locale.ROOT)) {
            case "INSTRUCTION" -> {
                set.instructionMissed += missed;
                set.instructionCovered += covered;
            }
            case "BRANCH" -> {
                set.branchMissed += missed;
                set.branchCovered += covered;
            }
            case "LINE" -> {
                set.lineMissed += missed;
                set.lineCovered += covered;
            }
            default -> {
                // ignore COMPLEXITY/METHOD/CLASS
            }
        }
    }

    private EvidenceStatus resolveStatus(BigDecimal percent) {
        if (percent.compareTo(warningThreshold) < 0) {
            return EvidenceStatus.WARNING;
        }
        return EvidenceStatus.PASSED;
    }

    private static String buildSummary(CoverageTotals totals, CoverageMetric primary) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "JaCoCo %s coverage %.1f%%",
                primary.name().toLowerCase(Locale.ROOT), primary.percent()));
        if (totals.hasLine() && primary.kind() != MetricKind.LINE) {
            sb.append(String.format(Locale.ROOT, "; line %.1f%%", totals.linePercent()));
        }
        if (totals.hasInstruction() && primary.kind() != MetricKind.INSTRUCTION) {
            sb.append(String.format(Locale.ROOT, "; instruction %.1f%%", totals.instructionPercent()));
        }
        if (totals.hasBranch() && primary.kind() != MetricKind.BRANCH) {
            sb.append(String.format(Locale.ROOT, "; branch %.1f%%", totals.branchPercent()));
        }
        return sb.toString();
    }

    private static String[] splitCsv(String line) {
        // JaCoCo CSV is simple (no escaped commas in standard reports)
        return line.split(",", -1);
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (name.equalsIgnoreCase(headers[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    private static long readLong(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) {
            return 0L;
        }
        String raw = cols[idx].trim();
        if (raw.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new ScannerParseException("Invalid numeric value in JaCoCo CSV: " + raw, ex);
        }
    }

    private static long parseLongAttr(Element el, String attr) {
        String raw = el.getAttribute(attr);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ScannerParseException("Invalid counter attribute '" + attr + "': " + raw, ex);
        }
    }

    private enum MetricKind {
        LINE, INSTRUCTION, BRANCH
    }

    private record CoverageMetric(MetricKind kind, BigDecimal percent) {
        String name() {
            return kind.name();
        }
    }

    private static final class CounterSet {
        long instructionMissed;
        long instructionCovered;
        long branchMissed;
        long branchCovered;
        long lineMissed;
        long lineCovered;

        boolean isEmpty() {
            return instructionMissed == 0 && instructionCovered == 0
                    && branchMissed == 0 && branchCovered == 0
                    && lineMissed == 0 && lineCovered == 0;
        }
    }

    private record CoverageTotals(
            long instructionMissed,
            long instructionCovered,
            long branchMissed,
            long branchCovered,
            long lineMissed,
            long lineCovered
    ) {
        boolean hasLine() {
            return lineMissed + lineCovered > 0;
        }

        boolean hasInstruction() {
            return instructionMissed + instructionCovered > 0;
        }

        boolean hasBranch() {
            return branchMissed + branchCovered > 0;
        }

        BigDecimal linePercent() {
            return percent(lineMissed, lineCovered);
        }

        BigDecimal instructionPercent() {
            return percent(instructionMissed, instructionCovered);
        }

        BigDecimal branchPercent() {
            return percent(branchMissed, branchCovered);
        }

        CoverageMetric primaryMetric() {
            if (hasLine()) {
                return new CoverageMetric(MetricKind.LINE, linePercent());
            }
            if (hasInstruction()) {
                return new CoverageMetric(MetricKind.INSTRUCTION, instructionPercent());
            }
            if (hasBranch()) {
                return new CoverageMetric(MetricKind.BRANCH, branchPercent());
            }
            // No counters with totals — treat as 0% line coverage
            return new CoverageMetric(MetricKind.LINE, BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP));
        }

        private static BigDecimal percent(long missed, long covered) {
            long total = missed + covered;
            if (total <= 0) {
                return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(covered)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        }
    }
}
