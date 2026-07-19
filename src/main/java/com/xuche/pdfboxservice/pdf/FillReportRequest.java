package com.xuche.pdfboxservice.pdf;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** Request body for filling a report template: AcroForm field name -> value. */
public record FillReportRequest(@NotNull Map<String, String> fields) {}
