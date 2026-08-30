package com.xuche.pdfboxservice.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Fills form-free templates from the validated {@link TemplateRegistry} by drawing values as page
 * content at the configured coordinates, and returns the resulting PDF.
 *
 * <ul>
 *   <li>{@link PdfTemplateProperties.FieldType#TEXT} — the value is drawn at {@code page/x/y},
 *       optionally shrunk to fit {@code maxWidth}
 *   <li>{@link PdfTemplateProperties.FieldType#CHECKBOX} — an X is drawn when the value is {@code
 *       true}
 * </ul>
 *
 * Requested field names are validated against the template's supported fields; unknown names are
 * rejected with {@link UnknownTemplateFieldException}. Values are validated against the field type:
 * text fields require a JSON string, checkbox fields a JSON boolean; anything else is rejected with
 * {@link InvalidFieldValueException}.
 */
@Service
class PdfReportService {

    /**
     * Standard-14 Helvetica: needs no embedding, but is limited to WinAnsi (Latin) characters. For
     * broader scripts, configure a classpath TTF with {@code PDType0Font} instead.
     */
    private static final PDType1Font DEFAULT_FONT =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final float DEFAULT_FONT_SIZE = 12f;

    /** Rendered at checkbox placements when the value is {@code true}. */
    private static final String CHECK_MARK = "X";

    private final TemplateRegistry templateRegistry;
    private final PdfRequestLimits limits;

    PdfReportService(TemplateRegistry templateRegistry) {
        this(templateRegistry, new PdfRequestLimits());
    }

    @Autowired
    PdfReportService(TemplateRegistry templateRegistry, PdfRequestLimits limits) {
        this.templateRegistry = templateRegistry;
        this.limits = limits;
    }

    byte[] fill(String templateName, Map<String, ?> fields) {
        return generate(templateName, null, fields).pdfBytes();
    }

    GeneratedReport generate(String templateName, String version, Map<String, ?> fields) {
        ResolvedTemplate template = templateRegistry.get(templateName, version);
        if (template == null) {
            if (version == null) {
                throw new TemplateNotFoundException(templateName);
            }
            throw new TemplateVersionNotFoundException(templateName, version);
        }

        if (fields.size() > limits.maxFields()) {
            throw new RequestLimitExceededException(
                    "The report contains too many fields.", templateName, null);
        }
        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            if (entry.getValue() instanceof String value
                    && value.codePointCount(0, value.length()) > limits.maxTextCodePoints()) {
                throw new RequestLimitExceededException(
                        "The text value exceeds the configured size limit.",
                        templateName,
                        entry.getKey());
            }
        }

        Set<String> unknownFields = new TreeSet<>(fields.keySet());
        unknownFields.removeAll(template.knownFields());
        if (!unknownFields.isEmpty()) {
            throw new UnknownTemplateFieldException(
                    templateName, template.version(), unknownFields, template.knownFields());
        }

        try (PDDocument document = Loader.loadPDF(template.pdfBytes())) {
            PDFont font =
                    template.fontBytes() == null
                            ? DEFAULT_FONT
                            : PDType0Font.load(
                                    document, new ByteArrayInputStream(template.fontBytes()));
            overlayFields(templateName, document, template, fields, font);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            if (output.size() > limits.maxGeneratedPdfBytes()) {
                throw new RequestLimitExceededException(
                        "The generated PDF exceeds the configured size limit.", templateName, null);
            }
            return new GeneratedReport(output.toByteArray(), template.version());
        } catch (IOException e) {
            throw new PdfRenderingFailedException(templateName, template.version(), e);
        }
    }

    TemplatePreview preview(String templateName, String version, Map<String, ?> suppliedFields) {
        ResolvedTemplate template = templateRegistry.get(templateName, version);
        if (template == null) {
            if (version == null) {
                throw new TemplateNotFoundException(templateName);
            }
            throw new TemplateVersionNotFoundException(templateName, version);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.putAll(suppliedFields);
        for (Map.Entry<String, PdfTemplateProperties.FieldPlacement> entry :
                template.placements().entrySet()) {
            Object defaultValue =
                    entry.getValue().type() == PdfTemplateProperties.FieldType.CHECKBOX
                            ? false
                            : "";
            values.putIfAbsent(entry.getKey(), defaultValue);
        }

        GeneratedReport report = generate(templateName, version, values);
        List<FieldPreview> fields = new ArrayList<>();
        for (Map.Entry<String, PdfTemplateProperties.FieldPlacement> entry :
                template.placements().entrySet()) {
            PdfTemplateProperties.FieldPlacement placement = entry.getValue();
            fields.add(
                    new FieldPreview(
                            entry.getKey(),
                            placement.page(),
                            placement.x(),
                            placement.y(),
                            configuredFontSize(placement),
                            placement.maxWidth(),
                            placement.maxHeight(),
                            placement.lineHeight(),
                            placement.alignment(),
                            placement.overflow(),
                            placement.type(),
                            template.font(),
                            values.get(entry.getKey())));
        }
        return new TemplatePreview(report, fields);
    }

    record GeneratedReport(byte[] pdfBytes, String templateVersion) {}

    record TemplatePreview(GeneratedReport report, List<FieldPreview> fields) {}

    record FieldPreview(
            String name,
            Integer page,
            Float x,
            Float y,
            float fontSize,
            Float maxWidth,
            Float maxHeight,
            Float lineHeight,
            PdfTemplateProperties.TextAlignment alignment,
            PdfTemplateProperties.TextOverflow overflow,
            PdfTemplateProperties.FieldType type,
            String font,
            Object value) {}

    private void overlayFields(
            String templateName,
            PDDocument document,
            ResolvedTemplate template,
            Map<String, ?> values,
            PDFont font)
            throws IOException {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            PdfTemplateProperties.FieldPlacement placement =
                    template.placements().get(entry.getKey());
            switch (placement.type()) {
                case TEXT -> {
                    if (!(entry.getValue() instanceof String text)) {
                        throw new InvalidFieldValueException(
                                templateName,
                                template.version(),
                                entry.getKey(),
                                "a JSON string",
                                entry.getValue());
                    }
                    drawText(
                            document,
                            font,
                            placement,
                            templateName,
                            template.version(),
                            entry.getKey(),
                            text);
                }
                case CHECKBOX -> {
                    if (!(entry.getValue() instanceof Boolean checked)) {
                        throw new InvalidFieldValueException(
                                templateName,
                                template.version(),
                                entry.getKey(),
                                "a JSON boolean",
                                entry.getValue());
                    }
                    if (checked) {
                        drawCheckMark(document, font, placement);
                    }
                }
            }
        }
    }

    /**
     * Draws a text field value, shrinking the configured font size proportionally to fit maxWidth.
     */
    private void drawText(
            PDDocument document,
            PDFont font,
            PdfTemplateProperties.FieldPlacement placement,
            String templateName,
            String version,
            String fieldName,
            String value)
            throws IOException {
        validateGlyphs(font, templateName, version, fieldName, value);
        float fontSize = configuredFontSize(placement);
        if (placement.maxWidth() == null || placement.maxHeight() == null || value.isEmpty()) {
            drawSingleLineText(document, font, placement, value, fontSize);
            return;
        }

        List<String> lines = wrapText(font, value, fontSize, placement.maxWidth());
        float lineHeight = placement.lineHeight();
        if (lines.stream()
                        .anyMatch(line -> widthExceeds(font, line, fontSize, placement.maxWidth()))
                || lines.size() * lineHeight > placement.maxHeight()) {
            throw new TextOverflowException(templateName, version, fieldName);
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            float lineWidth = textWidth(font, line, fontSize);
            float x = alignedX(placement, lineWidth);
            float y = placement.y() - index * lineHeight;
            drawValue(document, font, placement, line, fontSize, x, y);
        }
    }

    private void drawSingleLineText(
            PDDocument document,
            PDFont font,
            PdfTemplateProperties.FieldPlacement placement,
            String value,
            float fontSize)
            throws IOException {
        if (placement.maxWidth() != null && !value.isEmpty()) {
            float width = textWidth(font, value, fontSize);
            if (width > placement.maxWidth()) {
                fontSize *= placement.maxWidth() / width;
            }
        }
        drawValue(document, font, placement, value, fontSize);
    }

    private List<String> wrapText(PDFont font, String value, float fontSize, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.split("\\n", -1)) {
            if (paragraph.trim().isEmpty()) {
                lines.add("");
                continue;
            }
            String current = "";
            for (String word : paragraph.trim().split("\\s+")) {
                if (current.isEmpty()) {
                    current = word;
                } else if (textWidth(font, current + " " + word, fontSize) <= maxWidth) {
                    current += " " + word;
                } else {
                    lines.addAll(splitLongWord(font, current, fontSize, maxWidth));
                    current = word;
                }
            }
            if (!current.isEmpty()) {
                lines.addAll(splitLongWord(font, current, fontSize, maxWidth));
            }
        }
        return lines;
    }

    private List<String> splitLongWord(PDFont font, String word, float fontSize, float maxWidth)
            throws IOException {
        List<String> parts = new ArrayList<>();
        String current = "";
        for (int offset = 0; offset < word.length(); ) {
            int codePoint = word.codePointAt(offset);
            String candidate = current + new String(Character.toChars(codePoint));
            if (!current.isEmpty() && textWidth(font, candidate, fontSize) > maxWidth) {
                parts.add(current);
                current = new String(Character.toChars(codePoint));
            } else {
                current = candidate;
            }
            offset += Character.charCount(codePoint);
        }
        if (!current.isEmpty()) {
            parts.add(current);
        }
        return parts;
    }

    private static float textWidth(PDFont font, String value, float fontSize) throws IOException {
        return font.getStringWidth(value) / 1000f * fontSize;
    }

    private static boolean widthExceeds(PDFont font, String value, float fontSize, float maxWidth) {
        try {
            return textWidth(font, value, fontSize) > maxWidth;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to measure PDF text", e);
        }
    }

    private static float alignedX(PdfTemplateProperties.FieldPlacement placement, float lineWidth) {
        return switch (placement.alignment()) {
            case LEFT -> placement.x();
            case CENTER -> placement.x() + (placement.maxWidth() - lineWidth) / 2f;
            case RIGHT -> placement.x() + placement.maxWidth() - lineWidth;
        };
    }

    /** Draws the check mark for a checked checkbox at the placement coordinates. */
    private void drawCheckMark(
            PDDocument document, PDFont font, PdfTemplateProperties.FieldPlacement placement)
            throws IOException {
        drawValue(document, font, placement, CHECK_MARK, configuredFontSize(placement));
    }

    private static float configuredFontSize(PdfTemplateProperties.FieldPlacement placement) {
        return placement.fontSize() != null ? placement.fontSize() : DEFAULT_FONT_SIZE;
    }

    private void validateGlyphs(
            PDFont font, String templateName, String version, String fieldName, String value)
            throws IOException {
        if (!(font instanceof org.apache.pdfbox.pdmodel.font.PDType0Font type0Font)) return;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!type0Font.hasGlyph(codePoint)) {
                throw new UnsupportedGlyphException(templateName, version, fieldName, codePoint);
            }
            offset += Character.charCount(codePoint);
        }
    }

    /** The drawing primitive: renders the value at the placement coordinates and font size. */
    private void drawValue(
            PDDocument document,
            PDFont font,
            PdfTemplateProperties.FieldPlacement placement,
            String value,
            float fontSize)
            throws IOException {
        drawValue(document, font, placement, value, fontSize, placement.x(), placement.y());
    }

    private void drawValue(
            PDDocument document,
            PDFont font,
            PdfTemplateProperties.FieldPlacement placement,
            String value,
            float fontSize,
            float x,
            float y)
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
            content.newLineAtOffset(x, y);
            content.showText(value);
            content.endText();
        }
    }
}
