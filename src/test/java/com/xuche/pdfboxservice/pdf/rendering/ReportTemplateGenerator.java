package com.xuche.pdfboxservice.pdf.rendering;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

/**
 * Regenerates the sample templates (all form-free; values are overlaid at the coordinates declared
 * in {@code templates.yml}):
 *
 * <ul>
 *   <li>{@code src/main/resources/templates/report.pdf} — 3 pages: text lines (p1), checkbox
 *       squares (p2), notes (p3)
 *   <li>{@code src/main/resources/templates/certificate.pdf} — 3 pages: certificate (p1), checklist
 *       with checkbox squares (p2), instructor sign-off (p3)
 *   <li>{@code src/test/resources/templates/bare.pdf} — form-free fixture for the "placements
 *       required" registry validation
 *   <li>{@code src/test/resources/templates/acroform.pdf} — AcroForm fixture for the "AcroForm not
 *       supported" registry validation
 * </ul>
 *
 * <p>Run with:
 *
 * <pre>
 * ./mvnw -q test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *     -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.xuche.pdfboxservice.pdf.rendering.ReportTemplateGenerator
 * </pre>
 */
public final class ReportTemplateGenerator {

    private static final PDType1Font HELVETICA =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font HELVETICA_BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private ReportTemplateGenerator() {}

    public static void main(String[] args) throws IOException {
        writeReportTemplate(Path.of("src/main/resources/templates/report.pdf"));
        writeCertificateTemplate(Path.of("src/main/resources/templates/certificate.pdf"));
        writeBareTemplate(Path.of("src/test/resources/templates/bare.pdf"));
        writeAcroFormFixture(Path.of("src/test/resources/templates/acroform.pdf"));
    }

    private static void writeReportTemplate(Path output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // Page 1: report fields
            PDPage page1 = addPage(document, PDRectangle.LETTER);
            try (PDPageContentStream content = new PDPageContentStream(document, page1)) {
                text(content, HELVETICA_BOLD, 20, 50, 730, "Sample Report");
                labeledLine(content, "Title:", 665);
                labeledLine(content, "Author:", 615);
                labeledLine(content, "Date:", 565);
                labeledLine(content, "Summary:", 525);
            }

            // Page 2: review checkboxes
            PDPage page2 = addPage(document, PDRectangle.LETTER);
            try (PDPageContentStream content = new PDPageContentStream(document, page2)) {
                text(content, HELVETICA_BOLD, 20, 50, 730, "Review");
                checkboxItem(content, 680, "Mark as confidential");
                checkboxItem(content, 640, "Reviewed by team lead");
                checkboxItem(content, 600, "Approved for release");
            }

            // Page 3: notes
            PDPage page3 = addPage(document, PDRectangle.LETTER);
            try (PDPageContentStream content = new PDPageContentStream(document, page3)) {
                text(content, HELVETICA_BOLD, 20, 50, 730, "Notes");
                labeledLine(content, "Notes:", 665);
            }

            save(document, output);
        }
    }

    private static void writeCertificateTemplate(Path output) throws IOException {
        PDRectangle landscape =
                new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());
        try (PDDocument document = new PDDocument()) {
            // Page 1: certificate
            PDPage page1 = addPage(document, landscape);
            try (PDPageContentStream content = new PDPageContentStream(document, page1)) {
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

            // Page 2: course checklist with empty checkbox squares (overlay draws the X)
            PDPage page2 = addPage(document, landscape);
            try (PDPageContentStream content = new PDPageContentStream(document, page2)) {
                centeredText(
                        content, HELVETICA_BOLD, 24, "COURSE CHECKLIST", landscape.getWidth(), 540);
                checkboxItem(content, 480, "Module 1: Folding Fundamentals", 186, 210);
                checkboxItem(content, 440, "Module 2: Advanced Crease Patterns", 186, 210);
                checkboxItem(content, 400, "Module 3: Capstone Project", 186, 210);
            }

            // Page 3: instructor sign-off
            PDPage page3 = addPage(document, landscape);
            try (PDPageContentStream content = new PDPageContentStream(document, page3)) {
                centeredText(content, HELVETICA_BOLD, 24, "SIGN-OFF", landscape.getWidth(), 540);
                text(content, HELVETICA, 14, 180, 455, "Instructor:");
                line(content, 180, 445, 480, 445);
                text(content, HELVETICA, 14, 180, 395, "Date:");
                line(content, 235, 385, 415, 385);
            }

            save(document, output);
        }
    }

    private static void writeBareTemplate(Path output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = addPage(document, PDRectangle.LETTER);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                text(content, HELVETICA, 12, 50, 750, "Bare template: no overlay config.");
            }
            save(document, output);
        }
    }

    /** Minimal AcroForm PDF used to verify the registry rejects form-based templates. */
    private static void writeAcroFormFixture(Path output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = addPage(document, PDRectangle.LETTER);
            PDAcroForm acroForm = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(acroForm);
            PDTextField field = new PDTextField(acroForm);
            field.setPartialName("field1");
            acroForm.getFields().add(field);
            PDAnnotationWidget widget = field.getWidgets().get(0);
            widget.setRectangle(new PDRectangle(150, 700, 200, 20));
            widget.setPage(page);
            page.getAnnotations().add(widget);
            save(document, output);
        }
    }

    private static PDPage addPage(PDDocument document, PDRectangle size) {
        PDPage page = new PDPage(size);
        document.addPage(page);
        return page;
    }

    /** A label at x=50 and a fill-in line from x=150 to x=550, both at height {@code y}. */
    private static void labeledLine(PDPageContentStream content, String label, float y)
            throws IOException {
        text(content, HELVETICA, 12, 50, y, label);
        line(content, 150, y - 5, 550, y - 5);
    }

    private static void checkboxItem(PDPageContentStream content, float y, String label)
            throws IOException {
        checkboxItem(content, y, label, 70, 95);
    }

    private static void checkboxItem(
            PDPageContentStream content, float y, String label, float boxX, float labelX)
            throws IOException {
        content.addRect(boxX, y - 4, 14, 14);
        content.stroke();
        text(content, HELVETICA, 12, labelX, y, label);
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
