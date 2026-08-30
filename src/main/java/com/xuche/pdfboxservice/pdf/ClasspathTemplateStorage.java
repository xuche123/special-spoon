package com.xuche.pdfboxservice.pdf;

import java.io.IOException;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Initial classpath-backed template storage implementation. */
@Component
final class ClasspathTemplateStorage implements TemplateStorage {
    private final ResourceLoader resourceLoader;

    ClasspathTemplateStorage(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public byte[] read(String location) throws IOException {
        var resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException("PDF file not found: " + location);
        }
        return resource.getContentAsByteArray();
    }
}
