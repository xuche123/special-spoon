# PDF Report Generation

This context defines the language used when configuring and generating form-free PDF reports from templates.

## Template fields

**Text field**:
A template placement that renders a string value at configured PDF coordinates.
_Avoid_: text input, text box

**Multiline text field**:
A text field whose value may occupy multiple wrapped lines within configured width and height limits.
_Avoid_: paragraph field, wrapped field

**Checkbox field**:
A template placement that renders an X when its boolean value is true and remains empty when false.
_Avoid_: check field, toggle

**Template**:
A form-free PDF document together with the field placements that define where values are rendered.
_Avoid_: form, document template

**Field placement**:
The page, coordinates, typography, and layout rules assigned to one template field.
_Avoid_: field configuration, coordinate entry

**Template font**:
The font used to render text fields in a template, including the glyphs needed for the template's supported writing systems.
_Avoid_: field font, report font

**Template version**:
An immutable revision of a template and its field placements that can be selected when generating a report.
_Avoid_: template release, PDF revision

**Current version**:
The default template version selected when a report request does not specify a version.

**Template preview**:
A development or administrative rendering of a template that shows how configured field placements and sample values will appear.
_Avoid_: mock PDF, form preview

**Structured error**:
A stable machine-readable error response describing why an API request could not be completed without exposing internal diagnostics.
_Avoid_: error string, exception response
