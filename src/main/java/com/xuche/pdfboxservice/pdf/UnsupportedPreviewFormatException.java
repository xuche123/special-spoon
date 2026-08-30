package com.xuche.pdfboxservice.pdf;

/** Thrown when a preview format other than PDF or JSON is requested. */
class UnsupportedPreviewFormatException extends RuntimeException {
    UnsupportedPreviewFormatException(String format) {
        super("Unsupported preview format: " + format);
    }
}
