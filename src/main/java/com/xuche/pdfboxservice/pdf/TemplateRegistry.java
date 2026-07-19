package com.xuche.pdfboxservice.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Validated registry of the supported report templates, built once at startup from {@link
 * PdfTemplateProperties}.
 *
 * <p>Startup fails fast (listing every problem) when a template is misconfigured:
 *
 * <ul>
 *   <li>the PDF file must exist and load
 *   <li>a PDF with an AcroForm is filled by field name; declaring coordinate fields is an error
 *   <li>a form-free PDF must declare coordinate placements, and every placement must be complete
 *       ({@code page}, {@code x}, {@code y}) and within the document's page count
 * </ul>
 */
@Component
class TemplateRegistry {

    /** Template names appear in URLs, so keep them strict: lowercase alphanumerics and hyphens. */
    private static final Pattern TEMPLATE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final Map<String, ResolvedTemplate> templates;

    TemplateRegistry(PdfTemplateProperties properties, ResourceLoader resourceLoader) {
        Map<String, ResolvedTemplate> resolved = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, PdfTemplateProperties.Template> entry :
                properties.templates().entrySet()) {
            try {
                if (!TEMPLATE_NAME.matcher(entry.getKey()).matches()) {
                    throw new IllegalStateException(
                            "template name must match " + TEMPLATE_NAME.pattern());
                }
                resolved.put(
                        entry.getKey(), resolve(entry.getKey(), entry.getValue(), resourceLoader));
            } catch (IllegalStateException | UncheckedIOException e) {
                errors.add("pdf.templates." + entry.getKey() + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid template configuration:\n - " + String.join("\n - ", errors));
        }
        this.templates = Map.copyOf(resolved);
    }

    /** Returns the template, or {@code null} if the name is not in the supported list. */
    ResolvedTemplate get(String name) {
        return templates.get(name);
    }

    private ResolvedTemplate resolve(
            String name, PdfTemplateProperties.Template template, ResourceLoader resourceLoader) {
        Resource pdf = resourceLoader.getResource(template.file());
        if (!pdf.exists()) {
            throw new IllegalStateException("PDF file not found: " + template.file());
        }

        byte[] pdfBytes;
        try {
            pdfBytes = pdf.getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + template.file(), e);
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form != null && !form.getFields().isEmpty()) {
                if (!template.fields().isEmpty()) {
                    throw new IllegalStateException(
                            "declares coordinate fields but the PDF has an AcroForm; remove the"
                                    + " fields, AcroForm templates are filled by field name");
                }
                Set<String> knownFields = new TreeSet<>();
                for (PDField field : form.getFieldTree()) {
                    if (field instanceof PDTextField) {
                        knownFields.add(field.getFullyQualifiedName());
                    }
                }
                return new ResolvedTemplate(
                        name, ResolvedTemplate.Kind.ACROFORM, pdfBytes, knownFields, Map.of());
            }

            if (template.fields().isEmpty()) {
                throw new IllegalStateException(
                        "PDF has no AcroForm; coordinate placements are required under"
                                + " pdf.templates."
                                + name
                                + ".fields");
            }
            Map<String, PdfTemplateProperties.FieldPlacement> placements = new LinkedHashMap<>();
            for (Map.Entry<String, PdfTemplateProperties.FieldPlacement> field :
                    template.fields().entrySet()) {
                placements.put(
                        field.getKey(),
                        validatePlacement(
                                field.getKey(), field.getValue(), document.getNumberOfPages()));
            }
            return new ResolvedTemplate(
                    name,
                    ResolvedTemplate.Kind.OVERLAY,
                    pdfBytes,
                    Set.copyOf(placements.keySet()),
                    Map.copyOf(placements));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse " + template.file(), e);
        }
    }

    private PdfTemplateProperties.FieldPlacement validatePlacement(
            String fieldName, PdfTemplateProperties.FieldPlacement placement, int pageCount) {
        if (fieldName.isBlank()) {
            throw new IllegalStateException("field name must not be blank");
        }
        Integer page = placement.page();
        if (page == null || page < 1 || page > pageCount) {
            throw new IllegalStateException(
                    "field '"
                            + fieldName
                            + "': page must be between 1 and "
                            + pageCount
                            + " (the document's page count), got "
                            + page);
        }
        if (placement.x() == null || placement.y() == null) {
            throw new IllegalStateException("field '" + fieldName + "': x and y are required");
        }
        if (placement.fontSize() != null && placement.fontSize() <= 0) {
            throw new IllegalStateException("field '" + fieldName + "': fontSize must be positive");
        }
        if (placement.maxWidth() != null && placement.maxWidth() <= 0) {
            throw new IllegalStateException("field '" + fieldName + "': maxWidth must be positive");
        }
        return placement;
    }
}
