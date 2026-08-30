package com.xuche.pdfboxservice.pdf.rendering;

import com.xuche.pdfboxservice.pdf.ReportException;

/** Internal rendering failure whose response deliberately omits the cause. */
public final class PdfRenderingFailedException extends ReportException {
    public PdfRenderingFailedException(String templateName, String version, Throwable cause) {
        super("PDF rendering failed", templateName, version, null);
        initCause(cause);
    }
}
