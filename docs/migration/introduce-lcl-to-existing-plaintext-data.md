# Migration Guide: Introduce LightCrypto-Link to Existing Plaintext Data

This guide explains runtime behavior when a project starts with plaintext MongoDB data and later introduces LightCrypto-Link.

## Scenario

- Existing records were written before LightCrypto-Link was enabled.
- New records are written after enabling LightCrypto-Link and use encrypted sub-documents.
- Some encrypted fields may use blind index.

## Behavior Summary

### Read behavior

- Plaintext historical values are read successfully.
- During read conversion, only values in encrypted sub-document format are decrypted.
- If a value is not an encrypted sub-document, it is left unchanged.
- Unchanged plaintext values are then mapped by Spring Data converter and returned to application code.

Result: historical plaintext data does not fail just because LightCrypto-Link is enabled.

Notes:

- This is true when stored plaintext type is compatible with the entity field type.
- If historical data has incompatible types, conversion can still fail as a normal mapping issue.

### Query behavior for fields with blind index

- Queries on encrypted fields with blind index are rewritten to blind-index lookup.
- Historical plaintext records usually do not contain blind-index field data.

Result: queries may miss historical plaintext records. This is typically a coverage gap, not an exception.

### Query behavior for fields without blind index

- Querying an encrypted field with blind index disabled is not supported.

Result: the query path throws an unsupported operation exception by design.

### Legacy encrypted format edge case

- If old encrypted records exist in an incompatible structure (for example, missing key id marker), decryption may fail.

Result: this is a data-format compatibility issue, not a plaintext-legacy issue.

## Recommended Rollout

1. Enable LightCrypto-Link in write path first.
2. Keep read path compatible with both plaintext and encrypted values.
3. Plan a backfill job to migrate historical plaintext records to encrypted format.
4. Rebuild blind index data for migrated records.
5. Add operational metrics:
   - plaintext vs encrypted record ratio
   - query miss ratio for blind-index paths
6. Remove temporary compatibility handling after migration completion.

## Temporary Query Strategy During Migration Window

If business requires full hit-rate before backfill completion, use an application-side migration window strategy:

- Execute blind-index query for encrypted records.
- Add a temporary legacy-path query for historical plaintext records.
- Merge and de-duplicate results in application service layer.

This should be temporary and removed after data backfill is complete.

## Runnable Backfill Example

This repository includes a runnable reference implementation:

- Runner: `lightcrypto-link-examples/basic-crud/src/main/java/io/github/emmansun/lightcrypto/example/basiccrud/UserPlaintextBackfillRunner.java`
- Demo toggle: `lcl.demo.enabled=false` (to avoid demo data reset while backfilling)
- Backfill toggle: `lcl.migration.backfill.enabled=true`

Recommended flow:

1. Run dry-run first to estimate candidate volume.
2. Run real backfill with controlled batch size.
3. Resume from the last cursor when needed (`start-after-id`).

Example commands:

```bash
# Dry-run
mvn -pl lightcrypto-link-examples/basic-crud spring-boot:run \
   -Dspring-boot.run.jvmArguments="-Dlcl.demo.enabled=false -Dlcl.migration.backfill.enabled=true -Dlcl.migration.backfill.dry-run=true -Dlcl.migration.backfill.batch-size=500"

# Real write mode
mvn -pl lightcrypto-link-examples/basic-crud spring-boot:run \
   -Dspring-boot.run.jvmArguments="-Dlcl.demo.enabled=false -Dlcl.migration.backfill.enabled=true -Dlcl.migration.backfill.dry-run=false -Dlcl.migration.backfill.batch-size=500"
```

Backfill strategy used by the runner:

- Page by `_id` in ascending order.
- Select candidates likely to be plaintext or missing blind index.
- Load entity by id and call repository `save(...)` to trigger normal encryption write path.
- Print progress and cursor each batch for restartability.

## Checklist

- [ ] Confirm encrypted fields that are queryable require blind index.
- [ ] Run backfill for historical plaintext data.
- [ ] Verify blind-index fields exist on migrated records.
- [ ] Validate query hit-rate before and after migration.
- [ ] Remove migration-window fallback query logic.
