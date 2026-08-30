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
