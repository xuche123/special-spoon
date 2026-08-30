package com.xuche.pdfboxservice.pdf.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xuche.pdfboxservice.pdf.PdfReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
class TemplatePreviewDisabledControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PdfReportService pdfReportService;

    @Test
    void rejectsPreviewWhenDisabledWithoutRendering() throws Exception {
        mockMvc.perform(
                        post("/api/template-previews/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fields\":{}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_PREVIEW_DISABLED"));
        verifyNoInteractions(pdfReportService);
    }
}
