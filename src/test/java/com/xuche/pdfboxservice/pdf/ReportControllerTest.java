package com.xuche.pdfboxservice.pdf;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        when(pdfReportService.fill(eq("report"), anyMap(), anyMap())).thenReturn(pdf);

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
                .andExpect(content().bytes(pdf));
    }

    @Test
    void acceptsSignatures() throws Exception {
        byte[] pdf = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);
        when(pdfReportService.fill(eq("report"), anyMap(), anyMap())).thenReturn(pdf);

        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fields\":{\"title\":\"Q3\"},\"signatures\":{\"signature\":\"data:image/png;base64,abc\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownTemplateReturns404() throws Exception {
        when(pdfReportService.fill(eq("nope"), anyMap(), anyMap()))
                .thenThrow(new TemplateNotFoundException("nope"));

        mockMvc.perform(
                        post("/api/reports/nope")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownFieldReturns400() throws Exception {
        when(pdfReportService.fill(eq("report"), anyMap(), anyMap()))
                .thenThrow(
                        new UnknownTemplateFieldException(
                                "report", Set.of("bogus"), Set.of("title")));

        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{\"bogus\":\"x\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFieldsReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/reports/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
