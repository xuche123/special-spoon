package com.xuche.pdfboxservice.pdf.web;

import com.xuche.pdfboxservice.pdf.ReportException;
import java.util.UUID;

/** Stable, safe error payload returned by report endpoints. */
public record ReportError(
        String code,
        String message,
        String requestId,
        String templateName,
        String version,
        String fieldName) {

    static ReportError of(String code, String message, ReportException error) {
        return new ReportError(
                code,
                message,
                UUID.randomUUID().toString(),
                error.templateName(),
                error.version(),
                error.fieldName());
    }

    static ReportError simple(String code, String message) {
        return new ReportError(code, message, UUID.randomUUID().toString(), null, null, null);
    }

    static ReportError requestLimitExceeded() {
        return simple(
                "REQUEST_LIMIT_EXCEEDED",
                "The report request body exceeds the configured size limit.");
    }
}
