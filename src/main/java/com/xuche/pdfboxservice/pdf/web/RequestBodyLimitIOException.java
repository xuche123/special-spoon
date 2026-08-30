package com.xuche.pdfboxservice.pdf.web;

import java.io.IOException;

/** IOException raised when a request body exceeds the configured limit while being read. */
public final class RequestBodyLimitIOException extends IOException {
    public RequestBodyLimitIOException() {
        super("request body limit exceeded");
    }
}
