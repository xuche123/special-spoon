package com.xuche.pdfboxservice.pdf;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * Supported report templates, bound from {@code templates.yml} (imported by application.yml).
 * Adding a template means adding an entry here plus the PDF under {@code classpath:templates/}; the
 * {@link TemplateRegistry} validates each entry at startup.
 */
@Validated
@ConfigurationProperties(prefix = "pdf")
public record PdfTemplateProperties(@NotEmpty Map<String, @Valid Template> templates) {

    public PdfTemplateProperties {
        if (templates == null) {
            templates = Map.of();
        }
    }

    /**
     * @param file location of the template PDF, e.g. {@code classpath:templates/report.pdf}
     * @param fields coordinate placements for the template's fields
     */
    public static final class Template {
        private String currentVersion;
        @Valid private Map<String, Version> versions = Map.of();

        public Template() {}

        /** Supports both the versioned shape and the original single-version test shape. */
        public Template(String file, Map<String, FieldPlacement> fields) {
            this.currentVersion = "v1";
            this.versions = Map.of("v1", new Version(file, fields));
        }

        public Template(String file, String font, Map<String, FieldPlacement> fields) {
            this.currentVersion = "v1";
            this.versions = Map.of("v1", new Version(file, font, fields));
        }

        public void setCurrentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        public void setVersions(Map<String, Version> versions) {
            this.versions = versions == null ? Map.of() : versions;
        }

        public String currentVersion() {
            return currentVersion;
        }

        public Map<String, Version> versions() {
            return versions;
        }
    }

    /** An immutable, named revision of a template definition. */
    public record Version(
            @NotBlank String file, String font, @Valid Map<String, FieldPlacement> fields) {

        public Version(String file, Map<String, FieldPlacement> fields) {
            this(file, null, fields);
        }

        @ConstructorBinding
        public Version {
            if (fields == null) {
                fields = Map.of();
            }
        }
    }

    /**
     * Coordinates are PDF points (1/72 inch) with the origin at the bottom-left of the page.
     *
     * @param page 1-based page number
     * @param x left edge of the value's baseline, in points
     * @param y baseline of the value, in points
     * @param fontSize default 12; shrunk proportionally if {@code maxWidth} is exceeded
     * @param maxWidth optional maximum rendered width in points
     * @param maxHeight optional maximum number of vertical points for a multiline text field
     * @param lineHeight vertical distance between multiline baselines; defaults to 1.2 times font
     *     size
     * @param alignment horizontal alignment for multiline text fields
     * @param overflow overflow policy for multiline text fields
     * @param type how to render the value; default {@link FieldType#TEXT}
     */
    public record FieldPlacement(
            @NotNull @Positive Integer page,
            @NotNull Float x,
            @NotNull Float y,
            @Positive Float fontSize,
            @Positive Float maxWidth,
            @Positive Float maxHeight,
            @Positive Float lineHeight,
            TextAlignment alignment,
            TextOverflow overflow,
            FieldType type) {

        @ConstructorBinding
        public FieldPlacement {
            if (type == null) {
                type = FieldType.TEXT;
            }
            if (alignment == null) {
                alignment = TextAlignment.LEFT;
            }
            if (overflow == null) {
                overflow = TextOverflow.REJECT;
            }
            if (maxHeight != null && lineHeight == null) {
                float configuredFontSize = fontSize != null ? fontSize : 12f;
                lineHeight = configuredFontSize * 1.2f;
            }
        }

        public FieldPlacement(
                Integer page, Float x, Float y, Float fontSize, Float maxWidth, FieldType type) {
            this(page, x, y, fontSize, maxWidth, null, null, null, null, type);
        }
    }

    /** How an overlay field renders its value. */
    public enum FieldType {
        /** Draw the value as text, shrunk to {@code maxWidth} when configured. */
        TEXT,
        /**
         * Draw an X at the coordinates when the value is {@code "true"}; nothing when {@code
         * "false"}.
         */
        CHECKBOX
    }

    /** Horizontal alignment for a multiline text field. */
    public enum TextAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    /** Overflow behavior for a multiline text field. */
    public enum TextOverflow {
        REJECT
    }
}
