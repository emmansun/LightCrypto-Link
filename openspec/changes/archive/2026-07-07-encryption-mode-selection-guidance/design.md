## Context

The project now supports two powerful but different patterns for object and collection encryption: element-level encryption and whole-object encryption. Teams can satisfy both queryability and confidentiality requirements, but the current choice model is dispersed across implementation details and historical changes. This creates onboarding friction and inconsistent usage across modules.

## Goals / Non-Goals

**Goals:**
- Establish a normative selection model for encryption modes.
- Support whole-object encryption for simple collections/maps through an explicit, non-ambiguous opt-in.
- Ensure invalid combinations fail fast at startup with actionable messages.
- Provide practical guidance that maps business intent (queryable vs non-queryable) to concrete annotation usage.
- Keep existing supported behavior unchanged while making usage deterministic and explicit.

**Non-Goals:**
- Add partial-match query support over encrypted data.
- Change existing ciphertext structure or key-management flow.

## Decisions

### Decision 1: Keep both modes as first-class options
- Rationale: element-level and whole-object solve different problems and both are needed in production.
- Alternative considered: remove whole-object mode for collection POJO fields. Rejected because it weakens confidentiality for non-query workloads.

### Decision 2: Standardize selection by business intent
- Query-required fields SHALL use element-level encryption with blind index where needed.
- High-confidentiality fields that do not require query SHALL use whole-object encryption.
- Alternative considered: technical-type-only guidance (for example scalar always element-level, POJO always whole-object). Rejected because it ignores business access patterns.

### Decision 3: Add explicit mode selector to remove ambiguity
- Add an annotation-level mode selector (for example `mode = ELEMENT | WHOLE`) for collection and map fields.
- Default mode remains existing behavior for backward compatibility (simple collections/maps stay element-level unless explicitly switched to whole-object mode).
- Whole-object mode SHALL be available for both POJO collections and simple collections/maps.
- Alternative considered: implicit inference based on element type only. Rejected because `List<String>` and `Map<String, String>` require both queryable and non-queryable use cases.

### Decision 4: Enforce strict fail-fast constraints
- Whole-object mode with blind index SHALL be rejected.
- Whole-object mode combined with nested @Encrypted fields in the same object graph SHALL be rejected.
- Alternative considered: runtime warning only. Rejected because late failures are difficult to debug and can leak inconsistent behavior.

### Decision 5: Publish a canonical decision matrix in documentation
- README and runnable examples SHALL include selection rules and representative storage/query trade-offs.
- Alternative considered: code comments only. Rejected because users integrate from docs first.

## Risks / Trade-offs

- [Risk] Teams overuse whole-object mode and lose queryability. -> Mitigation: explicit decision matrix and examples showing query-required scenarios.
- [Risk] Startup validation may block legacy ambiguous models. -> Mitigation: actionable error messages and migration notes.
- [Risk] Documentation can drift from implementation. -> Mitigation: add integration tests that map directly to decision-matrix scenarios.
