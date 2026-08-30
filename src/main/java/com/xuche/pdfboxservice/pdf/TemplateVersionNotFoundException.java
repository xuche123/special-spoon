package com.xuche.pdfboxservice.pdf;

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
