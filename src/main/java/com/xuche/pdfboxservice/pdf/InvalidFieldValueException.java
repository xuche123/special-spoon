package com.xuche.pdfboxservice.pdf;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a field value has the wrong JSON type for the field's type. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFieldValueException extends RuntimeException {

    public InvalidFieldValueException(
            String templateName, String fieldName, String expected, Object actualValue) {
        super(
                "Invalid value for field '"
                        + fieldName
                        + "' in template '"
                        + templateName
                        + "': expected "
                        + expected
                        + " but got "
                        + describe(actualValue));
    }

    private static String describe(Object value) {
        return switch (value) {
            case null -> "null";
            case String s -> "string \"" + s + "\"";
            case Boolean b -> "boolean " + b;
            case Number n -> "number " + n;
            default -> value.getClass().getSimpleName();
        };
    }
}
