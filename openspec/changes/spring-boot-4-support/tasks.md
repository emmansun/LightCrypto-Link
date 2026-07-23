## 1. Research Spring Data MongoDB 4.x Replacement APIs

- [x] 1.1 Research `ValueExpressionDelegate` API — identify the constructor/method signature for `PartTreeMongoQuery`, `StringBasedMongoQuery`, `StringBasedAggregation` in Spring Data MongoDB 4.0.x
- [x] 1.2 Research `MongoRepositoryFactory.getQueryLookupStrategy()` method signature in Spring Data MongoDB 4.0.x — confirm new parameter type replacing `QueryMethodEvaluationContextProvider`
- [x] 1.3 Verify `CryptoMongoQueryCreator` and `CryptoMongoRepositoryFactoryBean` compile unchanged on SB4

## 2. V4 Adapter Module Skeleton

- [x] 2.1 Create `lcl-adapter-mongodb-v4/pom.xml` with `spring-boot.version=4.0.x`, `spring-boot-starter-data-mongodb` 4.x, and compile dependency on `lcl-adapter-mongodb`
- [x] 2.2 Add `lcl-adapter-mongodb-v4` to parent POM `<modules>` list
- [x] 2.3 Add `lcl-adapter-mongodb-v4` to parent POM `<dependencyManagement>`
- [x] 2.4 Create `src/main/java/io/github/emmansun/lightcrypto/adapter/mongodb/` directory structure

## 3. Query Layer Adaptation

- [x] 3.1 Create SB4-adapted `CryptoMongoRepositoryFactory` — update `getQueryLookupStrategy()` to use the new SB4 parameter type
- [x] 3.2 Create SB4-adapted `CryptoPartTreeMongoQuery` — update `super()` constructor to match SB4's `PartTreeMongoQuery` signature
- [x] 3.3 Create SB4-adapted `CryptoQueryLookupStrategy` — replace `QueryMethodEvaluationContextProvider` with SB4 equivalent, update `StringBasedMongoQuery` and `StringBasedAggregation` constructor calls
- [x] 3.4 Create SB4-adapted `MongoAdapterAutoConfiguration` — update `MongoAutoConfiguration` import to `org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration`
- [x] 3.5 Verify `CryptoMongoQueryCreator` compiles against SB4 (likely no changes needed)
- [x] 3.6 Verify `CryptoMongoRepositoryFactoryBean` compiles against SB4 (likely no changes needed)

## 4. Auto-Configuration Registration

- [x] 4.1 Create `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` for the v4 adapter
- [x] 4.2 Add `@ConditionalOnMissingBean` guard to prevent both SB3 and SB4 adapters from being active simultaneously

## 5. Health Classes SB4 Compatibility (Starter)

- [x] 5.1 Design health abstraction approach — decide between interface-based adapter or conditional source sets
- [x] 5.2 Refactor `LclHealthIndicator` to work on both SB3 and SB4 health packages
- [x] 5.3 Update `ObservabilityAutoConfiguration` health reference for cross-version compatibility
- [x] 5.4 Add `spring-boot-health` as optional dependency (for SB4 health classes)
- [x] 5.5 Verify health indicator still works on SB3 (no regressions)

## 6. Testing

- [x] 6.1 Determine SB4 integration test strategy — evaluate Flapdoodle `spring3x` compatibility or use Testcontainers MongoDB
- [x] 6.2 Create SB4-compatible integration test setup in the v4 adapter module
- [x] 6.3 Run v4 adapter tests (`mvn -pl lcl-adapter-mongodb-v4 clean verify`) — fix any failures
- [x] 6.4 Run SB3 adapter tests (`mvn -pl lcl-adapter-mongodb clean verify`) — confirm no regressions
- [x] 6.5 Run starter tests (`mvn -pl lcl-spring-boot-starter clean verify`) — confirm health changes work on SB3
- [x] 6.6 Run full reactor build (`mvn clean verify`) — confirm all modules pass

## 7. CI/CD and Release

- [x] 7.1 Add Spring Boot 4.x build row to CI build matrix (`.github/workflows/build.yml`)
- [x] 7.2 Add `lcl-adapter-mongodb-v4` to the release workflow's `-pl` list (`.github/workflows/release.yml`)
- [x] 7.3 Verify Maven Central publishing includes the v4 adapter

## 8. Documentation

- [x] 8.1 Update README to document dual adapter support (SB3 vs SB4) and migration guide
- [x] 8.2 Add note about adapter selection based on Spring Boot version
