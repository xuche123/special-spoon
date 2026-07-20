package com.xuche.pdfboxservice.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xuche.pdfboxservice.pdf.PdfTemplateProperties.FieldPlacement;
import com.xuche.pdfboxservice.pdf.PdfTemplateProperties.FieldType;
import com.xuche.pdfboxservice.pdf.PdfTemplateProperties.Template;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class PdfReportServiceTest {

    private final PdfReportService service = new PdfReportService(newRegistry());

    private static TemplateRegistry newRegistry() {
        Map<String, FieldPlacement> certificateFields =
                Map.of(
                        "recipient", new FieldPlacement(1, 180f, 400f, 28f, 432f, null, null, null),
                        "course", new FieldPlacement(1, 180f, 310f, 18f, 432f, null, null, null),
                        "date", new FieldPlacement(1, 235f, 240f, 12f, 180f, null, null, null),
                        "module-basics",
                                new FieldPlacement(
                                        2, 189f, 478f, 14f, null, FieldType.CHECKBOX, null, null),
                        "module-advanced",
                                new FieldPlacement(
                                        2, 189f, 438f, 14f, null, FieldType.CHECKBOX, null, null),
                        "module-project",
                                new FieldPlacement(
                                        2, 189f, 398f, 14f, null, FieldType.CHECKBOX, null, null),
                        "instructor-name",
                                new FieldPlacement(3, 180f, 450f, 16f, 300f, null, null, null),
                        "instructor-date",
                                new FieldPlacement(3, 240f, 390f, 12f, 170f, null, null, null),
                        "instructor-signature",
                                new FieldPlacement(
                                        3, 500f, 340f, null, null, FieldType.SIGNATURE, 180f, 60f));
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

    /** A small PNG "signature" as a data URL, like a canvas e-signature capture would send. */
    private static String pngSignature() {
        try {
            BufferedImage image = new BufferedImage(120, 50, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            graphics.drawLine(5, 40, 60, 10);
            graphics.drawLine(60, 10, 115, 35);
            graphics.dispose();
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean pageContainsImage(PDDocument document, int pageIndex)
            throws IOException {
        PDResources resources = document.getPage(pageIndex).getResources();
        for (COSName name : resources.getXObjectNames()) {
            if (resources.getXObject(name) instanceof PDImageXObject) {
                return true;
            }
        }
        return false;
    }

    @Test
    void fillsTextFieldsAndFlattensFormButKeepsSignatureField() throws Exception {
        byte[] pdf =
                service.fill(
                        "report",
                        Map.of(
                                "title", "Q3 Sales Report",
                                "author", "Jane Doe",
                                "date", "2026-07-19",
                                "summary", "Sales are up 12% quarter over quarter.",
                                "signed-by", "Jane Doe",
                                "signature-date", "2026-07-19"),
                        Map.of());

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            // Text/checkbox fields are flattened away; the unsigned signature field survives so
            // the filled report can still be signed digitally.
            assertThat(filled.getDocumentCatalog().getAcroForm().getFields())
                    .extracting(PDField::getFullyQualifiedName)
                    .containsExactly("signature");

            String text = new PDFTextStripper().getText(filled);
            assertThat(text)
                    .contains("Q3 Sales Report")
                    .contains("Jane Doe")
                    .contains("2026-07-19")
                    .contains("Sales are up 12% quarter over quarter.");
        }
    }

    @Test
    void fillsCheckboxes() throws Exception {
        byte[] pdf =
                service.fill(
                        "report",
                        Map.of("confidential", "true", "reviewed", "no", "approved", "yes"),
                        Map.of());

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            // Two boxes checked -> check glyphs baked into the flattened page content.
            String text = new PDFTextStripper().getText(filled);
            assertThat(text).containsAnyOf("✓", "✔");
        }
    }

    @Test
    void rejectsInvalidCheckboxValues() {
        assertThatThrownBy(() -> service.fill("report", Map.of("confidential", "maybe"), Map.of()))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("confidential")
                .hasMessageContaining("maybe");
    }

    @Test
    void stampsSignatureIntoAcroFormSignatureBox() throws Exception {
        byte[] pdf = service.fill("report", Map.of(), Map.of("signature", pngSignature()));

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            // All fields are gone: text/checkbox fields flattened, the signature field removed
            // after its box was inked.
            assertThat(filled.getDocumentCatalog().getAcroForm().getFields()).isEmpty();
            assertThat(pageContainsImage(filled, 2)).isTrue();
        }
    }

    @Test
    void stampsSignatureIntoOverlaySignatureBox() throws Exception {
        byte[] pdf =
                service.fill(
                        "certificate",
                        Map.of("instructor-name", "Prof. Crane"),
                        Map.of("instructor-signature", pngSignature()));

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            assertThat(pageContainsImage(filled, 2)).isTrue();
        }
    }

    @Test
    void rejectsUnknownSignatureFields() {
        assertThatThrownBy(() -> service.fill("report", Map.of(), Map.of("bogus", pngSignature())))
                .isInstanceOf(UnknownTemplateFieldException.class)
                .hasMessageContaining("bogus");
    }

    @Test
    void rejectsUndecodableSignatures() {
        assertThatThrownBy(
                        () ->
                                service.fill(
                                        "report", Map.of(), Map.of("signature", "not-base64!!!")))
                .isInstanceOf(InvalidSignatureException.class)
                .hasMessageContaining("base64");

        // valid base64, but not a PNG or JPEG
        String notAnImage = Base64.getEncoder().encodeToString("hello".getBytes());
        assertThatThrownBy(() -> service.fill("report", Map.of(), Map.of("signature", notAnImage)))
                .isInstanceOf(InvalidSignatureException.class)
                .hasMessageContaining("PNG or JPEG");
    }

    @Test
    void overlaysValuesAtConfiguredCoordinatesWhenTemplateHasNoAcroForm() throws Exception {
        byte[] pdf =
                service.fill(
                        "certificate",
                        Map.of(
                                "recipient", "Jane Doe",
                                "course", "Advanced Origami",
                                "date", "2026-07-19",
                                "module-basics", "true",
                                "module-project", "yes",
                                "instructor-name", "Prof. Crane",
                                "instructor-date", "2026-07-19"),
                        Map.of());

        try (PDDocument filled = Loader.loadPDF(pdf)) {
            // Overlay values become page content directly; there is no form to flatten.
            assertThat(filled.getDocumentCatalog().getAcroForm()).isNull();

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
    void rejectsInvalidOverlayCheckboxValues() {
        assertThatThrownBy(
                        () ->
                                service.fill(
                                        "certificate",
                                        Map.of("module-basics", "perhaps"),
                                        Map.of()))
                .isInstanceOf(InvalidFieldValueException.class)
                .hasMessageContaining("module-basics");
    }

    @Test
    void shrinksOverlayTextToFitMaxWidth() throws Exception {
        // Lowercase 'm' occurs only in this name, so its glyphs isolate the overlay font size.
        String longName = "Emmanuella Maximiliana Wilhelmina Charlotte von Hessen-Kassel";

        byte[] pdf = service.fill("certificate", Map.of("recipient", longName), Map.of());

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
        assertThatThrownBy(
                        () -> service.fill("report", Map.of("title", "Hi", "bogus", "x"), Map.of()))
                .isInstanceOf(UnknownTemplateFieldException.class)
                .hasMessageContaining("bogus")
                .hasMessageContaining("report");
    }

    @Test
    void unknownTemplateThrowsNotFound() {
        assertThatThrownBy(() -> service.fill("does-not-exist", Map.of(), Map.of()))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void rejectsTemplateNamesThatCouldEscapeTheTemplateDirectory() {
        assertThatThrownBy(() -> service.fill("../application", Map.of(), Map.of()))
                .isInstanceOf(TemplateNotFoundException.class);
    }
}
