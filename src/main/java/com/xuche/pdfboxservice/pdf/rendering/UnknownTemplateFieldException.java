package com.xuche.pdfboxservice.pdf.rendering;

import com.xuche.pdfboxservice.pdf.ReportException;
import java.util.Set;
import java.util.TreeSet;

/** Thrown when the request contains field names that the template does not support. */
public class UnknownTemplateFieldException extends ReportException {

    public UnknownTemplateFieldException(
            String templateName, Set<String> unknownFields, Set<String> supportedFields) {
        this(templateName, null, unknownFields, supportedFields);
    }

    public UnknownTemplateFieldException(
            String templateName,
            String version,
            Set<String> unknownFields,
            Set<String> supportedFields) {
        super(
                "Unknown field(s) "
                        + new TreeSet<>(unknownFields)
                        + " for template '"
                        + templateName
                        + "'. Supported fields: "
                        + new TreeSet<>(supportedFields),
                templateName,
                version,
                String.join(",", new TreeSet<>(unknownFields)));
    }
}
