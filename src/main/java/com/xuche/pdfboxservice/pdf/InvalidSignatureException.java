package com.xuche.pdfboxservice.pdf;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a signature image cannot be decoded or violates size/type limits. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException(String templateName, String fieldName, String reason) {
        super(
                "Invalid signature for field '"
                        + fieldName
                        + "' in template '"
                        + templateName
                        + "': "
                        + reason);
    }
}
