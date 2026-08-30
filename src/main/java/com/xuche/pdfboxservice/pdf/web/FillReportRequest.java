package com.xuche.pdfboxservice.pdf.web;

import com.xuche.pdfboxservice.pdf.InvalidFieldValueException;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request body for filling a report template: field name -> value. Values are typed: text fields
 * take JSON strings, checkbox fields take JSON booleans; anything else is rejected with {@link
 * InvalidFieldValueException}.
 */
public record FillReportRequest(@NotNull Map<String, Object> fields) {}
