package com.xuche.pdfboxservice.pdf.template;

import com.xuche.pdfboxservice.pdf.ReportException;

/** Thrown when a requested template version is not configured. */
public class TemplateVersionNotFoundException extends ReportException {
    public TemplateVersionNotFoundException(String templateName, String version) {
        super(
                "No version '" + version + "' found for PDF template: " + templateName,
                templateName,
                version,
                null);
    }
}
