package com.xuche.pdfboxservice.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.springframework.stereotype.Service;

/**
 * Fills templates from the validated {@link TemplateRegistry} and returns a non-editable PDF.
 *
 * <ul>
 *   <li>{@link ResolvedTemplate.Kind#ACROFORM} — text fields and checkboxes are filled, drawn
 *       e-signature images are stamped into their signature boxes, then all remaining fields except
 *       signature fields are flattened (the report stays digitally signable)
 *   <li>{@link ResolvedTemplate.Kind#OVERLAY} — values are drawn as page content at the configured
 *       coordinates; checkbox placements draw an X when the value is boolean-ish true; signature
 *       placements stamp an e-signature image fitted inside their box
 * </ul>
 *
 * Field and signature names are validated against the template; unknown names are rejected with
 * {@link UnknownTemplateFieldException}. Checkbox values must be boolean-ish (true/false, yes/no,
 * on/off, 1/0) and signature images must be decodable PNG or JPEG within size limits; violations
 * are rejected with {@link InvalidFieldValueException} or {@link InvalidSignatureException}.
 */
@Service
class PdfReportService {

    private static final float DEFAULT_FONT_SIZE = 12f;
    private static final int MAX_SIGNATURE_BASE64_LENGTH = 2_000_000;
    private static final int MAX_SIGNATURE_DIMENSION_PX = 4000;

    private final TemplateRegistry templateRegistry;

    PdfReportService(TemplateRegistry templateRegistry) {
        this.templateRegistry = templateRegistry;
    }

