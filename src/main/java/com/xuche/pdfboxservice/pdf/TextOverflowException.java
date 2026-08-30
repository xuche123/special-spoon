package com.xuche.pdfboxservice.pdf;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a multiline text field cannot contain its value within its configured bounds. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TextOverflowException extends RuntimeException {

    public TextOverflowException(String templateName, String fieldName) {
        super(
                "Text for field '"
                        + fieldName
                        + "' in template '"
                        + templateName
                        + "' exceeds its configured width or height");
    }
}
