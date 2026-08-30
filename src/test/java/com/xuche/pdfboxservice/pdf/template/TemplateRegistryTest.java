package com.xuche.pdfboxservice.pdf.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties.FieldPlacement;
import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties.Template;
import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties.TextAlignment;
import com.xuche.pdfboxservice.pdf.template.PdfTemplateProperties.TextOverflow;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class TemplateRegistryTest {

    private static final String REPORT = "classpath:templates/report.pdf";
    private static final String CERTIFICATE = "classpath:templates/certificate.pdf";
    private static final String BARE = "classpath:templates/bare.pdf";
    private static final String ACROFORM = "classpath:templates/acroform.pdf";
    private static final String FONT = "classpath:templates/test-font.ttf";

    private static Template template(String file, Map<String, FieldPlacement> fields) {
        return new Template(file, fields);
    }

    private static Template versionedTemplate(String currentVersion) {
        Template template = new Template();
        template.setCurrentVersion(currentVersion);
        template.setVersions(
                Map.of(
                        "v1",
                                new PdfTemplateProperties.Version(
                                        REPORT, Map.of("title", placement(1, 150f, 665f))),
                        "v2",
                                new PdfTemplateProperties.Version(
                                        REPORT, Map.of("title", placement(1, 160f, 665f)))));
        return template;
    }

    private static FieldPlacement placement(int page, Float x, Float y) {
        return new FieldPlacement(page, x, y, null, null, null);
    }

    private static TemplateRegistry registryOf(Map<String, Template> templates) {
        return new TemplateRegistry(
                new PdfTemplateProperties(templates), new DefaultResourceLoader());
    }

    @Test
    void resolvesTemplatesAndTheirConfiguredFields() {
        TemplateRegistry registry =
                registryOf(
                        Map.of(
                                "report",
                                template(REPORT, Map.of("title", placement(1, 150f, 665f)))));

        ResolvedTemplate report = registry.get("report");
        assertThat(report.knownFields()).containsExactly("title");
        assertThat(report.placements()).containsOnlyKeys("title");
    }

    @Test
    void resolvesTheConfiguredCurrentVersionWhenNoVersionIsRequested() {
        TemplateRegistry registry = registryOf(Map.of("report", versionedTemplate("v2")));

        ResolvedTemplate resolved = registry.get("report", null);
        assertThat(resolved.version()).isEqualTo("v2");
        assertThat(resolved.placements().get("title").x()).isEqualTo(160f);
    }

    @Test
    void resolvesAnAvailableVersionWhenItIsRequestedExplicitly() {
        TemplateRegistry registry = registryOf(Map.of("report", versionedTemplate("v2")));

        ResolvedTemplate resolved = registry.get("report", "v1");
        assertThat(resolved.version()).isEqualTo("v1");
        assertThat(resolved.placements().get("title").x()).isEqualTo(150f);
    }

    @Test
    void returnsNoResolutionForAnUnavailableVersion() {
        TemplateRegistry registry = registryOf(Map.of("report", versionedTemplate("v2")));

        assertThat(registry.get("report", "v3")).isNull();
    }

    @Test
    void resolvesAndEmbedsConfiguredTtf() {
        TemplateRegistry registry =
                registryOf(
                        Map.of(
                                "report",
                                new Template(
                                        REPORT, FONT, Map.of("title", placement(1, 150f, 665f)))));
        assertThat(registry.get("report").fontBytes()).isNotEmpty();
    }

    @Test
    void rejectsMissingConfiguredFont() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "report",
                                                new Template(
                                                        REPORT,
                                                        "classpath:templates/nope.ttf",
                                                        Map.of("title", placement(1, 1f, 1f))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.templates.report")
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsNonTtfConfiguredFont() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "report",
                                                new Template(
                                                        REPORT,
                                                        "classpath:templates/report.otf",
                                                        Map.of("title", placement(1, 1f, 1f))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classpath TTF");
    }

    @Test
    void rejectsMalformedConfiguredFontAtStartup() {
        assertThatThrownBy(
                        () ->
                                new TemplateRegistry(
                                        new PdfTemplateProperties(
                                                Map.of(
                                                        "report",
                                                        new Template(
                                                                REPORT,
                                                                "classpath:templates/bad.ttf",
                                                                Map.of(
                                                                        "title",
                                                                        placement(1, 1f, 1f))))),
                                        location -> "not a font".getBytes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid TTF resource");
    }

    @Test
    void failsWhenTemplateHasAnAcroForm() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "form",
                                                template(
                                                        ACROFORM,
                                                        Map.of("field1", placement(1, 1f, 1f))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.templates.form")
                .hasMessageContaining("AcroForm");
    }

    @Test
    void failsWhenTemplateFileIsMissing() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "report",
                                                template(
                                                        "classpath:templates/nope.pdf",
                                                        Map.of("title", placement(1, 1f, 1f))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.templates.report")
                .hasMessageContaining("not found");
    }

    @Test
    void failsWhenTemplateDeclaresNoFields() {
        assertThatThrownBy(() -> registryOf(Map.of("bare", template(BARE, Map.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.templates.bare")
                .hasMessageContaining("placements are required");
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
    void failsWhenMultilinePlacementHasHeightWithoutWidth() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "report",
                                                template(
                                                        REPORT,
                                                        Map.of(
                                                                "summary",
                                                                new FieldPlacement(
                                                                        1,
                                                                        150f,
                                                                        525f,
                                                                        12f,
                                                                        null,
                                                                        80f,
                                                                        null,
                                                                        TextAlignment.LEFT,
                                                                        TextOverflow.REJECT,
                                                                        null))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maxHeight requires maxWidth");
    }

    @Test
    void failsWhenLineHeightIsConfiguredWithoutMultilineBounds() {
        assertThatThrownBy(
                        () ->
                                registryOf(
                                        Map.of(
                                                "report",
                                                template(
                                                        REPORT,
                                                        Map.of(
                                                                "summary",
                                                                new FieldPlacement(
                                                                        1,
                                                                        150f,
                                                                        525f,
                                                                        12f,
                                                                        400f,
                                                                        null,
                                                                        16f,
                                                                        TextAlignment.LEFT,
                                                                        TextOverflow.REJECT,
                                                                        null))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lineHeight requires maxWidth and maxHeight");
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
