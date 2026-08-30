package com.xuche.pdfboxservice.pdf;

/** Internal exception carrying only safe, client-relevant report context. */
abstract class ReportException extends RuntimeException {
    private final String templateName;
    private final String version;
    private final String fieldName;

    ReportException(String message, String templateName, String version, String fieldName) {
        super(message);
        this.templateName = templateName;
        this.version = version;
        this.fieldName = fieldName;
    }

    String templateName() {
        return templateName;
    }

    String version() {
        return version;
    }

    String fieldName() {
        return fieldName;
    }
}
