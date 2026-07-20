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
  },
  "signatures": {
    "<signatureBoxName>": "<base64 image or data URL>",
    ...
  }
}
```

- `fields` — text fields take any string; checkbox fields take boolean-ish values:
  `true`/`false`, `yes`/`no`, `on`/`off`, `1`/`0` (case-insensitive).
- `signatures` — optional; drawn e-signature images (PNG or JPEG), either a
  `data:image/png;base64,...` data URL (what `canvas.toDataURL()` produces) or plain base64.
  Max 2MB base64, 4000px per dimension. See [Drawn e-signatures](#drawn-e-signatures).

Responses:

| Status | When |
| ------ | ---- |
| `200 application/pdf` | Filled PDF, `Content-Disposition: attachment; filename="<templateName>-filled.pdf"` |
| `400` | Body missing `fields`; unknown field or signature names; non-boolean checkbox value; undecodable/oversized signature image (response `message` explains) |
| `404` | `{templateName}` is not in the supported template registry |

### Examples

```bash
./mvnw spring-boot:run

# AcroForm template, 3 pages: text fields (p1), checkboxes (p2), sign-off (p3)
curl -X POST http://localhost:8080/api/reports/report \
  -H 'Content-Type: application/json' \
  -d '{"fields":{"title":"Q3 Sales Report","author":"Jane Doe","date":"2026-07-19","summary":"Sales are up 12%.","confidential":"true","reviewed":"yes","approved":"false","signed-by":"Jane Doe","signature-date":"2026-07-19"}}' \
  -o report-filled.pdf

# Same, with a drawn e-signature stamped into the signature box on page 3.
# Uses the bundled examples/signature.png as the "captured" signature and
# builds the JSON body with python3 (any base64 PNG/JPEG works):
SIG="data:image/png;base64,$(base64 -i examples/signature.png | tr -d '\n')"
SIG="$SIG" python3 -c '
import json, os, sys
json.dump({"fields": {"signed-by": "Jane Doe", "signature-date": "2026-07-19"},
           "signatures": {"signature": os.environ["SIG"]}}, sys.stdout)
' | curl -X POST http://localhost:8080/api/reports/report \
    -H 'Content-Type: application/json' \
    -d @- \
    -o report-signed.pdf

# Overlay template, 3 pages: certificate (p1), checklist checkboxes (p2), instructor sign-off (p3)
curl -X POST http://localhost:8080/api/reports/certificate \
  -H 'Content-Type: application/json' \
  -d '{"fields":{"recipient":"Jane Doe","course":"Advanced Origami","date":"2026-07-19","module-basics":"true","module-project":"yes","instructor-name":"Prof. Crane","instructor-date":"2026-07-19"}}' \
  -o certificate-filled.pdf

# Same, with the instructor's drawn e-signature stamped into the box on page 3
SIG="data:image/png;base64,$(base64 -i examples/signature.png | tr -d '\n')"
SIG="$SIG" python3 -c '
import json, os, sys
json.dump({"fields": {"recipient": "Jane Doe", "course": "Advanced Origami",
                      "date": "2026-07-19", "instructor-name": "Prof. Crane",
                      "instructor-date": "2026-07-19"},
           "signatures": {"instructor-signature": os.environ["SIG"]}}, sys.stdout)
' | curl -X POST http://localhost:8080/api/reports/certificate \
    -H 'Content-Type: application/json' \
    -d @- \
    -o certificate-signed.pdf
```

## Drawn e-signatures

**Decision (2026-07): signature capture is raster-first.** The frontend captures the signature
on an HTML `<canvas>` (plain pointer events or a library like `signature_pad`), exports it with
`canvas.toDataURL('image/png')`, and sends the data URL in `signatures`. The server decodes it,
validates type/size, and stamps it into the template's signature box, centered and scaled to fit
(aspect ratio always preserved — never stretch a signature). Rationale: simplest possible
end-to-end path, and every canvas library supports `toDataURL`.

Minimal frontend sketch:

```js
// ... draw on <canvas id="sig"> via pointer events ...
const dataUrl = canvas.toDataURL('image/png'); // render at 2-3x for print quality
await fetch('/api/reports/report', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ fields: { 'signed-by': name }, signatures: { signature: dataUrl } })
});
```

**Migration path to vector strokes (future):** raster pixelates when zoomed/printed. To upgrade,
capture stroke points instead (`signature_pad`'s `toData()`, or hand-rolled pointer tracking) and
send `{ "strokes": [[x, y], ...], "canvas": { "width": w, "height": h } }` as the signature value
(the API accepts a string today; accepting an object too is backward compatible). Server-side,
map canvas pixels into the signature box's PDF points (scale, flip the Y axis — canvas origin is
top-left, PDF bottom-left) and draw with `PDPageContentStream.moveTo/lineTo` + round line caps.
`PointerEvent.pressure` can drive per-segment line width for a pen-like feel. The box lookup,
fitting logic, and validation added here carry over unchanged.

**Not a cryptographic signature.** A stamped image is a digitized "wet" signature — it provides
no identity proof or tamper evidence. AcroForm signature fields that are *not* inked survive
flattening, so a filled report can still be digitally signed afterwards (PDFBox `addSignature` +
a server certificate) if real PKI signing is ever needed.

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
        module-basics:
          page: 2
          x: 189
          y: 478
          type: checkbox                   # text (default) | checkbox | signature
        instructor-signature:
          page: 3
          x: 500
          y: 340                           # for signature: lower-left corner of the box
          width: 180                       # required for type: signature, forbidden otherwise
          height: 60
          type: signature
```

At startup the `TemplateRegistry` inspects each PDF and **fails fast** (listing every problem)
when an entry is invalid:

- the PDF file must exist and load
- a PDF with an **AcroForm** is filled by field name; declaring coordinate `fields` for it is an
  error. Text fields and checkboxes become fillable `fields`; signature fields become
  `signatures` targets (their widget rectangle is the ink box)
- a **form-free** PDF must declare coordinate `fields`, and every placement must include
  `page`/`x`/`y` and reference a page that exists in the document; `signature` placements must
  also declare positive `width`/`height`

The registry also captures each template's supported names (from the AcroForm, or from the
placement keys), which is what request validation uses to reject unknown names with `400`.

### Field types

| Type | AcroForm | Overlay |
| ---- | -------- | ------- |
| text | `PDTextField` filled, then flattened | value drawn at `page/x/y`, optional `max-width` shrink-to-fit |
| checkbox | `PDCheckBox` checked/unchecked, then flattened | `X` drawn at `page/x/y` when the value is true-ish |
| signature | image stamped into the `PDSignatureField` widget box; field then removed. If **not** inked, the field survives flattening so the PDF stays digitally signable | image fitted into the `width`x`height` box at `page/x/y` |

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
you change a template layout, update its placements in `templates.yml` to match:

```bash
./mvnw -q test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.xuche.pdfboxservice.pdf.ReportTemplateGenerator
```

Two PDFBox quirks the generator demonstrates:

- **Checkbox appearances are never generated by PDFBox** — a `PDCheckBox` without explicit
  on/off appearance streams renders nothing when checked, so the generator draws the box and the
  ZapfDingbats check mark itself.
- **`showText` takes Unicode** — the ZapfDingbats check glyph is written as `"\u2714"`, not
  `"4"`; PDFBox translates it to glyph 0x34 via the font's encoding.

## Build

```bash
./mvnw verify   # format (Spotless/AOSP), compile, test, package
```
