package com.xuche.pdfboxservice.pdf.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xuche.pdfboxservice.pdf.limits.PdfRequestLimits;
import com.xuche.pdfboxservice.pdf.limits.RequestLimitExceededException;
import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties;
import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties.FieldPlacement;
import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties.FieldType;
import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties.Template;
import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties.TextAlignment;
import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties.TextOverflow;
import com.xuche.pdfboxservice.pdf.template.TemplateNotFoundException;
import com.xuche.pdfboxservice.pdf.template.TemplateRegistry;
import com.xuche.pdfboxservice.pdf.template.TemplateVersionNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
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
    void rendersUnicodeWithConfiguredTemplateFont() throws Exception {
        Map<String, FieldPlacement> fields =
                Map.of("title", new FieldPlacement(1, 150f, 665f, 14f, 400f, null));
        TemplateRegistry registry =
                new TemplateRegistry(
                        new PdfTemplateProperties(
                                Map.of(
                                        "report",
                                        new Template(
                                                "classpath:templates/report.pdf",
                                                "classpath:templates/test-font.ttf",
                                                fields))),
                        new DefaultResourceLoader());

        PdfReportService unicodeService = new PdfReportService(registry);
        byte[] pdf = unicodeService.fill("report", Map.of("title", "café"));
        try (PDDocument filled = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(filled)).contains("café");
        }
        PdfReportService.TemplatePreview preview =
                unicodeService.preview("report", null, Map.of("title", "café"));
        assertThat(preview.fields().getFirst().font())
                .isEqualTo("classpath:templates/test-font.ttf");
        try (PDDocument previewDocument = Loader.loadPDF(preview.report().pdfBytes())) {
            assertThat(new PDFTextStripper().getText(previewDocument)).contains("café");
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
    void previewRendersDefaultsAndReturnsPlacementMetadata() throws Exception {
        PdfReportService.TemplatePreview preview =
                service.preview("report", null, Map.of("title", "Preview"));

        assertThat(preview.report().templateVersion()).isEqualTo("v1");
        assertThat(preview.fields())
                .extracting(PdfReportService.FieldPreview::name)
                .contains("title", "confidential");
        assertThat(
                        preview.fields().stream()
                                .filter(field -> field.name().equals("title"))
                                .findFirst()
                                .orElseThrow()
                                .value())
                .isEqualTo("Preview");
        assertThat(
                        preview.fields().stream()
                                .filter(field -> field.name().equals("confidential"))
                                .findFirst()
                                .orElseThrow()
                                .value())
                .isEqualTo(false);
        try (PDDocument filled = Loader.loadPDF(preview.report().pdfBytes())) {
            assertThat(new PDFTextStripper().getText(filled)).contains("Preview");
        }
    }

    @Test
    void rejectsValuesOfTheWrongJsonType() {
        // checkbox fields require a JSON boolean
        assertThatThrownBy(() -> service.fill("report", Map.of("confidential", "yes")))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("confidential")
                .hasMessageContaining("expected a JSON boolean")
                .hasMessageContaining("string");
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
    void previewMetadataUsesTheEffectiveFontSizeRenderedToFit() {
        String longName = "Emmanuella Maximiliana Wilhelmina Charlotte von Hessen-Kassel";

        PdfReportService.TemplatePreview preview =
                service.preview("certificate", "v1", Map.of("recipient", longName));

        assertThat(preview.fields())
                .filteredOn(field -> field.name().equals("recipient"))
                .singleElement()
                .extracting(PdfReportService.FieldPreview::fontSize)
                .matches(fontSize -> fontSize < 28f);
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
    void wrapsMultilineTextWithinItsConfiguredWidth() throws Exception {
        Map<String, FieldPlacement> fields =
                Map.of(
                        "summary",
                        new FieldPlacement(
                                1,
                                150f,
                                665f,
                                12f,
                                75f,
                                40f,
                                14f,
                                TextAlignment.LEFT,
                                TextOverflow.REJECT,
                                null));
        PdfTemplateProperties properties =
                new PdfTemplateProperties(
                        Map.of("report", new Template("classpath:templates/report.pdf", fields)));
        PdfReportService multilineService =
                new PdfReportService(new TemplateRegistry(properties, new DefaultResourceLoader()));

        byte[] pdf = multilineService.fill("report", Map.of("summary", "alpha beta gamma delta"));

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(filled);
            assertThat(text).contains("alpha beta").contains("gamma delta");
        }
    }

    @Test
    void rejectsMultilineTextThatExceedsConfiguredHeight() {
        Map<String, FieldPlacement> fields =
                Map.of(
                        "summary",
                        new FieldPlacement(
                                1,
                                150f,
                                665f,
                                12f,
                                100f,
                                10f,
                                14f,
                                TextAlignment.LEFT,
                                TextOverflow.REJECT,
                                null));
        PdfTemplateProperties properties =
                new PdfTemplateProperties(
                        Map.of("report", new Template("classpath:templates/report.pdf", fields)));
        PdfReportService multilineService =
                new PdfReportService(new TemplateRegistry(properties, new DefaultResourceLoader()));

        assertThatThrownBy(
                        () ->
                                multilineService.fill(
                                        "report", Map.of("summary", "alpha beta gamma")))
                .isInstanceOf(TextOverflowException.class)
                .hasMessageContaining("summary")
                .hasMessageContaining("report");
    }

    @Test
    void rejectsUnknownFieldNames() {
        assertThatThrownBy(() -> service.fill("report", Map.of("title", "Hi", "bogus", "x")))
                .isInstanceOf(UnknownTemplateFieldException.class)
                .hasMessageContaining("bogus")
                .hasMessageContaining("report");
    }

    @Test
    void rejectsNullValuesAndConfiguredLimits() {
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("title", null);
        assertThatThrownBy(() -> service.fill("report", nullValue))
                .isInstanceOf(InvalidFieldValueException.class);

        PdfReportService limited =
                new PdfReportService(newRegistry(), new PdfRequestLimits(1024, 1, 3, 25_000_000));
        assertThatThrownBy(() -> limited.fill("report", Map.of("title", "abcd")))
                .isInstanceOf(RequestLimitExceededException.class)
                .hasMessageContaining("text value");
        assertThatThrownBy(() -> limited.fill("report", Map.of("title", "a", "author", "b")))
                .isInstanceOf(RequestLimitExceededException.class)
                .hasMessageContaining("too many fields");
    }

    @Test
    void unknownTemplateThrowsNotFound() {
        assertThatThrownBy(() -> service.fill("does-not-exist", Map.of()))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void unavailableTemplateVersionThrowsVersionNotFound() {
        assertThatThrownBy(() -> service.generate("report", "v2", Map.of()))
                .isInstanceOf(TemplateVersionNotFoundException.class)
                .hasMessageContaining("report")
                .hasMessageContaining("v2");
    }

    @Test
    void rejectsTemplateNamesThatCouldEscapeTheTemplateDirectory() {
        assertThatThrownBy(() -> service.fill("../application", Map.of()))
                .isInstanceOf(TemplateNotFoundException.class);
    }
}
