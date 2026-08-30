package com.xuche.pdfboxservice.pdf;

/** Thrown when administrative template previews have not been explicitly enabled. */
class TemplatePreviewDisabledException extends ReportException {
    TemplatePreviewDisabledException() {
        super("Template previews are disabled.", null, null, null);
    }
}
