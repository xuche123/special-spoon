package com.xuche.pdfboxservice.pdf;

import java.util.Map;
import java.util.Set;

/**
 * A template after startup validation: the PDF is known to load, and the supported field names and
 * their coordinate placements are known.
 */
record ResolvedTemplate(
        String name,
        String version,
        byte[] pdfBytes,
        Set<String> knownFields,
        Map<String, PdfTemplateProperties.FieldPlacement> placements) {}
