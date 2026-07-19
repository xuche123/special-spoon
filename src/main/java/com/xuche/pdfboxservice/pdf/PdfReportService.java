package com.xuche.pdfboxservice.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.springframework.stereotype.Service;

/**
 * Fills templates from the validated {@link TemplateRegistry} and returns a non-editable PDF.
 *
 * <ul>
 *   <li>{@link ResolvedTemplate.Kind#ACROFORM} — text fields are filled and the form is flattened
 *   <li>{@link ResolvedTemplate.Kind#OVERLAY} — values are drawn as page content at the configured
 *       coordinates
 * </ul>
 *
 * Requested field names are validated against the template's supported fields; unknown names are
 * rejected with {@link UnknownTemplateFieldException}.
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
            switch (template.kind()) {
                case ACROFORM -> fillAcroForm(document.getDocumentCatalog().getAcroForm(), fields);
                case OVERLAY -> overlayFields(document, template, fields);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fill PDF template: " + templateName, e);
        }
    }

    private void fillAcroForm(PDAcroForm form, Map<String, String> values) throws IOException {
        Map<String, PDField> fieldsByName = new HashMap<>();
        for (PDField field : form.getFieldTree()) {
            fieldsByName.put(field.getFullyQualifiedName(), field);
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (fieldsByName.get(entry.getKey()) instanceof PDTextField textField) {
                textField.setValue(entry.getValue());
            }
        }
        form.flatten();
    }

    private void overlayFields(
            PDDocument document, ResolvedTemplate template, Map<String, String> values)
            throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            drawValue(document, font, template.placements().get(entry.getKey()), entry.getValue());
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
}
