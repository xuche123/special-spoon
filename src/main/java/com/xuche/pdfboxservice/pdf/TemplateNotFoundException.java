package com.xuche.pdfboxservice.pdf;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when no PDF template exists for the requested template name. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(String templateName) {
        super("No PDF template found for name: " + templateName);
    }
}
