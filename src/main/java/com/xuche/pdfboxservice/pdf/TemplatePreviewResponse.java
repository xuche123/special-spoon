package com.xuche.pdfboxservice.pdf;

import java.util.List;

/** Machine-readable description of a rendered template preview. */
record TemplatePreviewResponse(
        String templateVersion, List<PdfReportService.FieldPreview> fields) {}
