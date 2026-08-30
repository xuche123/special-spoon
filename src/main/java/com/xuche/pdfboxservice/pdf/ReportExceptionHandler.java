package com.xuche.pdfboxservice.pdf;

import java.io.UncheckedIOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts report failures into the public, diagnostic-free error contract. */
@RestControllerAdvice
class ReportExceptionHandler {

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        HttpMediaTypeNotSupportedException.class
    })
    ResponseEntity<ReportError> malformed(Exception exception) {
        if (hasCause(exception, RequestBodyLimitIOException.class)) {
            return response(HttpStatus.PAYLOAD_TOO_LARGE, ReportError.requestLimitExceeded());
        }
        return response(
                HttpStatus.BAD_REQUEST,
                ReportError.simple("MALFORMED_REQUEST", "The report request is malformed."));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    ResponseEntity<ReportError> templateNotFound(TemplateNotFoundException exception) {
        return response(
                HttpStatus.NOT_FOUND,
                ReportError.of("TEMPLATE_NOT_FOUND", exception.getMessage(), exception));
    }

    @ExceptionHandler(TemplateVersionNotFoundException.class)
    ResponseEntity<ReportError> versionNotFound(TemplateVersionNotFoundException exception) {
        return response(
                HttpStatus.NOT_FOUND,
                ReportError.of("TEMPLATE_VERSION_NOT_FOUND", exception.getMessage(), exception));
    }

    @ExceptionHandler(TemplatePreviewDisabledException.class)
    ResponseEntity<ReportError> previewDisabled(TemplatePreviewDisabledException exception) {
        return response(
                HttpStatus.NOT_FOUND,
                ReportError.simple("TEMPLATE_PREVIEW_DISABLED", "Template previews are disabled."));
    }

    @ExceptionHandler(UnsupportedPreviewFormatException.class)
    ResponseEntity<ReportError> invalidPreviewFormat(UnsupportedPreviewFormatException exception) {
        return response(
                HttpStatus.BAD_REQUEST,
                ReportError.simple("MALFORMED_REQUEST", "The report request is malformed."));
    }

    @ExceptionHandler(UnknownTemplateFieldException.class)
    ResponseEntity<ReportError> unknownField(UnknownTemplateFieldException exception) {
        return response(
                HttpStatus.BAD_REQUEST,
                ReportError.of("UNKNOWN_FIELD", exception.getMessage(), exception));
    }

    @ExceptionHandler(InvalidFieldValueException.class)
    ResponseEntity<ReportError> invalidValue(InvalidFieldValueException exception) {
        return response(
                HttpStatus.BAD_REQUEST,
                ReportError.of("FIELD_VALUE_TYPE_INVALID", exception.getMessage(), exception));
    }

    @ExceptionHandler(TextOverflowException.class)
    ResponseEntity<ReportError> overflow(TextOverflowException exception) {
        return response(
                HttpStatus.BAD_REQUEST,
                ReportError.of("TEXT_OVERFLOW", exception.getMessage(), exception));
    }

    @ExceptionHandler(UnsupportedGlyphException.class)
    ResponseEntity<ReportError> unsupportedGlyph(UnsupportedGlyphException exception) {
        return response(
                HttpStatus.BAD_REQUEST,
                ReportError.of("UNSUPPORTED_GLYPH", exception.getMessage(), exception));
    }

    @ExceptionHandler(RequestLimitExceededException.class)
    ResponseEntity<ReportError> limit(RequestLimitExceededException exception) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ReportError.of("REQUEST_LIMIT_EXCEEDED", exception.getMessage(), exception));
    }

    @ExceptionHandler(UncheckedIOException.class)
    ResponseEntity<ReportError> rendering(UncheckedIOException exception) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReportError.simple("PDF_RENDERING_FAILED", "PDF rendering failed."));
    }

    @ExceptionHandler(PdfRenderingFailedException.class)
    ResponseEntity<ReportError> rendering(PdfRenderingFailedException exception) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReportError.of("PDF_RENDERING_FAILED", "PDF rendering failed.", exception));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ReportError> invalidConfiguration(IllegalStateException exception) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReportError.simple(
                        "TEMPLATE_CONFIGURATION_INVALID", "Template configuration is invalid."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ReportError> unexpected(Exception exception) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReportError.simple("PDF_RENDERING_FAILED", "PDF rendering failed."));
    }

    private ResponseEntity<ReportError> response(HttpStatus status, ReportError error) {
        return ResponseEntity.status(status).body(error);
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }
}
