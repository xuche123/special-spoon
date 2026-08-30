package com.xuche.pdfboxservice.pdf;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Resource limits applied to report requests and generated PDFs. */
@Validated
@ConfigurationProperties(prefix = "pdf.limits")
public record PdfRequestLimits(
        @Positive long maxRequestBodyBytes,
        @Positive int maxFields,
        @Positive int maxTextCodePoints,
        @Positive long maxGeneratedPdfBytes) {

    public PdfRequestLimits() {
        this(1024L * 1024L, 100, 100_000, 25L * 1024L * 1024L);
    }
}
