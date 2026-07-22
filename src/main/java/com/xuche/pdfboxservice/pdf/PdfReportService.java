package com.xuche.pdfboxservice.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

/**
 * Fills form-free templates from the validated {@link TemplateRegistry} by drawing values as page
 * content at the configured coordinates, and returns the resulting PDF.
 *
 * <ul>
 *   <li>{@link PdfTemplateProperties.FieldType#TEXT} — the value is drawn at {@code page/x/y},
 *       optionally shrunk to fit {@code maxWidth}
 *   <li>{@link PdfTemplateProperties.FieldType#CHECKBOX} — an X is drawn when the value is
 *       boolean-ish true
 * </ul>
 *
 * Requested field names are validated against the template's supported fields; unknown names are
 * rejected with {@link UnknownTemplateFieldException}. Checkbox values must be boolean-ish
 * (true/false, yes/no, on/off, 1/0); anything else is rejected with {@link
 * InvalidFieldValueException}.
 */
@Service
class PdfReportService {

    private static final float DEFAULT_FONT_SIZE = 12f;

    private final TemplateRegistry templateRegistry;

    PdfReportService(TemplateRegistry templateRegistry) {
        this.templateRegistry = templateRegistry;
    }

    byte[] fill(String templateName, Map<String, String> fields) {
        ResolvedTemplate template = templateRegistry.get(templateName);
        if (template == null) {
            throw new TemplateNotFoundException(templateName);
        }

        Set<String> unknownFields = new TreeSet<>(fields.keySet());
        unknownFields.removeAll(template.knownFields());
        if (!unknownFields.isEmpty()) {
            throw new UnknownTemplateFieldException(
                    templateName, unknownFields, template.knownFields());
        }

        try (PDDocument document = Loader.loadPDF(template.pdfBytes())) {
            overlayFields(templateName, document, template, fields);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fill PDF template: " + templateName, e);
        }
    }

    private void overlayFields(
            String templateName,
            PDDocument document,
            ResolvedTemplate template,
            Map<String, String> values)
            throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            PdfTemplateProperties.FieldPlacement placement =
                    template.placements().get(entry.getKey());
            switch (placement.type()) {
                case TEXT -> drawValue(document, font, placement, entry.getValue());
                case CHECKBOX -> {
                    if (parseCheckboxValue(templateName, entry.getKey(), entry.getValue())) {
                        drawText(document, font, placement, "X");
                    }
                }
            }
        }
    }

    private void drawValue(
            PDDocument document,
            PDType1Font font,
            PdfTemplateProperties.FieldPlacement placement,
            String value)
            throws IOException {
        float fontSize = placement.fontSize() != null ? placement.fontSize() : DEFAULT_FONT_SIZE;
        if (placement.maxWidth() != null && !value.isEmpty()) {
            float width = font.getStringWidth(value) / 1000f * fontSize;
            if (width > placement.maxWidth()) {
                fontSize *= placement.maxWidth() / width;
            }
        }
        drawText(document, font, placement, value, fontSize);
    }

    private void drawText(
            PDDocument document,
            PDType1Font font,
            PdfTemplateProperties.FieldPlacement placement,
            String value)
            throws IOException {
        float fontSize = placement.fontSize() != null ? placement.fontSize() : DEFAULT_FONT_SIZE;
        drawText(document, font, placement, value, fontSize);
    }

    private void drawText(
            PDDocument document,
            PDType1Font font,
            PdfTemplateProperties.FieldPlacement placement,
            String value,
            float fontSize)
            throws IOException {
        try (PDPageContentStream content =
                new PDPageContentStream(
                        document,
                        document.getPage(placement.page() - 1),
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true)) {
            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(placement.x(), placement.y());
            content.showText(value);
            content.endText();
        }
    }

    private static boolean parseCheckboxValue(String templateName, String fieldName, String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw new InvalidFieldValueException(templateName, fieldName, value);
        };
    }
}
