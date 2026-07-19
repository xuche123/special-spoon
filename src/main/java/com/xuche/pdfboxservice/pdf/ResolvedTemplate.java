package com.xuche.pdfboxservice.pdf;

import java.util.Map;
import java.util.Set;

/**
 * A template after startup validation: its fill strategy is decided and its supported field names
 * are known (from the AcroForm for {@link Kind#ACROFORM}, from the configured placements for {@link
 * Kind#OVERLAY}).
 */
record ResolvedTemplate(
        String name,
        Kind kind,
        byte[] pdfBytes,
        Set<String> knownFields,
        Map<String, PdfTemplateProperties.FieldPlacement> placements) {

    enum Kind {
        /** Filled by AcroForm field name, then flattened. */
        ACROFORM,
        /** Filled by drawing values as page content at configured coordinates. */
        OVERLAY
    }
}
