## 1. Bootstrap Engine Core (lcl-core)

- [ ] 1.1 Create `io.github.emmansun.lightcrypto.core.bootstrap.FailureClass` enum: FATAL, RECOVERABLE, ADVISORY
- [ ] 1.2 Create `BootstrapCheck` functional interface: `BootstrapResult check(BootstrapContext context)`
- [ ] 1.3 Create `BootstrapPhase` record with fields: name, check, failureClass
- [ ] 1.4 Create `BootstrapResult` record with fields: status (READY/FAILED/DEGRADED), phaseResults (list), durationMs, failedPhase (nullable), errorDetails (nullable)
- [ ] 1.5 Create `BootstrapContext` immutable class carrying: CmkProvider, EventBus, RuntimeProperties, optional VaultStore
- [ ] 1.6 Create `BootstrapTimeoutException` extending CryptoException
- [ ] 1.7 Create `BootstrapEngine` class: sequential phase execution, timeout enforcement, EventBus event emission, retry logic for RECOVERABLE phases
- [ ] 1.8 Write unit tests for BootstrapEngine (all-pass, fatal-abort, advisory-continue, timeout, recoverable-retry)

## 2. KAT Runner (lcl-core)

- [ ] 2.1 Copy KAT vector JSON files from project `vectors/` to `lcl-core/src/main/resources/kat/` (encryption/, blind-index/, kcv/)
- [ ] 2.2 Create `KatVectorLoader` — load JSON vectors from classpath `/kat/` resources
- [ ] 2.3 Create `KatRunner` implementing `BootstrapCheck` — run encryption KAT for AES-256-GCM, SM4-GCM, AES-256-CBC, SM4-CBC
- [ ] 2.4 Add HKDF-SHA-256 KAT execution to KatRunner
- [ ] 2.5 Add HMAC-SHA-256 blind index KAT execution to KatRunner
- [ ] 2.6 Implement KAT timing enforcement (total ≤ 200ms, per-primitive ≤ 30ms)
- [ ] 2.7 Write unit tests for KatRunner (all-pass, single-algorithm-fail, timeout, missing-vectors)

## 3. Canary Self-Test (lcl-core)

- [ ] 3.1 Create `CanaryRunner` implementing `BootstrapCheck` — canary encrypt/decrypt roundtrip using embedded canary DEK + namespace
- [ ] 3.2 Add multi-algorithm canary (iterate over allowed algorithms)
- [ ] 3.3 Add metadata roundtrip canary (encrypt → binary encode → decode → verify)
- [ ] 3.4 Write unit tests for CanaryRunner (success, encrypt-fail, decrypt-fail, metadata-fail)

## 4. Bootstrap Phase Implementations (lcl-core)

- [ ] 4.1 Create `ConfigValidationCheck` — verify RuntimeProperties are valid (BOOT-1)
- [ ] 4.2 Create `SpiVersionCheck` — verify spiVersion == 1 (BOOT-2)
- [ ] 4.3 Create `VaultReachabilityCheck` — verify VaultStore.load() does not throw (BOOT-8), RECOVERABLE class
- [ ] 4.4 Create `KmsReachabilityCheck` — verify CmkProvider can wrap/unwrap a canary key (BOOT-9), RECOVERABLE class
- [ ] 4.5 Write unit tests for each check implementation

## 5. Bootstrap Integration in Starter

- [ ] 5.1 Add `bootstrap-enabled` and `bootstrap-timeout` fields to `RuntimeProperties`
- [ ] 5.2 Create `DiagnosticsAutoConfiguration` with `@ConditionalOnProperty("lightcrypto.runtime.bootstrap-enabled", matchIfMissing=true)`
- [ ] 5.3 Create `LclBootstrapRunner` implementing `ApplicationRunner` — constructs BootstrapContext, registers phases, runs BootstrapEngine
- [ ] 5.4 Register LclBootstrapRunner bean in DiagnosticsAutoConfiguration
- [ ] 5.5 Wire BootstrapResult into LclHealthIndicator (from observability-foundation) for status reporting
- [ ] 5.6 Add DiagnosticsAutoConfiguration to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 6. Actuator Diagnostics Endpoints

- [ ] 6.1 Add `spring-boot-starter-actuator` as optional dependency to `lcl-spring-boot-starter/pom.xml`
- [ ] 6.2 Create `LclHealthEndpoint` with `@Endpoint(id = "lclhealth")` + `@ReadOperation` returning diagnostic JSON
- [ ] 6.3 Create `LclKatEndpoint` with `@Endpoint(id = "lclkat")` + `@ReadOperation` returning KAT results, `@WriteOperation` for on-demand re-run
- [ ] 6.4 Implement response redaction (CMK ID hashing, secret masking)
- [ ] 6.5 Register endpoints in DiagnosticsAutoConfiguration with `@ConditionalOnClass(Endpoint.class)`
- [ ] 6.6 Write unit tests for endpoint responses (ready state, degraded state, redaction verification)

## 7. Tests & Verification

- [ ] 7.1 Write integration test: full bootstrap sequence with mock KMS + vault, verify all events emitted
- [ ] 7.2 Write integration test: KAT failure aborts bootstrap, verify FATAL status
- [ ] 7.3 Write integration test: canary roundtrip during bootstrap, verify success event
- [ ] 7.4 Write integration test: actuator endpoints return correct JSON
- [ ] 7.5 Run `mvn -B -pl lcl-core,lcl-spring-boot-starter clean verify` — fix any failures
- [ ] 7.6 Run `mvn -B -pl lcl-spring-boot-starter spotbugs:check` — fix any warnings

## 8. Documentation Sync

- [ ] 8.1 Update `docs/configuration.md` — add `lightcrypto.runtime.bootstrap-enabled` and `lightcrypto.runtime.bootstrap-timeout` properties
- [ ] 8.2 Update `README.md` — add bootstrap diagnostics section (KAT, canary, actuator endpoints, failure behavior)
- [ ] 8.3 Ensure diagnostics endpoint `sdkVersion` field uses `SdkVersion.getVersion()` (from observability-foundation) instead of hardcoded value
