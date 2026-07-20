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
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;

/**
 * Regenerates the sample templates:
 *
 * <ul>
 *   <li>{@code src/main/resources/templates/report.pdf} — 3-page AcroForm template: text fields
 *       (p1), checkboxes (p2), sign-off text fields and a digital signature field (p3)
 *   <li>{@code src/main/resources/templates/certificate.pdf} — 3-page form-free template filled via
 *       the coordinate overlay (placements live in {@code templates.yml}): certificate (p1),
 *       checklist with checkbox squares (p2), instructor sign-off (p3)
 *   <li>{@code src/test/resources/templates/bare.pdf} — form-free template with no overlay config,
 *       used to test the registry validation
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
    private static final float CHECKBOX_SIZE = 14f;

    private ReportTemplateGenerator() {}

    public static void main(String[] args) throws IOException {
        writeAcroFormReportTemplate(Path.of("src/main/resources/templates/report.pdf"));
        writeCertificateTemplate(Path.of("src/main/resources/templates/certificate.pdf"));
        writeBareTemplate(Path.of("src/test/resources/templates/bare.pdf"));
    }

    private static void writeAcroFormReportTemplate(Path output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDAcroForm acroForm = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(acroForm);

            // Text fields inherit this appearance; without it filled values render blank.
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), HELVETICA);
            acroForm.setDefaultResources(resources);
            acroForm.setDefaultAppearance("/Helv 12 Tf 0 g");
            acroForm.setNeedAppearances(true);

            // Page 1: report text fields
            PDPage page1 = addPage(document, PDRectangle.LETTER);
            try (PDPageContentStream content = new PDPageContentStream(document, page1)) {
                text(content, HELVETICA_BOLD, 20, 50, 730, "Sample Report");
                text(content, HELVETICA, 12, 50, 665, "Title:");
                text(content, HELVETICA, 12, 50, 615, "Author:");
                text(content, HELVETICA, 12, 50, 565, "Date:");
                text(content, HELVETICA, 12, 50, 525, "Summary:");
            }
            addTextField(acroForm, page1, "title", 150, 660, 400, 24, false);
            addTextField(acroForm, page1, "author", 150, 610, 400, 24, false);
            addTextField(acroForm, page1, "date", 150, 560, 400, 24, false);
            addTextField(acroForm, page1, "summary", 150, 380, 400, 150, true);

            // Page 2: review checkboxes
            PDPage page2 = addPage(document, PDRectangle.LETTER);
            try (PDPageContentStream content = new PDPageContentStream(document, page2)) {
                text(content, HELVETICA_BOLD, 20, 50, 730, "Review");
            }
            addCheckBox(document, acroForm, page2, "confidential", 70, 680, "Mark as confidential");
            addCheckBox(document, acroForm, page2, "reviewed", 70, 640, "Reviewed by team lead");
            addCheckBox(document, acroForm, page2, "approved", 70, 600, "Approved for release");

            // Page 3: sign-off with a digital signature field
            PDPage page3 = addPage(document, PDRectangle.LETTER);
            try (PDPageContentStream content = new PDPageContentStream(document, page3)) {
                text(content, HELVETICA_BOLD, 20, 50, 730, "Sign-off");
                text(content, HELVETICA, 12, 50, 665, "Signed by:");
                text(content, HELVETICA, 12, 50, 615, "Date:");
                text(content, HELVETICA, 12, 50, 555, "Digital signature:");
                content.addRect(150, 500, 300, 50);
                content.stroke();
            }
            addTextField(acroForm, page3, "signed-by", 150, 660, 400, 24, false);
            addTextField(acroForm, page3, "signature-date", 150, 610, 400, 24, false);
            addSignatureField(acroForm, page3, "signature", 150, 500, 300, 50);

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
                checklistItem(content, 480, "Module 1: Folding Fundamentals");
                checklistItem(content, 440, "Module 2: Advanced Crease Patterns");
                checklistItem(content, 400, "Module 3: Capstone Project");
            }

            // Page 3: instructor sign-off
            PDPage page3 = addPage(document, landscape);
            try (PDPageContentStream content = new PDPageContentStream(document, page3)) {
                centeredText(content, HELVETICA_BOLD, 24, "SIGN-OFF", landscape.getWidth(), 540);
                text(content, HELVETICA, 14, 180, 455, "Instructor:");
                line(content, 180, 445, 480, 445);
                text(content, HELVETICA, 14, 180, 395, "Date:");
                line(content, 235, 385, 415, 385);
                text(content, HELVETICA, 14, 500, 412, "Signature:");
                content.addRect(500, 340, 180, 60);
                content.stroke();
            }

            save(document, output);
        }
    }

    private static void writeBareTemplate(Path output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = addPage(document, PDRectangle.LETTER);
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

    private static PDPage addPage(PDDocument document, PDRectangle size) {
        PDPage page = new PDPage(size);
        document.addPage(page);
        return page;
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
        addWidget(page, field.getWidgets().get(0), x, y, width, height);
    }

    private static void addCheckBox(
            PDDocument document,
            PDAcroForm acroForm,
            PDPage page,
            String name,
            float x,
            float y,
            String label)
            throws IOException {
        try (PDPageContentStream content =
                new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            text(content, HELVETICA, 12, x + 25, y + 4, label);
        }
        PDCheckBox checkBox = new PDCheckBox(acroForm);
        checkBox.setPartialName(name);
        acroForm.getFields().add(checkBox);

        PDAnnotationWidget widget = checkBox.getWidgets().get(0);
        addWidget(page, widget, x, y, CHECKBOX_SIZE, CHECKBOX_SIZE);

        // PDFBox does not generate checkbox appearances; without explicit on/off appearance
        // streams the checked state renders nothing (and the on value is undefined).
        COSDictionary states = new COSDictionary();
        states.setItem(COSName.getPDFName("Yes"), checkboxAppearance(document, true));
        states.setItem(COSName.OFF, checkboxAppearance(document, false));
        PDAppearanceDictionary appearance = new PDAppearanceDictionary();
        appearance.setNormalAppearance(new PDAppearanceEntry(states));
        widget.setAppearance(appearance);
    }

    private static PDAppearanceStream checkboxAppearance(PDDocument document, boolean checked)
            throws IOException {
        PDType1Font zapfDingbats = new PDType1Font(Standard14Fonts.FontName.ZAPF_DINGBATS);
        PDAppearanceStream stream = new PDAppearanceStream(document);
        stream.setBBox(new PDRectangle(CHECKBOX_SIZE, CHECKBOX_SIZE));
        PDResources resources = new PDResources();
        resources.put(COSName.getPDFName("ZaDb"), zapfDingbats);
        stream.setResources(resources);
        try (PDPageContentStream content = new PDPageContentStream(document, stream)) {
            content.setLineWidth(1);
            content.addRect(1, 1, CHECKBOX_SIZE - 2, CHECKBOX_SIZE - 2);
            content.stroke();
            if (checked) {
                content.beginText();
                content.setFont(zapfDingbats, CHECKBOX_SIZE - 4);
                content.newLineAtOffset(3, 3);
                content.showText("\u2714"); // check mark; encoded as glyph 0x34 in ZapfDingbats
                content.endText();
            }
        }
        return stream;
    }

    private static void addSignatureField(
            PDAcroForm acroForm,
            PDPage page,
            String name,
            float x,
            float y,
            float width,
            float height)
            throws IOException {
        PDSignatureField signatureField = new PDSignatureField(acroForm);
        signatureField.setPartialName(name);
        acroForm.getFields().add(signatureField);
        addWidget(page, signatureField.getWidgets().get(0), x, y, width, height);
    }

    private static void addWidget(
            PDPage page, PDAnnotationWidget widget, float x, float y, float width, float height)
            throws IOException {
        widget.setRectangle(new PDRectangle(x, y, width, height));
        widget.setPage(page);
        widget.setPrinted(true);
        widget.setAppearanceCharacteristics(borderCharacteristics());
        page.getAnnotations().add(widget);
    }

    private static PDAppearanceCharacteristicsDictionary borderCharacteristics() {
        PDAppearanceCharacteristicsDictionary characteristics =
                new PDAppearanceCharacteristicsDictionary(new COSDictionary());
        characteristics.setBorderColour(
                new PDColor(new float[] {0f, 0f, 0f}, PDDeviceRGB.INSTANCE));
        characteristics.setBackground(new PDColor(new float[] {1f, 1f, 1f}, PDDeviceRGB.INSTANCE));
        return characteristics;
    }

    private static void checklistItem(PDPageContentStream content, float y, String label)
            throws IOException {
        content.addRect(186, y - 4, 14, 14);
        content.stroke();
        text(content, HELVETICA, 14, 210, y, label);
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
