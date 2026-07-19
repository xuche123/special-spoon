package com.xuche.pdfboxservice.pdf;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when the request contains field names that the template does not support. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UnknownTemplateFieldException extends RuntimeException {

    public UnknownTemplateFieldException(
            String templateName, Set<String> unknownFields, Set<String> supportedFields) {
        super(
                "Unknown field(s) "
                        + new TreeSet<>(unknownFields)
                        + " for template '"
                        + templateName
                        + "'. Supported fields: "
                        + new TreeSet<>(supportedFields));
    }
}
