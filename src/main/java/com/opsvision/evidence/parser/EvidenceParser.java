package com.opsvision.evidence.parser;

import com.opsvision.evidence.dto.NormalizedEvidenceInput;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Converts vendor-specific scanner output into normalized evidence (+ findings).
 */
public interface EvidenceParser {

    /**
     * Human-readable source identifier stored on evidence (e.g. {@code semgrep}).
     */
    String source();

    /**
     * Parse raw scanner bytes into a single normalized evidence item.
     */
    NormalizedEvidenceInput parse(byte[] content);

    default NormalizedEvidenceInput parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new ScannerParseException(source() + " content must not be null");
        }
        try {
            return parse(inputStream.readAllBytes());
        } catch (ScannerParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ScannerParseException("Failed to read " + source() + " content", ex);
        }
    }

    default NormalizedEvidenceInput parse(String content) {
        return parse(content, StandardCharsets.UTF_8);
    }

    default NormalizedEvidenceInput parse(String content, Charset charset) {
        if (content == null) {
            throw new ScannerParseException(source() + " content must not be null");
        }
        Charset cs = charset != null ? charset : StandardCharsets.UTF_8;
        return parse(content.getBytes(cs));
    }
}
