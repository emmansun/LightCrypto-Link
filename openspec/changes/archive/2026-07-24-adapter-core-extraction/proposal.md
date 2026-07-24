## Why

The current `lcl-adapter-mongodb-v4` module depends on the SB3 `lcl-adapter-mongodb` JAR (compile scope with wildcard exclusions) to reuse 11 shared classes. This causes a cascade of version conflicts:

1. **spring-data-commons 3.2.5 vs 4.0.5** — parent POM manages 3.2.5, SB4 requires 4.0.5; wildcard exclusions and explicit overrides needed in every downstream module
2. **spring-core 6.x vs 7.x** — SB3 starter's flattened POM leaks spring-core 6.1.6 into SB4 classpath
3. **Classpath ordering sensitivity** — v4 JAR must precede SB3 JAR for class loading to work
4. **User-side complexity** — SB4 applications need `spring.autoconfigure.exclude`, `allow-bean-definition-overriding`, and explicit `dependencyManagement` overrides

Additionally, the SB4 `LclHealthIndicator` currently lives in `lcl-adapter-mongodb-v4`. This means any future SB4 storage adapter (JDBC, Elasticsearch, etc.) would need to depend on the MongoDB adapter just to get health support — a clear violation of module responsibility boundaries.

## What Changes

- Create new module `lcl-adapter-mongodb-core` containing the 11 version-independent MongoDB classes shared between SB3 and SB4 adapters
- Refactor `lcl-adapter-mongodb` to depend on core (retaining only SB3 query layer + auto-configuration)
- Refactor `lcl-adapter-mongodb-v4` to depend on core instead of the SB3 adapter JAR (removing wildcard exclusions and the entire `health/` package)
- Move SB4 health support into `lcl-spring-boot-starter` via optional dependency + `@ConditionalOnClass` conditional activation
- Simplify SB4 application configuration (remove `spring.autoconfigure.exclude`, reduce `dependencyManagement` overrides)

## Capabilities

### New Capabilities
- `mongodb-adapter-core`: A shared Maven module `lcl-adapter-mongodb-core` containing version-independent MongoDB adapter classes (VaultStore, handlers, listeners, codec, query transformer, properties, query creator) that compile against Spring Data MongoDB 4.x and run correctly on both 4.x (SB3) and 5.x (SB4)

### Modified Capabilities
- `lcl-adapter-mongodb-v4`: Depends on `lcl-adapter-mongodb-core` instead of `lcl-adapter-mongodb`; no longer contains health classes; no wildcard exclusions needed
- `mongo-adapter`: Shared classes relocated to `lcl-adapter-mongodb-core`; module retains only SB3-specific query layer and auto-configuration
- `starter-health-sb4-compat`: Health indicator support fully provided by the starter via conditional activation (optional `spring-boot-health` dependency + `@ConditionalOnClass`); no adapter involvement
- `starter-mongo-separation`: Class location constraints updated to reflect shared classes residing in `lcl-adapter-mongodb-core`

## Impact

- **New module**: `lcl-adapter-mongodb-core` with its own `pom.xml`, added to parent reactor and release pipeline
- **lcl-adapter-mongodb**: Reduced from 17 to 6 source files; depends on core module
- **lcl-adapter-mongodb-v4**: Reduced from 8 to 6 source files (health/ removed); depends on core module; wildcard exclusions eliminated
- **lcl-spring-boot-starter**: Gains `LclHealthCollector` (pure logic) + SB4 thin-shell indicator; adds `spring-boot-health` optional dependency
- **SB4 applications**: No longer need `spring.autoconfigure.exclude` or `allow-bean-definition-overriding`; dependencyManagement simplified
- **CI/CD**: New module added to build matrix and release `-pl` list
- **Users**: No API changes; SB3 users unaffected; SB4 users get simpler configuration
