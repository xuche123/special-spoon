package com.xuche.pdfboxservice.pdf.web;

import com.xuche.pdfboxservice.pdf.PdfReportService;
import java.util.List;

/** Machine-readable description of a rendered template preview. */
record TemplatePreviewResponse(
        String templateVersion, List<PdfReportService.FieldPreview> fields) {}
