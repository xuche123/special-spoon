package com.xuche.pdfboxservice.pdf;

/** Thrown when a configured report safeguard is exceeded. */
final class RequestLimitExceededException extends ReportException {
    RequestLimitExceededException(String message, String templateName, String fieldName) {
        super(message, templateName, null, fieldName);
    }
}
