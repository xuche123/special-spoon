package com.xuche.pdfboxservice.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceCharacteristicsDictionary;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;

/**
 * Regenerates the sample templates:
 *
 * <ul>
 *   <li>{@code src/main/resources/templates/report.pdf} — AcroForm template
 *   <li>{@code src/main/resources/templates/certificate.pdf} — form-free template filled via the
 *       coordinate overlay (placements live in the hand-written {@code certificate.json})
 *   <li>{@code src/test/resources/templates/bare.pdf} — form-free template with no overlay config,
 *       used to test the 422 response
 * </ul>
 *
 * <p>Run with:
 *
 * <pre>
 * ./mvnw -q test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *     -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.xuche.pdfboxservice.pdf.ReportTemplateGenerator
 * </pre>
 */
public final class ReportTemplateGenerator {

    private static final PDType1Font HELVETICA =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font HELVETICA_BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private ReportTemplateGenerator() {}

    public static void main(String[] args) throws IOException {
        writeAcroFormReportTemplate(Path.of("src/main/resources/templates/report.pdf"));
        writeCertificateTemplate(Path.of("src/main/resources/templates/certificate.pdf"));
        writeBareTemplate(Path.of("src/test/resources/templates/bare.pdf"));
    }

    private static void writeAcroFormReportTemplate(Path output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDAcroForm acroForm = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(acroForm);

            // Text fields inherit this appearance; without it filled values render blank.
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), HELVETICA);
            acroForm.setDefaultResources(resources);
            acroForm.setDefaultAppearance("/Helv 12 Tf 0 g");
            acroForm.setNeedAppearances(true);

            drawStaticContent(document, page);

            addTextField(acroForm, page, "title", 150, 660, 400, 24, false);
            addTextField(acroForm, page, "author", 150, 610, 400, 24, false);
            addTextField(acroForm, page, "date", 150, 560, 400, 24, false);
            addTextField(acroForm, page, "summary", 150, 380, 400, 150, true);

            save(document, output);
        }
    }

    private static void writeCertificateTemplate(Path output) throws IOException {
        PDRectangle landscape =
                new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(landscape);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setLineWidth(2);
                content.addRect(36, 36, 720, 540);
                content.stroke();

                centeredText(
                        content,
                        HELVETICA_BOLD,
                        30,
                        "CERTIFICATE OF COMPLETION",
                        landscape.getWidth(),
                        500);
                text(content, HELVETICA, 14, 180, 440, "This certifies that");
                line(content, 180, 395, 612, 395);
                text(content, HELVETICA, 14, 180, 350, "has successfully completed the course");
                line(content, 180, 305, 612, 305);
                text(content, HELVETICA, 12, 180, 240, "Date:");
                line(content, 235, 235, 415, 235);
            }

            save(document, output);
        }
    }

    private static void writeBareTemplate(Path output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                text(
                        content,
                        HELVETICA,
                        12,
                        50,
                        750,
                        "Bare template: no AcroForm, no overlay config.");
            }

            save(document, output);
        }
    }

    private static void drawStaticContent(PDDocument document, PDPage page) throws IOException {
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            text(content, HELVETICA_BOLD, 20, 50, 730, "Sample Report");
            text(content, HELVETICA, 12, 50, 665, "Title:");
            text(content, HELVETICA, 12, 50, 615, "Author:");
            text(content, HELVETICA, 12, 50, 565, "Date:");
            text(content, HELVETICA, 12, 50, 525, "Summary:");
        }
    }

    private static void addTextField(
            PDAcroForm acroForm,
            PDPage page,
            String name,
            float x,
            float y,
            float width,
            float height,
            boolean multiline)
            throws IOException {
        PDTextField field = new PDTextField(acroForm);
        field.setPartialName(name);
        field.setQ(PDVariableText.QUADDING_LEFT);
        if (multiline) {
            field.setMultiline(true);
        }
        acroForm.getFields().add(field);

        PDAnnotationWidget widget = field.getWidgets().get(0);
        widget.setRectangle(new PDRectangle(x, y, width, height));
        widget.setPage(page);
        widget.setPrinted(true);

        PDAppearanceCharacteristicsDictionary characteristics =
                new PDAppearanceCharacteristicsDictionary(new COSDictionary());
        characteristics.setBorderColour(
                new PDColor(new float[] {0f, 0f, 0f}, PDDeviceRGB.INSTANCE));
        characteristics.setBackground(new PDColor(new float[] {1f, 1f, 1f}, PDDeviceRGB.INSTANCE));
        widget.setAppearanceCharacteristics(characteristics);

        page.getAnnotations().add(widget);
    }

    private static void text(
            PDPageContentStream content,
            PDType1Font font,
            float size,
            float x,
            float y,
            String value)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(value);
        content.endText();
    }

    private static void centeredText(
            PDPageContentStream content,
            PDType1Font font,
            float size,
            String value,
            float pageWidth,
            float y)
            throws IOException {
        float width = font.getStringWidth(value) / 1000f * size;
        text(content, font, size, (pageWidth - width) / 2, y, value);
    }

    private static void line(PDPageContentStream content, float x1, float y1, float x2, float y2)
            throws IOException {
        content.moveTo(x1, y1);
        content.lineTo(x2, y2);
        content.stroke();
    }

    private static void save(PDDocument document, Path output) throws IOException {
        Files.createDirectories(output.getParent());
        document.save(output.toFile());
        System.out.println("Wrote " + output.toAbsolutePath());
    }
}
