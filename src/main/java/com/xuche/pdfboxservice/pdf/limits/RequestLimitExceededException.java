package com.xuche.pdfboxservice.pdf.limits;

import com.xuche.pdfboxservice.pdf.ReportException;

/** Thrown when a configured report safeguard is exceeded. */
public final class RequestLimitExceededException extends ReportException {
    public RequestLimitExceededException(String message, String templateName, String fieldName) {
        super(message, templateName, null, fieldName);
    }
}
