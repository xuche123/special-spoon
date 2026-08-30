package com.xuche.pdfboxservice.pdf.template;

import java.util.Map;
import java.util.Set;

/**
 * A template after startup validation: the PDF is known to load, and the supported field names and
 * their coordinate placements are known.
 */
public record ResolvedTemplate(
        String name,
        String version,
        byte[] pdfBytes,
        byte[] fontBytes,
        String font,
        Set<String> knownFields,
        Map<String, PdfTemplateProperties.FieldPlacement> placements) {

    public ResolvedTemplate {
        pdfBytes = pdfBytes.clone();
        fontBytes = fontBytes == null ? null : fontBytes.clone();
    }

    @Override
    public byte[] pdfBytes() {
        return pdfBytes.clone();
    }

    @Override
    public byte[] fontBytes() {
        return fontBytes == null ? null : fontBytes.clone();
    }
}
