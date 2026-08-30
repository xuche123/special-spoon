package com.xuche.pdfboxservice.pdf.rendering;

import com.xuche.pdfboxservice.pdf.ReportException;

/** Thrown when a multiline text field cannot contain its value within its configured bounds. */
public class TextOverflowException extends ReportException {

    public TextOverflowException(String templateName, String fieldName) {
        this(templateName, null, fieldName);
    }

    public TextOverflowException(String templateName, String version, String fieldName) {
        super(
                "Text for field '"
                        + fieldName
                        + "' in template '"
                        + templateName
                        + "' exceeds its configured width or height",
                templateName,
                version,
                fieldName);
    }
}
