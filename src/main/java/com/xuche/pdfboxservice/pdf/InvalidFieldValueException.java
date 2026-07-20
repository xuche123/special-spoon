package com.xuche.pdfboxservice.pdf;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a field value cannot be interpreted for the field's type (e.g. a checkbox). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFieldValueException extends RuntimeException {

    public InvalidFieldValueException(String templateName, String fieldName, String value) {
        super(
                "Invalid value '"
                        + value
                        + "' for checkbox field '"
                        + fieldName
                        + "' in template '"
                        + templateName
                        + "'. Use true/false (also accepted: yes/no, on/off, 1/0).");
    }
}
