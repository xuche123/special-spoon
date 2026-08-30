package com.xuche.pdfboxservice.pdf.template;

import java.io.IOException;

/** Storage seam for immutable template PDF bytes. */
interface TemplateStorage {
    byte[] read(String location) throws IOException;
}
