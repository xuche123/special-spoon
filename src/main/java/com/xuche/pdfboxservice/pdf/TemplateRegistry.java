package com.xuche.pdfboxservice.pdf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Startup-resolved registry of immutable template versions. */
@Component
class TemplateRegistry {
    private static final Pattern TEMPLATE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final Map<String, Map<String, ResolvedTemplate>> templates;
    private final Map<String, String> currentVersions;

    @Autowired
    TemplateRegistry(PdfTemplateProperties properties, TemplateStorage storage) {
        ResolvedTemplates resolved = resolveConfiguredTemplates(properties.templates(), storage);
        templates = resolved.templates();
        currentVersions = resolved.currentVersions();
    }

    TemplateRegistry(PdfTemplateProperties properties, ResourceLoader resourceLoader) {
        this(properties, new ClasspathTemplateStorage(resourceLoader));
    }

    ResolvedTemplate get(String name, String requestedVersion) {
        Map<String, ResolvedTemplate> versions = templates.get(name);
        if (versions == null) {
            return null;
        }
        String version = requestedVersion == null ? currentVersions.get(name) : requestedVersion;
        return versions.get(version);
    }

    ResolvedTemplate get(String name) {
        return get(name, null);
    }

    private ResolvedTemplates resolveConfiguredTemplates(
            Map<String, PdfTemplateProperties.Template> configured, TemplateStorage storage) {
        Map<String, Map<String, ResolvedTemplate>> resolved = new LinkedHashMap<>();
        Map<String, String> currents = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, PdfTemplateProperties.Template> entry : configured.entrySet()) {
            try {
                String name = entry.getKey();
                PdfTemplateProperties.Template template = entry.getValue();
                validateTemplateName(name);
                validateVersions(template);
                Map<String, ResolvedTemplate> versions = new LinkedHashMap<>();
                for (Map.Entry<String, PdfTemplateProperties.Version> version :
                        template.versions().entrySet()) {
                    versions.put(
                            version.getKey(),
                            resolveTemplate(name, version.getKey(), version.getValue(), storage));
                }
                resolved.put(name, Map.copyOf(versions));
                currents.put(name, template.currentVersion());
            } catch (IllegalStateException | UncheckedIOException e) {
                errors.add("pdf.templates." + entry.getKey() + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid template configuration:\n - " + String.join("\n - ", errors));
        }
        return new ResolvedTemplates(Map.copyOf(resolved), Map.copyOf(currents));
    }

    private void validateVersions(PdfTemplateProperties.Template template) {
        if (template == null
                || template.currentVersion() == null
                || template.currentVersion().isBlank()) {
            throw new IllegalStateException("currentVersion is required");
        }
        if (template.versions() == null || template.versions().isEmpty()) {
            throw new IllegalStateException("at least one immutable version is required");
        }
        if (!template.versions().containsKey(template.currentVersion())) {
            throw new IllegalStateException(
                    "currentVersion '" + template.currentVersion() + "' is not configured");
        }
        for (String version : template.versions().keySet()) {
            if (version == null || version.isBlank() || version.contains("/")) {
                throw new IllegalStateException("version identifiers must be non-blank and opaque");
            }
        }
    }

    private ResolvedTemplate resolveTemplate(
            String name,
            String version,
            PdfTemplateProperties.Version template,
            TemplateStorage storage) {
        byte[] pdfBytes;
        try {
            pdfBytes = storage.read(template.file());
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        byte[] fontBytes = resolveFont(version, template.font(), storage);
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form != null && !form.getFields().isEmpty()) {
                throw new IllegalStateException(
                        "PDF has an AcroForm; only form-free (overlay) templates are supported");
            }
            if (template.fields().isEmpty()) {
                throw new IllegalStateException("coordinate placements are required under fields");
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
                    version,
                    pdfBytes,
                    fontBytes,
                    template.font() == null || template.font().isBlank()
                            ? "Helvetica"
                            : template.font(),
                    Set.copyOf(placements.keySet()),
                    Map.copyOf(placements));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse " + template.file(), e);
        }
    }

    private byte[] resolveFont(String version, String location, TemplateStorage storage) {
        if (location == null || location.isBlank()) return null;
        if (!location.startsWith("classpath:") || !location.toLowerCase().endsWith(".ttf")) {
            throw new IllegalStateException(
                    "version '" + version + "' font must be a classpath TTF resource");
        }
        byte[] bytes;
        try {
            bytes = storage.read(location);
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        try (PDDocument document = new PDDocument()) {
            PDType0Font.load(document, new ByteArrayInputStream(bytes));
            return bytes;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "version '" + version + "' font is not a valid TTF resource", e);
        }
    }

    private void validateTemplateName(String name) {
        if (!TEMPLATE_NAME.matcher(name).matches()) {
            throw new IllegalStateException("template name must match " + TEMPLATE_NAME.pattern());
        }
    }

    private PdfTemplateProperties.FieldPlacement validatePlacement(
            String fieldName, PdfTemplateProperties.FieldPlacement placement, int pageCount) {
        if (fieldName.isBlank()) throw new IllegalStateException("field name must not be blank");
        Integer page = placement.page();
        if (page == null || page < 1 || page > pageCount) {
            throw new IllegalStateException(
                    "field '"
                            + fieldName
                            + "': page must be between 1 and "
                            + pageCount
                            + ", got "
                            + page);
        }
        if (placement.x() == null || placement.y() == null) {
            throw new IllegalStateException("field '" + fieldName + "': x and y are required");
        }
        if (placement.maxHeight() != null && placement.maxWidth() == null) {
            throw new IllegalStateException(
                    "field '" + fieldName + "': maxHeight requires maxWidth");
        }
        if (placement.lineHeight() != null
                && (placement.maxWidth() == null || placement.maxHeight() == null)) {
            throw new IllegalStateException(
                    "field '" + fieldName + "': lineHeight requires maxWidth and maxHeight");
        }
        return placement;
    }

    private record ResolvedTemplates(
            Map<String, Map<String, ResolvedTemplate>> templates,
            Map<String, String> currentVersions) {}
}
