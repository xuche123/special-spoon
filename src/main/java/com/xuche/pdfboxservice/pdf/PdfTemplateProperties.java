package com.xuche.pdfboxservice.pdf;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
    public record Template(@NotBlank String file, @Valid Map<String, FieldPlacement> fields) {

        public Template {
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
     * @param type how to render the value; default {@link FieldType#TEXT}
     */
    public record FieldPlacement(
            @NotNull @Positive Integer page,
            @NotNull Float x,
            @NotNull Float y,
            @Positive Float fontSize,
            @Positive Float maxWidth,
            FieldType type) {

        public FieldPlacement {
            if (type == null) {
                type = FieldType.TEXT;
            }
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
}
