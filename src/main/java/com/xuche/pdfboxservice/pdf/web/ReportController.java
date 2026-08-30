package com.xuche.pdfboxservice.pdf.web;

import com.xuche.pdfboxservice.pdf.rendering.PdfReportService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReportController {

    private final PdfReportService pdfReportService;
    private final boolean previewEnabled;

    ReportController(
            PdfReportService pdfReportService,
            @Value("${pdf.preview.enabled:false}") boolean previewEnabled) {
        this.pdfReportService = pdfReportService;
        this.previewEnabled = previewEnabled;
    }

    @PostMapping("/api/reports/{templateName}")
    ResponseEntity<byte[]> fillReport(
            @PathVariable String templateName,
            @RequestParam(required = false) String version,
            @Valid @RequestBody FillReportRequest request) {
        PdfReportService.GeneratedReport report =
                pdfReportService.generate(templateName, version, request.fields());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + templateName + "-filled.pdf\"")
                .header("X-Template-Version", report.templateVersion())
                .body(report.pdfBytes());
    }

    @PostMapping("/api/template-previews/{templateName}")
    ResponseEntity<?> previewTemplate(
            @PathVariable String templateName,
            @RequestParam(required = false) String version,
            @RequestParam(defaultValue = "pdf") String format,
            @Valid @RequestBody(required = false) FillReportRequest request) {
        if (!previewEnabled) {
            throw new TemplatePreviewDisabledException();
        }
        if (!format.equals("pdf") && !format.equals("json")) {
            throw new UnsupportedPreviewFormatException(format);
        }
        Map<String, Object> fields = request == null ? Map.of() : request.fields();
        PdfReportService.TemplatePreview preview =
                pdfReportService.preview(templateName, version, fields);
        if (format.equals("json")) {
            return ResponseEntity.ok()
                    .header("X-Template-Version", preview.report().templateVersion())
                    .body(
                            new TemplatePreviewResponse(
                                    preview.report().templateVersion(), preview.fields()));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + templateName + "-preview.pdf\"")
                .header("X-Template-Version", preview.report().templateVersion())
                .body(preview.report().pdfBytes());
    }
}
