## 1. SPI Extension (lcl-spi)

- [x] 1.1 Replace `concurrencyToken` field with `fieldKids` (`Map<String, String>`) in `RawDocument` record
- [x] 1.2 Update `RawDocument` Javadoc to document `fieldKids` semantics

## 2. MongoDB Adapter (lcl-adapter-mongodb-core)

- [x] 2.1 Modify `MongoDocumentRewriteStore.replace()` to build per-field `_k` filter from `fieldKids`
- [x] 2.2 Update `replaceBatch()` with same per-field filter logic for each `ReplaceOneModel`
- [x] 2.3 Remove `concurrencyField` constructor parameter and `DEFAULT_CONCURRENCY_FIELD` constant
- [x] 2.4 Update `MongoCloseableIterator.toRawDocument()` to extract `_k` from encrypted sub-documents

## 3. DekReEncryptionService (lcl-spring-boot-starter)

- [x] 3.1 Modify scan phase to extract per-field kid snapshots using `CryptoMetadataCache` field list
- [x] 3.2 Pass `fieldKids` map when constructing `RawDocument` instances
- [x] 3.3 Verify CAS conflict counting still works with new filter semantics

## 4. Tests

- [x] 4.1 Add unit test: `MongoDocumentRewriteStore.replace()` builds correct dot-notation filter
- [x] 4.2 Add unit test: CAS conflict when `_k` changed concurrently
- [x] 4.3 Add unit test: `fieldKids` empty results in `_id`-only filter
- [x] 4.4 Update existing `DekReEncryptionServiceTest` CAS scenarios

## 5. Documentation

- [x] 5.1 Update `docs/key-lifecycle.md` CAS description to reflect per-field `_k` strategy

## 6. Verification

- [x] 6.1 Run `mvn clean compile` — all modules compile
- [x] 6.2 Run `mvn test` — all tests pass
- [x] 6.3 Run `mvn -pl lcl-spring-boot-starter spotbugs:check` — zero warnings
