# ADR-0001: Use immutable template versions with a current default

## Status

Accepted

## Context

Reports must be reproducible when templates change, while normal callers should not need to know which version is current. Template loading is currently startup-based and classpath-backed; runtime reload and the first external storage backend are not yet required.

## Decision

Templates are exposed through a storage abstraction whose entries have opaque, immutable template version identifiers. Each template has a current version used by default. Callers may optionally select another available version, and generated reports identify the selected version in the response.

The initial implementation remains restart-based and uses classpath resources. Filesystem and object-storage adapters may be added later without changing the report-generation contract.

## Consequences

- Existing callers continue to use the template name without a version.
- Auditing and reproducibility are possible by recording the returned version identifier.
- Replacing a template requires publishing a new immutable version and changing the current-version selection.
- Runtime reload, deletion semantics, and the first external backend remain future decisions.