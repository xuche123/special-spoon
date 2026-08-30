package com.xuche.pdfboxservice.pdf;

import org.springframework.http.HttpStatus;

/** Maps internal report failures to the stable public HTTP error contract. */
final class ReportFailureMapper {

    MappedFailure map(Exception exception) {
        if (isCause(exception, RequestBodyLimitIOException.class)) {
            return failure(HttpStatus.PAYLOAD_TOO_LARGE, ReportError.requestLimitExceeded());
        }
        if (exception instanceof TemplateNotFoundException error) {
            return failure(
                    HttpStatus.NOT_FOUND,
                    ReportError.of("TEMPLATE_NOT_FOUND", error.getMessage(), error));
        }
        if (exception instanceof TemplateVersionNotFoundException error) {
            return failure(
                    HttpStatus.NOT_FOUND,
                    ReportError.of("TEMPLATE_VERSION_NOT_FOUND", error.getMessage(), error));
        }
        if (exception instanceof TemplatePreviewDisabledException) {
            return failure(
                    HttpStatus.NOT_FOUND,
                    ReportError.simple(
                            "TEMPLATE_PREVIEW_DISABLED", "Template previews are disabled."));
        }
        if (exception instanceof UnsupportedPreviewFormatException
                || exception instanceof org.springframework.web.HttpMediaTypeNotSupportedException
                || exception instanceof org.springframework.web.bind.MethodArgumentNotValidException
                || exception
                        instanceof
                        org.springframework.http.converter.HttpMessageNotReadableException) {
            return failure(
                    HttpStatus.BAD_REQUEST,
                    ReportError.simple("MALFORMED_REQUEST", "The report request is malformed."));
        }
        if (exception instanceof UnknownTemplateFieldException error) {
            return failure(
                    HttpStatus.BAD_REQUEST,
                    ReportError.of("UNKNOWN_FIELD", error.getMessage(), error));
        }
        if (exception instanceof InvalidFieldValueException error) {
            return failure(
                    HttpStatus.BAD_REQUEST,
                    ReportError.of("FIELD_VALUE_TYPE_INVALID", error.getMessage(), error));
        }
        if (exception instanceof TextOverflowException error) {
            return failure(
                    HttpStatus.BAD_REQUEST,
                    ReportError.of("TEXT_OVERFLOW", error.getMessage(), error));
        }
        if (exception instanceof UnsupportedGlyphException error) {
            return failure(
                    HttpStatus.BAD_REQUEST,
                    ReportError.of("UNSUPPORTED_GLYPH", error.getMessage(), error));
        }
        if (exception instanceof RequestLimitExceededException error) {
            return failure(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    ReportError.of("REQUEST_LIMIT_EXCEEDED", error.getMessage(), error));
        }
        if (exception instanceof IllegalStateException) {
            return failure(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ReportError.simple(
                            "TEMPLATE_CONFIGURATION_INVALID",
                            "Template configuration is invalid."));
        }
        return renderingFailure(exception);
    }

    private MappedFailure renderingFailure(Exception exception) {
        if (exception instanceof PdfRenderingFailedException error) {
            return failure(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ReportError.of("PDF_RENDERING_FAILED", "PDF rendering failed.", error));
        }
        return failure(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReportError.simple("PDF_RENDERING_FAILED", "PDF rendering failed."));
    }

    private MappedFailure failure(HttpStatus status, ReportError error) {
        return new MappedFailure(status, error);
    }

    private boolean isCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    record MappedFailure(HttpStatus status, ReportError error) {}
}
