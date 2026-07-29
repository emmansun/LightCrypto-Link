## ADDED Requirements

### Requirement: JMH benchmark module exists and compiles
A Maven module `lcl-benchmarks` SHALL exist with JMH 1.37+ dependency, and SHALL compile successfully as part of the `bench` profile.

#### Scenario: Benchmark module compiles
- **WHEN** `mvn compile -Pbench` is run from the parent POM
- **THEN** `lcl-benchmarks` SHALL compile with zero errors

#### Scenario: Benchmark module excluded from default build
- **WHEN** `mvn verify` is run without `-Pbench`
- **THEN** `lcl-benchmarks` SHALL NOT be included in the reactor

### Requirement: AES-256-GCM single-field benchmark
The benchmark suite SHALL measure single-field AES-256-GCM encryption and decryption latency.

#### Scenario: AES-256-GCM encrypt p95 within target
- **WHEN** `AesGcmBenchmark.encryptField` is executed
- **THEN** the p95 latency SHALL be reported in microseconds
- **THEN** the p95 latency SHALL be < 250µs on reference hardware (4-core, JDK 17)

#### Scenario: AES-256-GCM decrypt p95 within target
- **WHEN** `AesGcmBenchmark.decryptField` is executed
- **THEN** the p95 latency SHALL be < 250µs on reference hardware

### Requirement: SM4-GCM single-field benchmark
The benchmark suite SHALL measure single-field SM4-GCM encryption and decryption latency via Bouncy Castle.

#### Scenario: SM4-GCM encrypt p95 within target
- **WHEN** `Sm4GcmBenchmark.encryptField` is executed
- **THEN** the p95 latency SHALL be reported in microseconds

#### Scenario: SM4-GCM decrypt p95 within target
- **WHEN** `Sm4GcmBenchmark.decryptField` is executed
- **THEN** the p95 latency SHALL be reported in microseconds

### Requirement: Blind index computation benchmark
The benchmark suite SHALL measure HMAC-SHA256 blind index computation latency.

#### Scenario: Blind index p95 within target
- **WHEN** `BlindIndexBenchmark.computeIndex` is executed
- **THEN** the p95 latency SHALL be < 50µs on reference hardware

### Requirement: Batch document encryption benchmark
The benchmark suite SHALL measure encryption of a simulated document with 8 encrypted fields (mixed types: String, Integer, nested object).

#### Scenario: Batch document encrypt throughput
- **WHEN** `BatchDocumentBenchmark.encryptDocument` is executed
- **THEN** the average time per document SHALL be reported
- **THEN** the p95 per-field latency (total / 8) SHALL be < 250µs

#### Scenario: Batch document decrypt throughput
- **WHEN** `BatchDocumentBenchmark.decryptDocument` is executed
- **THEN** the average time per document SHALL be reported

### Requirement: DEK cache benchmark
The benchmark suite SHALL measure DEK retrieval latency for cache-hit and cold-path scenarios.

#### Scenario: DEK cache hit latency
- **WHEN** `DekCacheBenchmark.cacheHit` is executed (DEK already in ConcurrentHashMap)
- **THEN** the p95 latency SHALL be < 5µs

#### Scenario: DEK cold path latency
- **WHEN** `DekCacheBenchmark.coldPath` is executed (DEK must be unwrapped from VaultDocument)
- **THEN** the p95 latency SHALL be reported (no hard target — depends on CMK provider)

### Requirement: JSON output format
Benchmarks SHALL output results in JMH JSON format to a configurable path.

#### Scenario: Results written as JSON
- **WHEN** benchmarks complete execution
- **THEN** results SHALL be written to `results/benchmark-results.json` in JMH JSON schema
- **THEN** the JSON SHALL contain per-benchmark p50, p95, p99, and average values

### Requirement: Committed baseline for regression detection
A `baseline.json` file SHALL be committed to the repository representing the known-good performance state.

#### Scenario: Baseline exists and is valid JSON
- **WHEN** inspecting `lcl-benchmarks/baseline.json`
- **THEN** it SHALL be valid JMH JSON format with the same benchmark names as the current suite

### Requirement: Nightly CI benchmark workflow
A GitHub Actions workflow SHALL run benchmarks nightly and detect regressions against the committed baseline.

#### Scenario: Nightly benchmark runs successfully
- **WHEN** the `benchmark.yml` workflow triggers on schedule
- **THEN** benchmarks SHALL execute and produce `benchmark-results.json`

#### Scenario: Regression detected
- **WHEN** any benchmark p95 exceeds the baseline p95 by more than 10%
- **THEN** the workflow SHALL mark the run as failed with a summary annotation listing regressed benchmarks

#### Scenario: No regression
- **WHEN** all benchmark p95 values are within 10% of baseline
- **THEN** the workflow SHALL pass and optionally update the baseline if all metrics improved
