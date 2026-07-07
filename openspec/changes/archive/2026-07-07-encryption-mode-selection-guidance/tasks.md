## 1. Selection Rules and Validation

- [x] 1.1 Add a single decision table in docs for element-level vs whole-object mode selection
- [x] 1.2 Extend @Encrypted with explicit mode selector (for example ELEMENT and WHOLE)
- [x] 1.3 Implement whole-object mode for simple collections/maps (List/Set/Map of scalar/simple values)
- [x] 1.4 Add startup validation assertions for whole-object plus blindIndex invalid combinations
- [x] 1.5 Add startup validation assertions for whole-object plus nested @Encrypted conflict
- [x] 1.6 Ensure IllegalStateException messages include migration guidance and example usage

## 2. Documentation and Examples

- [x] 2.1 Update root README with a mode-selection section based on business intent
- [x] 2.2 Add one mixed real-world BSON example covering scalar, element-level, and whole-object fields
- [x] 2.3 Update basic-crud demo narrative to show query-required and non-query-sensitive modeling choices

## 3. Tests and Verification

- [x] 3.1 Add integration test for query-required collection field using element-level blind index
- [x] 3.2 Add integration test for non-query simple collection using whole-object COL payload
- [x] 3.3 Add integration test for non-query simple map using whole-object MAP payload
- [x] 3.4 Add integration test for non-query POJO collection using whole-object COL payload
- [x] 3.5 Add startup-fail tests for invalid mode combinations
- [x] 3.6 Run mvn clean verify and confirm no regression in existing encryption behavior
