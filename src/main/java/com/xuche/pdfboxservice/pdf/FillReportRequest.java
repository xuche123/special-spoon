package com.xuche.pdfboxservice.pdf;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request body for filling a report template.
 *
 * @param fields AcroForm field name or overlay placement name -> value
 * @param signatures signature box name -> drawn e-signature image as a base64 data URL ({@code
 *     data:image/png;base64,...}) or plain base64; PNG and JPEG are supported
 */
public record FillReportRequest(
        @NotNull Map<String, String> fields, Map<String, String> signatures) {

    public FillReportRequest {
        if (signatures == null) {
            signatures = Map.of();
        }
    }
}