    byte[] fill(String templateName, Map<String, String> fields, Map<String, String> signatures) {
        ResolvedTemplate template = templateRegistry.get(templateName);
        if (template == null) {
            throw new TemplateNotFoundException(templateName);
        }
        rejectUnknown(templateName, fields.keySet(), template.knownFields());
        rejectUnknown(templateName, signatures.keySet(), template.signatureFields());

        try (PDDocument document = Loader.loadPDF(template.pdfBytes())) {
            switch (template.kind()) {
                case ACROFORM -> fillAcroForm(templateName, document, fields, signatures);
                case OVERLAY -> overlayFields(templateName, document, template, fields, signatures);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fill PDF template: " + templateName, e);
        }
    }

    private static void rejectUnknown(
            String templateName, Set<String> requested, Set<String> supported) {
        Set<String> unknown = new TreeSet<>(requested);
        unknown.removeAll(supported);
        if (!unknown.isEmpty()) {
            throw new UnknownTemplateFieldException(templateName, unknown, supported);
        }
    }

    private void fillAcroForm(
            String templateName,
            PDDocument document,
            Map<String, String> values,
            Map<String, String> signatures)
            throws IOException {
        PDAcroForm form = document.getDocumentCatalog().getAcroForm();
        Map<String, PDField> fieldsByName = new HashMap<>();
        for (PDField field : form.getFieldTree()) {
            fieldsByName.put(field.getFullyQualifiedName(), field);
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {
            switch (fieldsByName.get(entry.getKey())) {
                case PDTextField textField -> textField.setValue(entry.getValue());
                case PDCheckBox checkBox -> {
                    if (parseCheckboxValue(templateName, entry.getKey(), entry.getValue())) {
                        checkBox.check();
                    } else {
                        checkBox.unCheck();
                    }
                }
                case null, default -> {} // registry validation prevents this
            }
        }

        // Stamp drawn e-signatures into their signature boxes and drop the now-redundant
        // signature fields, so viewers don't show an empty digital-signature placeholder.
        for (Map.Entry<String, String> entry : signatures.entrySet()) {
            if (fieldsByName.get(entry.getKey()) instanceof PDSignatureField signatureField) {
                PDImageXObject image =
                        decodeSignatureImage(
                                document, templateName, entry.getKey(), entry.getValue());
                PDAnnotationWidget widget = signatureField.getWidgets().get(0);
                PDPage page = widget.getPage();
                drawImageFit(document, page, widget.getRectangle(), image);
                removeField(form, page, signatureField, widget);
            }
        }

        // Flatten everything except signature fields so the report remains digitally signable.
        List<PDField> flattenable = new ArrayList<>();
        for (PDField field : form.getFields()) {
            if (!(field instanceof PDSignatureField)) {
                flattenable.add(field);
            }
        }
        form.flatten(flattenable, true);
    }

    private void overlayFields(
            String templateName,
            PDDocument document,
            ResolvedTemplate template,
            Map<String, String> values,
            Map<String, String> signatures)
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
                case SIGNATURE -> {} // handled below, registry validation prevents this case here
            }
        }
        for (Map.Entry<String, String> entry : signatures.entrySet()) {
            PdfTemplateProperties.FieldPlacement placement =
                    template.placements().get(entry.getKey());
            PDImageXObject image =
                    decodeSignatureImage(document, templateName, entry.getKey(), entry.getValue());
            PDRectangle box =
                    new PDRectangle(
                            placement.x(), placement.y(), placement.width(), placement.height());
            drawImageFit(document, document.getPage(placement.page() - 1), box, image);
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

    /**
     * Removes a field and its widget at the COS level. Wrapper objects returned by PDFBox traversal
     * APIs are not identity-stable, and the arrays hold indirect references, so comparison must
     * resolve and compare the underlying COS dictionaries.
     */
    private static void removeField(
            PDAcroForm form, PDPage page, PDField field, PDAnnotationWidget widget) {
        removeCosReference(
                (COSArray) form.getCOSObject().getDictionaryObject(COSName.FIELDS),
                field.getCOSObject());
        removeCosReference(
                (COSArray) page.getCOSObject().getDictionaryObject(COSName.ANNOTS),
                widget.getCOSObject());
    }

    private static void removeCosReference(COSArray array, COSBase target) {
        if (array == null) {
            return;
        }
        for (int i = array.size() - 1; i >= 0; i--) {
            COSBase item = array.get(i);
            COSBase resolved = item instanceof COSObject reference ? reference.getObject() : item;
            if (resolved == target) {
                array.remove(i);
                return;
            }
        }
    }

    private PDImageXObject decodeSignatureImage(
            PDDocument document, String templateName, String fieldName, String encoded) {
        String base64 = encoded.strip();
        int comma = base64.indexOf(',');
        if (base64.regionMatches(true, 0, "data:", 0, 5) && comma > 0) {
            base64 = base64.substring(comma + 1);
        }
        if (base64.length() > MAX_SIGNATURE_BASE64_LENGTH) {
            throw new InvalidSignatureException(
                    templateName, fieldName, "image exceeds the 2MB base64 limit");
        }

        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new InvalidSignatureException(templateName, fieldName, "not valid base64");
        }

        try {
            if (isJpeg(bytes)) {
                return JPEGFactory.createFromByteArray(document, bytes);
            }
            if (isPng(bytes)) {
                var image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) {
                    throw new InvalidSignatureException(
                            templateName, fieldName, "could not be decoded as an image");
                }
                if (image.getWidth() > MAX_SIGNATURE_DIMENSION_PX
                        || image.getHeight() > MAX_SIGNATURE_DIMENSION_PX) {
                    throw new InvalidSignatureException(
                            templateName,
                            fieldName,
                            "image dimensions exceed " + MAX_SIGNATURE_DIMENSION_PX + "px");
                }
                return LosslessFactory.createFromImage(document, image);
            }
            throw new InvalidSignatureException(
                    templateName, fieldName, "only PNG or JPEG images are supported");
        } catch (IOException e) {
            throw new InvalidSignatureException(
                    templateName, fieldName, "could not be decoded as an image");
        }
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length > 4
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G';
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
    }

    /** Draws the image centered inside the box, scaled to fit while preserving aspect ratio. */
    private void drawImageFit(
            PDDocument document, PDPage page, PDRectangle box, PDImageXObject image)
            throws IOException {
        float scale =
                Math.min(box.getWidth() / image.getWidth(), box.getHeight() / image.getHeight());
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        float x = box.getLowerLeftX() + (box.getWidth() - width) / 2;
        float y = box.getLowerLeftY() + (box.getHeight() - height) / 2;
        try (PDPageContentStream content =
                new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            content.drawImage(image, x, y, width, height);
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
