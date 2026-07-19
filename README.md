# pdfbox-service

Spring Boot service that fills PDF report templates using
[Apache PDFBox](https://pdfbox.apache.org/) 3.x and returns the filled PDF.

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

Responses:

| Status | When |
| ------ | ---- |
| `200 application/pdf` | Filled PDF, `Content-Disposition: attachment; filename="<templateName>-filled.pdf"` |
| `400` | Body missing `fields`, or `fields` contains names the template doesn't support (response `message` lists the supported ones) |
| `404` | `{templateName}` is not in the supported template registry |

### Examples

```bash
./mvnw spring-boot:run

# AcroForm template (fields: title, author, date, summary)
curl -X POST http://localhost:8080/api/reports/report \
  -H 'Content-Type: application/json' \
  -d '{"fields":{"title":"Q3 Sales Report","author":"Jane Doe","date":"2026-07-19","summary":"Sales are up 12%."}}' \
  -o report-filled.pdf

# Overlay template (fields: recipient, course, date)
curl -X POST http://localhost:8080/api/reports/certificate \
  -H 'Content-Type: application/json' \
  -d '{"fields":{"recipient":"Jane Doe","course":"Advanced Origami","date":"2026-07-19"}}' \
  -o certificate-filled.pdf
```

## Template registry

Supported templates are declared in `src/main/resources/templates.yml` (imported via
`spring.config.import` in `application.yml`) and bound to the type-safe
`PdfTemplateProperties` record:

```yaml
pdf:
  templates:
    report:                                # AcroForm template: no fields config needed
      file: classpath:templates/report.pdf
    certificate:                           # form-free template: coordinate placements required
      file: classpath:templates/certificate.pdf
      fields:
        recipient:
          page: 1                          # 1-based
          x: 180                           # points, origin at bottom-left
          y: 400                           # baseline of the value
          font-size: 28                    # default 12
          max-width: 432                   # optional: text wider than this is shrunk to fit
```

At startup the `TemplateRegistry` inspects each PDF and **fails fast** (listing every problem)
when an entry is invalid:

- the PDF file must exist and load
- a PDF with an **AcroForm** is filled by field name; declaring coordinate `fields` for it is an
  error
- a **form-free** PDF must declare coordinate `fields`, and every placement must include
  `page`/`x`/`y` and reference a page that exists in the document

The registry also captures each template's supported field names (from the AcroForm, or from the
placement keys), which is what request validation uses to reject unknown fields with `400`.

### Adding a new template

1. Drop the PDF into `src/main/resources/templates/`.
2. Add an entry to `templates.yml` — just `file:` for an AcroForm PDF, or `file:` + `fields:`
   placements for a form-free PDF.
3. Start the app; misconfiguration fails startup with a message naming the entry and the problem.

Overlay text uses standard-14 Helvetica, so values are limited to WinAnsi (Latin) characters.
Need more glyphs or styling? Extend `PdfReportService.drawValue` to load an embedded TTF
(`PDType0Font`).

### Regenerating the bundled templates

`ReportTemplateGenerator` (test sources) writes `report.pdf` and `certificate.pdf` into
`src/main/resources/templates/` (and `bare.pdf`, a form-free fixture, into test resources). If
you change the certificate layout, update its placements in `templates.yml` to match:

```bash
./mvnw -q test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.xuche.pdfboxservice.pdf.ReportTemplateGenerator
```

## Build

```bash
./mvnw verify   # format (Spotless/AOSP), compile, test, package
```
