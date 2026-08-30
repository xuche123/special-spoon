package com.xuche.pdfboxservice.pdf.web;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xuche.pdfboxservice.pdf.rendering.PdfReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@TestPropertySource(
        properties = {"pdf.preview.enabled=true", "pdf.limits.max-request-body-bytes=16"})
class ReportRequestBodyLimitControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PdfReportService pdfReportService;

    @Test
    void rejectsDeclaredOversizedReportBeforeGeneration() throws Exception {
        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{\"title\":\"too large\"}}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(pdfReportService);
    }

    @Test
    void rejectsDeclaredOversizedPreviewBeforeRendering() throws Exception {
        mockMvc.perform(
                        post("/api/template-previews/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{\"title\":\"too large\"}}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(pdfReportService);
    }

    @Test
    void rejectsStreamedReportWhenReceivedBytesExceedLimit() throws Exception {
        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Content-Length", "-1")
                                .content("{\"fields\":{\"title\":\"too large\"}}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_LIMIT_EXCEEDED"));
        verifyNoInteractions(pdfReportService);
    }

    @Test
    void rejectsStreamedPreviewWhenReceivedBytesExceedLimit() throws Exception {
        mockMvc.perform(
                        post("/api/template-previews/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Content-Length", "-1")
                                .content("{\"fields\":{\"title\":\"too large\"}}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_LIMIT_EXCEEDED"));
        verifyNoInteractions(pdfReportService);
    }

    @Test
    void allowsRequestsWithinLimitOnBothIntakePaths() throws Exception {
        when(pdfReportService.generate(eq("report"), eq(null), anyMap()))
                .thenReturn(new PdfReportService.GeneratedReport(new byte[0], "v1"));
        when(pdfReportService.preview(eq("report"), eq(null), anyMap()))
                .thenReturn(
                        new PdfReportService.TemplatePreview(
                                new PdfReportService.GeneratedReport(new byte[0], "v1"),
                                java.util.List.of()));

        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post("/api/template-previews/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isOk());
    }
}
