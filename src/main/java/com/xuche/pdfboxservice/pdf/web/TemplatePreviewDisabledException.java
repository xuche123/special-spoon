package com.xuche.pdfboxservice.pdf.web;

import com.xuche.pdfboxservice.pdf.ReportException;

/** Thrown when administrative template previews have not been explicitly enabled. */
public class TemplatePreviewDisabledException extends ReportException {
    public TemplatePreviewDisabledException() {
        super("Template previews are disabled.", null, null, null);
    }
}
