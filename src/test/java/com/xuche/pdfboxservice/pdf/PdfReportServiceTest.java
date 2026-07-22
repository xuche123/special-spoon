package com.xuche.pdfboxservice.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xuche.pdfboxservice.pdf.PdfTemplateProperties.FieldPlacement;
import com.xuche.pdfboxservice.pdf.PdfTemplateProperties.FieldType;
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
        Map<String, FieldPlacement> reportFields =
                Map.of(
                        "title", new FieldPlacement(1, 150f, 665f, 14f, 400f, null),
                        "author", new FieldPlacement(1, 150f, 615f, 14f, 400f, null),
                        "date", new FieldPlacement(1, 150f, 565f, 14f, 400f, null),
                        "summary", new FieldPlacement(1, 150f, 525f, 12f, 400f, null),
                        "confidential",
                                new FieldPlacement(2, 73f, 679f, 12f, null, FieldType.CHECKBOX),
                        "reviewed", new FieldPlacement(2, 73f, 639f, 12f, null, FieldType.CHECKBOX),
                        "approved", new FieldPlacement(2, 73f, 599f, 12f, null, FieldType.CHECKBOX),
                        "notes", new FieldPlacement(3, 150f, 665f, 12f, 400f, null));
        Map<String, FieldPlacement> certificateFields =
                Map.of(
                        "recipient", new FieldPlacement(1, 180f, 400f, 28f, 432f, null),
                        "course", new FieldPlacement(1, 180f, 310f, 18f, 432f, null),
                        "date", new FieldPlacement(1, 235f, 240f, 12f, 180f, null),
                        "module-basics",
                                new FieldPlacement(2, 189f, 478f, 14f, null, FieldType.CHECKBOX),
                        "module-advanced",
                                new FieldPlacement(2, 189f, 438f, 14f, null, FieldType.CHECKBOX),
                        "module-project",
                                new FieldPlacement(2, 189f, 398f, 14f, null, FieldType.CHECKBOX),
                        "instructor-name", new FieldPlacement(3, 180f, 450f, 16f, 300f, null),
                        "instructor-date", new FieldPlacement(3, 240f, 390f, 12f, 170f, null));
        PdfTemplateProperties properties =
                new PdfTemplateProperties(
                        Map.of(
                                "report",
                                new Template("classpath:templates/report.pdf", reportFields),
                                "certificate",
                                new Template(
                                        "classpath:templates/certificate.pdf", certificateFields)));
        return new TemplateRegistry(properties, new DefaultResourceLoader());
    }

    @Test
    void overlaysReportTextFields() throws Exception {
        byte[] pdf =
                service.fill(
                        "report",
                        Map.of(
                                "title", "Q3 Sales Report",
                                "author", "Jane Doe",
                                "date", "2026-07-19",
                                "summary", "Sales are up 12% quarter over quarter.",
                                "notes", "Follow up with the west region."));

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            assertThat(filled.getDocumentCatalog().getAcroForm()).isNull();

            String text = new PDFTextStripper().getText(filled);
            assertThat(text)
                    .contains("Q3 Sales Report")
                    .contains("Jane Doe")
                    .contains("2026-07-19")
                    .contains("Sales are up 12% quarter over quarter.")
                    .contains("Follow up with the west region.");
        }
    }

    @Test
    void overlaysCheckboxes() throws Exception {
        byte[] pdf =
                service.fill(
                        "report",
                        Map.of("confidential", true, "reviewed", false, "approved", true));

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            // Two boxes checked -> two X marks in the page content.
            String text = new PDFTextStripper().getText(filled);
            assertThat(text).contains("X");
        }
    }

    @Test
    void rejectsValuesOfTheWrongJsonType() {
        // checkbox fields require a JSON boolean
        assertThatThrownBy(() -> service.fill("report", Map.of("confidential", "yes")))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("confidential")
                .hasMessageContaining("expected a JSON boolean")
                .hasMessageContaining("string \"yes\"");
        assertThatThrownBy(() -> service.fill("report", Map.of("confidential", 1)))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("number 1");

        // text fields require a JSON string
        assertThatThrownBy(() -> service.fill("report", Map.of("title", true)))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("title")
                .hasMessageContaining("expected a JSON string")
                .hasMessageContaining("boolean true");
    }

    @Test
    void overlaysCertificateFieldsAcrossPages() throws Exception {
        byte[] pdf =
                service.fill(
                        "certificate",
                        Map.of(
                                "recipient", "Jane Doe",
                                "course", "Advanced Origami",
                                "date", "2026-07-19",
                                "module-basics", true,
                                "module-project", true,
                                "instructor-name", "Prof. Crane",
                                "instructor-date", "2026-07-19"));

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(filled);
            assertThat(text)
                    .contains("Jane Doe")
                    .contains("Advanced Origami")
                    .contains("2026-07-19")
                    .contains("Prof. Crane")
                    // two checked checklist boxes
                    .contains("X");
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
