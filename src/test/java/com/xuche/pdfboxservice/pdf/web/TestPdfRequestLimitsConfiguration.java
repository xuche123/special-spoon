package com.xuche.pdfboxservice.pdf.web;

import com.xuche.pdfboxservice.pdf.limits.PdfRequestLimits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class TestPdfRequestLimitsConfiguration {
    @Bean
    PdfRequestLimits pdfRequestLimits(
            @Value("${test.pdf.limits.max-request-body-bytes:1048576}") long maxRequestBodyBytes) {
        return new PdfRequestLimits(maxRequestBodyBytes, 100, 100_000, 25L * 1024L * 1024L);
    }
}
