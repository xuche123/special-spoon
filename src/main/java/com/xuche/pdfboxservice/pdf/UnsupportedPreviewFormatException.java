package com.xuche.pdfboxservice.pdf;

/** Thrown when a preview format other than PDF or JSON is requested. */
public class UnsupportedPreviewFormatException extends RuntimeException {
    public UnsupportedPreviewFormatException(String format) {
        super("Unsupported preview format: " + format);
    }
}
