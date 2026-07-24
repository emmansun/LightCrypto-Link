## 1. Create lcl-adapter-mongodb-core Module Skeleton

- [x] 1.1 Create `lcl-adapter-mongodb-core/pom.xml` with parent `lcl-parent`, dependencies on `lcl-spi`, `lcl-core`, `lcl-spring-boot-starter`, `spring-boot-starter-data-mongodb`
- [x] 1.2 Add `lcl-adapter-mongodb-core` to parent POM `<modules>` list (before `lcl-adapter-mongodb`)
- [x] 1.3 Add `lcl-adapter-mongodb-core` to parent POM `<dependencyManagement>` with `${project.version}`
- [x] 1.4 Create `src/main/java/io/github/emmansun/lightcrypto/adapter/mongodb/` directory structure
- [x] 1.5 Verify `mvn compile -pl lcl-adapter-mongodb-core` succeeds (empty module)

## 2. Migrate Shared Classes to Core Module

- [x] 2.1 Move `MongoVaultStore.java` from `lcl-adapter-mongodb` to `lcl-adapter-mongodb-core`
- [x] 2.2 Move `MongoEncryptHandler.java` to core
- [x] 2.3 Move `MongoDecryptHandler.java` to core
- [x] 2.4 Move `MongoStorageAdapter.java` to core
- [x] 2.5 Move `BsonDocumentAccessor.java` to core
- [x] 2.6 Move `BsonStructuredValueCodec.java` to core
- [x] 2.7 Move `CryptoBeforeSaveListener.java` to core
- [x] 2.8 Move `MongoCryptoEventListener.java` to core
- [x] 2.9 Move `MongoQueryTransformer.java` to core
- [x] 2.10 Move `MongoAdapterProperties.java` to core
- [x] 2.11 Move `CryptoMongoQueryCreator.java` to core
- [x] 2.12 Verify `mvn compile -pl lcl-adapter-mongodb-core` succeeds with all 11 classes

## 3. Refactor lcl-adapter-mongodb (SB3)

- [x] 3.1 Replace direct dependencies (`lcl-spi`, `lcl-core`, `lcl-spring-boot-starter`) with single dependency on `lcl-adapter-mongodb-core`
- [x] 3.2 Delete the 11 migrated source files from `lcl-adapter-mongodb/src/main/java`
- [x] 3.3 Update `MongoAdapterAutoConfiguration` imports to reference classes from core (same package, should be automatic)
- [x] 3.4 Verify `mvn compile -pl lcl-adapter-mongodb` succeeds with only 6 remaining source files
- [x] 3.5 Run SB3 adapter tests: `mvn verify -pl lcl-adapter-mongodb`

## 4. Refactor lcl-adapter-mongodb-v4 (SB4)

- [x] 4.1 Replace dependency on `lcl-adapter-mongodb` (with wildcard exclusions) with dependency on `lcl-adapter-mongodb-core`
- [x] 4.2 Remove all `<exclusion>` elements from the core dependency
- [x] 4.3 Delete `health/HealthAutoConfiguration.java` and `health/LclHealthIndicator.java` from v4 adapter
- [x] 4.4 Remove `spring-boot-health` dependency from v4 adapter POM
- [x] 4.5 Remove `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` entry for HealthAutoConfiguration
- [x] 4.6 Verify `mvn compile -pl lcl-adapter-mongodb-v4` succeeds
- [x] 4.7 Run v4 adapter tests: `mvn verify -pl lcl-adapter-mongodb-v4`

## 5. Starter Health Conditionalization

- [x] 5.1 Create `LclHealthCollector.java` in starter `observability` package — extract composition logic (iterate checks, compute worst status, build details map) with no Spring Health imports
- [x] 5.2 Refactor existing `LclHealthIndicator.java` to delegate to `LclHealthCollector` (thin shell)
- [x] 5.3 Create `LclHealthIndicatorV4.java` implementing `org.springframework.boot.health.contributor.HealthIndicator`, delegating to `LclHealthCollector`
- [x] 5.4 Add `spring-boot-health` (version `${spring-boot.version}`) as `<optional>true</optional>` dependency to starter POM
- [x] 5.5 Update `ObservabilityAutoConfiguration`: split HealthConfiguration into two inner classes with `@ConditionalOnClass(name=...)` guards
- [x] 5.6 Verify starter compiles: `mvn compile -pl lcl-spring-boot-starter`
- [x] 5.7 Run starter tests: `mvn verify -pl lcl-spring-boot-starter`

## 6. Update basic-crud-v4 Example

- [x] 6.1 Remove `spring.autoconfigure.exclude` from `application.properties`
- [x] 6.2 Remove `spring.main.allow-bean-definition-overriding` from `application.properties`
- [x] 6.3 Simplify `dependencyManagement` — remove explicit `spring-data-commons` and `spring-boot-starter-data-mongodb` overrides (should be resolved correctly via BOM alone now)
- [x] 6.4 Verify example compiles: `mvn compile -pl lcl-examples/basic-crud-v4`
- [x] 6.5 Run example locally with MongoDB to verify end-to-end

## 7. Full Build Verification

- [x] 7.1 Run full reactor build: `mvn clean verify`
- [x] 7.2 Run SpotBugs check: `mvn -B -pl lcl-spring-boot-starter spotbugs:check`
- [x] 7.3 Verify SB3 basic-crud example still works: `mvn spring-boot:run -pl lcl-examples/basic-crud`
- [x] 7.4 Verify SB4 basic-crud-v4 example still works (with MONGODB_URI env var)

## 8. Documentation and Release Configuration

- [x] 8.1 Update README project structure to include `lcl-adapter-mongodb-core`
- [x] 8.2 Update README "Spring Boot 4.x notes" — remove `spring.autoconfigure.exclude` and `dependencyManagement` override instructions
- [x] 8.3 Add `lcl-adapter-mongodb-core` to release workflow `-pl` list (`.github/workflows/release.yml`)
- [x] 8.4 Update `docs/architecture.md` if it references module structure
