package com.xuche.pdfboxservice.pdf;

/** Internal rendering failure whose response deliberately omits the cause. */
final class PdfRenderingFailedException extends ReportException {
    PdfRenderingFailedException(String templateName, String version, Throwable cause) {
        super("PDF rendering failed", templateName, version, null);
        initCause(cause);
    }
}
