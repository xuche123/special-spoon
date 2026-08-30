package com.xuche.pdfboxservice.pdf.template;

import com.xuche.pdfboxservice.pdf.ReportException;

/** Thrown when no PDF template exists for the requested template name. */
public class TemplateNotFoundException extends ReportException {

    public TemplateNotFoundException(String templateName) {
        super("No PDF template found for name: " + templateName, templateName, null, null);
    }
}
