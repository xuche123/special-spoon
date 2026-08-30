# pdfbox-service

Spring Boot service that fills form-free PDF report templates using
[Apache PDFBox](https://pdfbox.apache.org/) 3.x: values are drawn as page content at configured
coordinates, and the filled PDF is returned.

> Scope: only form-free ("overlay") templates with **text** and **checkbox** fields are
> supported. AcroForm filling and drawn e-signature support were removed; they are preserved on
> the `full-featured` branch.

## API

### Fill a report template

```
POST /api/reports/{templateName}
Content-Type: application/json

{
  "fields": {
    "<fieldName>": "<value>",
    ...
  }
}
```

Requests may select an immutable template version with `?version=<version>`. When omitted, the
template's configured `current-version` is used. Successful responses include the selected version
in `X-Template-Version`; an unknown version returns `404`.

- Text fields take JSON strings; checkbox fields take JSON booleans (`true`/`false`).
  Values of the wrong JSON type are rejected with a clear message.

Responses:

| Status | When |
| ------ | ---- |
| `200 application/pdf` | Filled PDF, `Content-Disposition: attachment; filename="<templateName>-filled.pdf"` |
| `400` | Body missing `fields`, a field name the template doesn't support, or a value of the wrong JSON type for the field (response `message` explains) |
| `404` | `{templateName}` is not in the supported template registry |

### Examples

```bash
./mvnw spring-boot:run

# report template, 3 pages: text fields (p1), checkboxes (p2), notes (p3)
curl -X POST http://localhost:8080/api/reports/report \
  -H 'Content-Type: application/json' \
  -d '{"fields":{"title":"Q3 Sales Report","author":"Jane Doe","date":"2026-07-19","summary":"Sales are up 12%.","confidential":true,"reviewed":true,"approved":false,"notes":"Follow up with the west region."}}' \
  -o report-filled.pdf

# certificate template, 3 pages: certificate (p1), checklist checkboxes (p2), instructor sign-off (p3)
curl -X POST http://localhost:8080/api/reports/certificate \
  -H 'Content-Type: application/json' \
  -d '{"fields":{"recipient":"Jane Doe","course":"Advanced Origami","date":"2026-07-19","module-basics":true,"module-project":true,"instructor-name":"Prof. Crane","instructor-date":"2026-07-19"}}' \
  -o certificate-filled.pdf
```

## Template registry

Supported templates are declared in `src/main/resources/templates.yml` (imported via
`spring.config.import` in `application.yml`) and bound to the type-safe
`PdfTemplateProperties` configuration type:

```yaml
pdf:
  templates:
    report:
      current-version: v1
      versions:
        v1:
          file: classpath:templates/report.pdf
          fields:
            title:
              page: 1                          # 1-based
              x: 150                           # points, origin at bottom-left
              y: 665                           # baseline of the value
              font-size: 14                    # default 12
              max-width: 400                   # optional: text wider than this is shrunk to fit
            summary:
              page: 1
              x: 150
              y: 525
              font-size: 12
              max-width: 400
              max-height: 80                  # enables multiline wrapping
              line-height: 14                 # optional: defaults to 1.2 * font-size
              alignment: left                 # left (default) | center (default) | right
              overflow: reject                # currently the only supported policy
            confidential:
              page: 2
              x: 73
              y: 679
              type: checkbox                   # text (default) | checkbox: draws X when true
```

At startup the `TemplateRegistry` inspects each PDF and **fails fast** (listing every problem)
when an entry is invalid:

- the PDF file must exist and load
- the PDF must be form-free — a PDF with an AcroForm is rejected
- coordinate `fields` must be declared, and every placement must include `page`/`x`/`y` and
  reference a page that exists in the document

The registry also captures each template's supported field names from the placement keys, which
is what request validation uses to reject unknown names with `400`.

### Field types

| Type | Rendering |
| ---- | --------- |
| text (default) | value drawn at `page/x/y`, optionally shrunk proportionally to fit `max-width` |
| checkbox | `X` drawn at `page/x/y` when the value is `true`; nothing when `false` |

Text fields become **multiline text fields** when both `max-width` and `max-height` are configured.
Text wraps at whitespace, explicit `\\n` characters force line breaks, and overlong words are split
when possible. The first line uses the configured `y` baseline; later lines move downward by
`line-height`. `left`, `center`, and `right` alignment are applied independently to each line.
Content that cannot fit within the configured width or height is rejected with `400`; it is never
silently truncated.

### Adding a new template

1. Drop the form-free PDF into `src/main/resources/templates/`.
2. Add an entry to `templates.yml` with `file:` and a placement for every field.
3. Start the app; misconfiguration fails startup with a message naming the entry and the problem.

Overlay text uses standard-14 Helvetica, so values are limited to WinAnsi (Latin) characters.
Need more glyphs or styling? Extend `PdfReportService.drawValue` to load an embedded TTF
(`PDType0Font`).

### Regenerating the bundled templates

`ReportTemplateGenerator` (test sources) writes `report.pdf` and `certificate.pdf` into
`src/main/resources/templates/` (plus `bare.pdf` and `acroform.pdf` registry-test fixtures into
test resources). If you change a template layout, update its placements in `templates.yml` to
match:

```bash
./mvnw -q test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.xuche.pdfboxservice.pdf.ReportTemplateGenerator
```

## Build

```bash
./mvnw verify   # format (Spotless/AOSP), compile, test, package
```
