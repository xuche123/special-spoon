package com.xuche.pdfboxservice.pdf;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReportController {

    private final PdfReportService pdfReportService;

    ReportController(PdfReportService pdfReportService) {
        this.pdfReportService = pdfReportService;
    }

    @PostMapping("/api/reports/{templateName}")
    ResponseEntity<byte[]> fillReport(
            @PathVariable String templateName, @Valid @RequestBody FillReportRequest request) {
        byte[] pdf = pdfReportService.fill(templateName, request.fields(), request.signatures());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + templateName + "-filled.pdf\"")
                .body(pdf);
    }
}
