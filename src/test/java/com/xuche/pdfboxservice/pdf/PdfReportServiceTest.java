package com.xuche.pdfboxservice.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xuche.pdfboxservice.pdf.PdfTemplateProperties.FieldPlacement;
import com.xuche.pdfboxservice.pdf.PdfTemplateProperties.Template;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class PdfReportServiceTest {

    private final PdfReportService service = new PdfReportService(newRegistry());

    private static TemplateRegistry newRegistry() {
        Map<String, FieldPlacement> certificateFields =
                Map.of(
                        "recipient", new FieldPlacement(1, 180f, 400f, 28f, 432f),
                        "course", new FieldPlacement(1, 180f, 310f, 18f, 432f),
                        "date", new FieldPlacement(1, 235f, 240f, 12f, 180f));
        PdfTemplateProperties properties =
                new PdfTemplateProperties(
                        Map.of(
                                "report",
                                new Template("classpath:templates/report.pdf", Map.of()),
                                "certificate",
                                new Template(
                                        "classpath:templates/certificate.pdf", certificateFields)));
        return new TemplateRegistry(properties, new DefaultResourceLoader());
    }

    @Test
    void fillsTextFieldsAndFlattensForm() throws Exception {
        byte[] pdf =
                service.fill(
                        "report",
                        Map.of(
                                "title", "Q3 Sales Report",
                                "author", "Jane Doe",
                                "date", "2026-07-19",
                                "summary", "Sales are up 12% quarter over quarter."));

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            // Flattened: no form fields remain and values are baked into the page content.
            assertThat(filled.getDocumentCatalog().getAcroForm().getFields()).isEmpty();

            String text = new PDFTextStripper().getText(filled);
            assertThat(text)
                    .contains("Q3 Sales Report")
                    .contains("Jane Doe")
                    .contains("2026-07-19")
                    .contains("Sales are up 12% quarter over quarter.");
        }
    }

    @Test
    void overlaysValuesAtConfiguredCoordinatesWhenTemplateHasNoAcroForm() throws Exception {
        byte[] pdf =
                service.fill(
                        "certificate",
                        Map.of(
                                "recipient", "Jane Doe",
                                "course", "Advanced Origami",
                                "date", "2026-07-19"));

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            // Overlay values become page content directly; there is no form to flatten.
            assertThat(filled.getDocumentCatalog().getAcroForm()).isNull();

            String text = new PDFTextStripper().getText(filled);
            assertThat(text)
                    .contains("Jane Doe")
                    .contains("Advanced Origami")
                    .contains("2026-07-19");
        }
    }

    @Test
    void shrinksOverlayTextToFitMaxWidth() throws Exception {
        // Lowercase 'm' occurs only in this name, so its glyphs isolate the overlay font size.
        String longName = "Emmanuella Maximiliana Wilhelmina Charlotte von Hessen-Kassel";

        byte[] pdf = service.fill("certificate", Map.of("recipient", longName));

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            List<Float> nameFontSizes = new ArrayList<>();
            PDFTextStripper stripper =
                    new PDFTextStripper() {
                        @Override
                        protected void processTextPosition(TextPosition text) {
                            if ("m".equals(text.getUnicode())) {
                                nameFontSizes.add(text.getFontSizeInPt());
                            }
                        }
                    };
            stripper.getText(filled);

            // Configured size is 28 with maxWidth 432; the long name must have been shrunk.
            assertThat(nameFontSizes).isNotEmpty().allMatch(size -> size < 28f);
        }
    }

    @Test
    void rejectsUnknownFieldNames() {
        assertThatThrownBy(() -> service.fill("report", Map.of("title", "Hi", "bogus", "x")))
                .isInstanceOf(UnknownTemplateFieldException.class)
                .hasMessageContaining("bogus")
                .hasMessageContaining("report");
    }

    @Test
    void unknownTemplateThrowsNotFound() {
        assertThatThrownBy(() -> service.fill("does-not-exist", Map.of()))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void rejectsTemplateNamesThatCouldEscapeTheTemplateDirectory() {
        assertThatThrownBy(() -> service.fill("../application", Map.of()))
                .isInstanceOf(TemplateNotFoundException.class);
    }
}
