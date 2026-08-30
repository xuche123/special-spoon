package com.xuche.pdfboxservice.pdf;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@TestPropertySource(properties = "pdf.preview.enabled=true")
class ReportControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PdfReportService pdfReportService;

    @Test
    void fillsTemplateAndReturnsPdf() throws Exception {
        byte[] pdf = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);
        when(pdfReportService.generate(eq("report"), eq(null), anyMap()))
                .thenReturn(new PdfReportService.GeneratedReport(pdf, "v1"));

        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{\"title\":\"Q3 Sales Report\"}}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_DISPOSITION,
                                        containsString("report-filled.pdf")))
                .andExpect(header().string("X-Template-Version", "v1"))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void unknownTemplateReturns404() throws Exception {
        when(pdfReportService.generate(eq("nope"), eq(null), anyMap()))
                .thenThrow(new TemplateNotFoundException("nope"));

        mockMvc.perform(
                        post("/api/reports/nope")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unavailableTemplateVersionReturnsStructured404() throws Exception {
        when(pdfReportService.generate(eq("report"), eq("v3"), anyMap()))
                .thenThrow(new TemplateVersionNotFoundException("report", "v3"));

        mockMvc.perform(
                        post("/api/reports/report?version=v3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_VERSION_NOT_FOUND"))
                .andExpect(jsonPath("$.templateName").value("report"))
                .andExpect(jsonPath("$.version").value("v3"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unknownFieldReturns400() throws Exception {
        when(pdfReportService.generate(eq("report"), eq(null), anyMap()))
                .thenThrow(
                        new UnknownTemplateFieldException(
                                "report", Set.of("bogus"), Set.of("title")));

        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{\"bogus\":\"x\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.templateName").value("report"));
    }

    @Test
    void unsupportedGlyphReturnsStructured400() throws Exception {
        when(pdfReportService.generate(eq("report"), eq(null), anyMap()))
                .thenThrow(new UnsupportedGlyphException("report", "v1", "title", 0x1F600));

        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{\"title\":\"😀\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_GLYPH"))
                .andExpect(jsonPath("$.templateName").value("report"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.fieldName").value("title"));
    }

    @Test
    void invalidFieldValueReturnsStructured400() throws Exception {
        when(pdfReportService.generate(eq("report"), eq(null), anyMap()))
                .thenThrow(
                        new InvalidFieldValueException(
                                "report", "v1", "title", "a JSON string", true));

        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{\"title\":true}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FIELD_VALUE_TYPE_INVALID"))
                .andExpect(jsonPath("$.templateName").value("report"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.fieldName").value("title"));
    }

    @Test
    void textOverflowReturnsStructured400() throws Exception {
        when(pdfReportService.generate(eq("report"), eq(null), anyMap()))
                .thenThrow(new TextOverflowException("report", "v1", "summary"));

        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{\"summary\":\"long text\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEXT_OVERFLOW"))
                .andExpect(jsonPath("$.templateName").value("report"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.fieldName").value("summary"));
    }

    @Test
    void invalidConfigurationReturnsSafeStructured500() throws Exception {
        when(pdfReportService.generate(eq("report"), eq(null), anyMap()))
                .thenThrow(new IllegalStateException("internal configuration details"));

        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("TEMPLATE_CONFIGURATION_INVALID"))
                .andExpect(jsonPath("$.message").value("Template configuration is invalid."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void renderingFailureRetainsSafeContextAndHidesCause() throws Exception {
        when(pdfReportService.generate(eq("report"), eq("v1"), anyMap()))
                .thenThrow(
                        new PdfRenderingFailedException(
                                "report", "v1", new RuntimeException("secret")));

        mockMvc.perform(
                        post("/api/reports/report?version=v1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PDF_RENDERING_FAILED"))
                .andExpect(jsonPath("$.message").value("PDF rendering failed."))
                .andExpect(jsonPath("$.templateName").value("report"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void missingFieldsReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void malformedJsonReturnsSafeStructuredError() throws Exception {
        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void returnsInlinePdfPreviewWithSelectedVersion() throws Exception {
        byte[] pdf = "%PDF-1.7 preview".getBytes(StandardCharsets.UTF_8);
        when(pdfReportService.preview(eq("report"), eq("v2"), anyMap()))
                .thenReturn(
                        new PdfReportService.TemplatePreview(
                                new PdfReportService.GeneratedReport(pdf, "v2"),
                                java.util.List.of()));

        mockMvc.perform(
                        post("/api/template-previews/report?version=v2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Template-Version", "v2"))
                .andExpect(
                        header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void returnsJsonPreviewMetadata() throws Exception {
        when(pdfReportService.preview(eq("report"), eq(null), anyMap()))
                .thenReturn(
                        new PdfReportService.TemplatePreview(
                                new PdfReportService.GeneratedReport(new byte[0], "v1"),
                                java.util.List.of(
                                        new PdfReportService.FieldPreview(
                                                "title",
                                                1,
                                                150f,
                                                665f,
                                                14f,
                                                400f,
                                                null,
                                                null,
                                                PdfTemplateProperties.TextAlignment.LEFT,
                                                PdfTemplateProperties.TextOverflow.REJECT,
                                                PdfTemplateProperties.FieldType.TEXT,
                                                "Helvetica",
                                                "Hello"))));

        mockMvc.perform(
                        post("/api/template-previews/report?format=json")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateVersion").value("v1"))
                .andExpect(jsonPath("$.fields[0].name").value("title"))
                .andExpect(jsonPath("$.fields[0].page").value(1))
                .andExpect(jsonPath("$.fields[0].x").value(150.0))
                .andExpect(jsonPath("$.fields[0].y").value(665.0))
                .andExpect(jsonPath("$.fields[0].type").value("TEXT"))
                .andExpect(jsonPath("$.fields[0].font").value("Helvetica"))
                .andExpect(jsonPath("$.fields[0].value").value("Hello"));
    }

    @Test
    void missingPreviewFieldsReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/template-previews/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unsupportedPreviewFormatReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/template-previews/report?format=xml")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
