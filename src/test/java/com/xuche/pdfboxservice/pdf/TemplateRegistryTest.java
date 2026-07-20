package com.xuche.pdfboxservice.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xuche.pdfboxservice.pdf.PdfTemplateProperties.FieldPlacement;
import com.xuche.pdfboxservice.pdf.PdfTemplateProperties.Template;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class TemplateRegistryTest {

    private static final String REPORT = "classpath:templates/report.pdf";
    private static final String CERTIFICATE = "classpath:templates/certificate.pdf";
    private static final String BARE = "classpath:templates/bare.pdf";

    private static Template template(String file, Map<String, FieldPlacement> fields) {
        return new Template(file, fields);
    }

    private static FieldPlacement placement(int page, Float x, Float y) {
        return new FieldPlacement(page, x, y, null, null, null, null, null);
    }

    private static FieldPlacement signaturePlacement(
            int page, float x, float y, Float width, Float height) {
        return new FieldPlacement(
                page, x, y, null, null, PdfTemplateProperties.FieldType.SIGNATURE, width, height);
    }

    private static TemplateRegistry registryOf(Map<String, Template> templates) {
        return new TemplateRegistry(
                new PdfTemplateProperties(templates), new DefaultResourceLoader());
    }

    @Test
    void detectsAcroFormTemplatesAndTheirFillableFields() {
        TemplateRegistry registry = registryOf(Map.of("report", template(REPORT, Map.of())));

        ResolvedTemplate report = registry.get("report");
        assertThat(report.kind()).isEqualTo(ResolvedTemplate.Kind.ACROFORM);
        // Text fields and checkboxes are fillable via fields; the signature field takes a
        // drawn e-signature via signatures.
        assertThat(report.knownFields())
                .containsExactlyInAnyOrder(
                        "title",
                        "author",
                        "date",
                        "summary",
                        "confidential",
                        "reviewed",
                        "approved",
                        "signed-by",
                        "signature-date");
        assertThat(report.signatureFields()).containsExactly("signature");
    }

    @Test
    void detectsOverlaySignaturePlacements() {
        TemplateRegistry registry =
                registryOf(
                        Map.of(
                                "certificate",
                                template(
                                        CERTIFICATE,
                                        Map.of(
                                                "instructor-signature",
                                                signaturePlacement(3, 500f, 340f, 180f, 60f)))));

        ResolvedTemplate certificate = registry.get("certificate");
        assertThat(certificate.signatureFields()).containsExactly("instructor-signature");
        assertThat(certificate.knownFields()).isEmpty();
    }

    @Test
    void failsWhenSignaturePlacementIsMissingSize() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "certificate",
                                                template(
                                                        CERTIFICATE,
                                                        Map.of(
                                                                "instructor-signature",
                                                                signaturePlacement(
                                                                        3, 500f, 340f, null,
                                                                        60f))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("instructor-signature")
                .hasMessageContaining("width and height");
    }

    @Test
    void failsWhenNonSignaturePlacementDeclaresWidth() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "certificate",
                                                template(
                                                        CERTIFICATE,
                                                        Map.of(
                                                                "recipient",
                                                                new FieldPlacement(
                                                                        1, 180f, 400f, null, null,
                                                                        null, 100f, null))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recipient")
                .hasMessageContaining("signature fields");
    }

    @Test
    void detectsOverlayTemplatesAndTheirConfiguredFields() {
        TemplateRegistry registry =
                registryOf(
                        Map.of(
                                "certificate",
                                template(
                                        CERTIFICATE,
                                        Map.of("recipient", placement(1, 180f, 400f)))));

        ResolvedTemplate certificate = registry.get("certificate");
        assertThat(certificate.kind()).isEqualTo(ResolvedTemplate.Kind.OVERLAY);
        assertThat(certificate.knownFields()).containsExactly("recipient");
        assertThat(certificate.placements()).containsOnlyKeys("recipient");
    }

    @Test
    void failsWhenTemplateFileIsMissing() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "report",
                                                template(
                                                        "classpath:templates/nope.pdf", Map.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.templates.report")
                .hasMessageContaining("not found");
    }

    @Test
    void failsWhenFormFreeTemplateDeclaresNoFields() {
        assertThatThrownBy(() -> registryOf(Map.of("bare", template(BARE, Map.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.templates.bare")
                .hasMessageContaining("no AcroForm");
    }

    @Test
    void failsWhenAcroFormTemplateDeclaresCoordinateFields() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "report",
                                                template(
                                                        REPORT,
                                                        Map.of("title", placement(1, 1f, 1f))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.templates.report")
                .hasMessageContaining("AcroForm");
    }

    @Test
    void failsWhenPlacementPageIsOutOfRange() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "certificate",
                                                template(
                                                        CERTIFICATE,
                                                        Map.of(
                                                                "recipient",
                                                                placement(9, 180f, 400f))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recipient")
                .hasMessageContaining("page");
    }

    @Test
    void failsWhenPlacementIsMissingCoordinates() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "certificate",
                                                template(
                                                        CERTIFICATE,
                                                        Map.of(
                                                                "recipient",
                                                                placement(1, null, 400f))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recipient")
                .hasMessageContaining("x and y are required");
    }

    @Test
    void failsWhenTemplateNameIsNotUrlSafe() {
        assertThatThrownBy(() -> registryOf(Map.of("Report_1", template(REPORT, Map.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.templates.Report_1")
                .hasMessageContaining("template name");
    }

    @Test
    void aggregatesErrorsAcrossTemplates() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "report",
                                                        template(
                                                                "classpath:templates/nope.pdf",
                                                                Map.of()),
                                                "bare", template(BARE, Map.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.templates.report")
                .hasMessageContaining("pdf.templates.bare");
    }
}
