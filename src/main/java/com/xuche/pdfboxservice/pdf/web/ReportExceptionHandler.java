package com.xuche.pdfboxservice.pdf.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts report failures into the public, diagnostic-free error contract. */
@RestControllerAdvice
class ReportExceptionHandler {
    private final ReportFailureMapper failureMapper = new ReportFailureMapper();

    @ExceptionHandler(Exception.class)
    ResponseEntity<ReportError> handle(Exception exception) {
        ReportFailureMapper.MappedFailure failure = failureMapper.map(exception);
        return ResponseEntity.status(failure.status()).body(failure.error());
    }
}
