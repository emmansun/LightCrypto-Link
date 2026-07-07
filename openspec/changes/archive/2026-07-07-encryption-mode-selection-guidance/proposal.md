## Why

Collection and nested object encryption now support both element-level and whole-object modes, but project guidance is still implicit and easy to misapply. We need a single normative spec that defines mode-selection rules, queryability boundaries, and fail-fast constraints.

## What Changes

- Define a new capability that standardizes encryption mode selection for object fields and collections/maps.
- Specify a clear decision matrix: when to use element-level encryption vs whole-object encryption.
- Add explicit mode selection support for simple collections/maps (for example `List<String>`, `Set<Integer>`, `Map<String, String>`) so users can opt into whole-object encryption.
- Require startup validation for invalid combinations (such as whole-object mode with blind index or mixed nested encrypted fields).
- Require user-facing documentation and examples that map common business scenarios to the recommended mode.

## Capabilities

### New Capabilities
- `encryption-mode-selection`: Define and validate mode-selection rules for scalar, nested, collection, and whole-object encryption paths.

### Modified Capabilities
- `encrypted-annotation`: Add an explicit mode selector to resolve element-level vs whole-object behavior for collection and map fields.

## Impact

- Affects entity metadata validation and startup diagnostics in the spring-boot-starter module.
- Affects annotation contract and metadata model for collection/map fields.
- Affects README and example guidance to reduce configuration ambiguity.
- Clarifies query behavior expectations for teams using blind index with collections.
