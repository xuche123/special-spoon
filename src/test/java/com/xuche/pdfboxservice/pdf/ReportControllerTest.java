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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
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
}
