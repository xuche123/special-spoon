package com.xuche.pdfboxservice.pdf;

/** Raised when a configured template font cannot represent input text. */
final class UnsupportedGlyphException extends ReportException {
    UnsupportedGlyphException(
            String templateName, String version, String fieldName, int codePoint) {
        super(
                "The configured template font does not support Unicode code point U+%04X"
                        .formatted(codePoint),
                templateName,
                version,
                fieldName);
    }
}
