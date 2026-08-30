package com.xuche.pdfboxservice.pdf;

/** Thrown when a field value has the wrong JSON type for the field's type. */
public class InvalidFieldValueException extends ReportException {

    public InvalidFieldValueException(
            String templateName, String fieldName, String expected, Object actualValue) {
        this(templateName, null, fieldName, expected, actualValue);
    }

    public InvalidFieldValueException(
            String templateName,
            String version,
            String fieldName,
            String expected,
            Object actualValue) {
        super(
                "Invalid value for field '"
                        + fieldName
                        + "' in template '"
                        + templateName
                        + "': expected "
                        + expected
                        + " but got "
                        + describe(actualValue),
                templateName,
                version,
                fieldName);
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
