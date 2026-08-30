package com.xuche.pdfboxservice.pdf;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a requested template version is not configured. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TemplateVersionNotFoundException extends RuntimeException {
    public TemplateVersionNotFoundException(String templateName, String version) {
        super("No version '" + version + "' found for PDF template: " + templateName);
    }
}
