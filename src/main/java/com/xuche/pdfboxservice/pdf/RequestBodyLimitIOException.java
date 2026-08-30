package com.xuche.pdfboxservice.pdf;

import java.io.IOException;

/** IOException raised when a request body exceeds the configured limit while being read. */
final class RequestBodyLimitIOException extends IOException {
    RequestBodyLimitIOException() {
        super("request body limit exceeded");
    }
}
