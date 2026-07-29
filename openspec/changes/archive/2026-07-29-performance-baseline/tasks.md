## 1. Module Skeleton

- [x] 1.1 Create `lcl-benchmarks/pom.xml` with JMH 1.37+ dependencies, `maven-shade-plugin` for benchmark JAR packaging, and dependencies on `lcl-core` + `lcl-spi` + `bcprov-jdk18on`
- [x] 1.2 Add `bench` profile to parent POM with `lcl-benchmarks` in `<modules>`
- [x] 1.3 Create `lcl-benchmarks/src/main/java/io/github/emmansun/lightcrypto/benchmark/` package structure
- [x] 1.4 Verify module compiles: `mvn compile -Pbench -pl lcl-benchmarks`

## 2. Benchmark Classes

- [x] 2.1 Create `AesGcmBenchmark.java` — single-field AES-256-GCM encrypt/decrypt with `@Fork(2)`, `@Warmup(3)`, `@Measurement(5)`, `Mode.AverageTime` + `Mode.SampleTime`
- [x] 2.2 Create `Sm4GcmBenchmark.java` — single-field SM4-GCM encrypt/decrypt (register BouncyCastleProvider in `@Setup`)
- [x] 2.3 Create `BlindIndexBenchmark.java` — HMAC-SHA256 blind index computation
- [x] 2.4 Create `BatchDocumentBenchmark.java` — simulated 8-field document encrypt/decrypt (String, Integer, nested object fields)
- [x] 2.5 Create `DekCacheBenchmark.java` — DEK cache hit (ConcurrentHashMap lookup) vs cold path (CMK unwrap simulation)

## 3. JSON Output and Baseline

- [x] 3.1 Configure JMH `ResultFormatType.JSON` output to `results/benchmark-results.json`
- [x] 3.2 Run benchmarks locally to generate initial results
- [x] 3.3 Commit `lcl-benchmarks/baseline.json` from initial run results
- [x] 3.4 Create `scripts/compare-baseline.py` (or shell) — compares `benchmark-results.json` vs `baseline.json`, exits non-zero if any p95 regresses >10%

## 4. CI Workflow

- [x] 4.1 Create `.github/workflows/benchmark.yml` — nightly schedule (`cron '0 2 * * *'`), JDK 17, runs `mvn verify -Pbench -pl lcl-benchmarks`
- [x] 4.2 Add comparison step — run `compare-baseline.py`, fail workflow on regression
- [x] 4.3 Add summary annotation output listing regressed benchmarks with old/new p95 values

## 5. Verification

- [x] 5.1 Run full benchmark suite locally: `mvn verify -Pbench -pl lcl-benchmarks` — confirm all 5 benchmarks execute and produce JSON
- [x] 5.2 Verify default build excludes benchmarks: `mvn verify` — confirm `lcl-benchmarks` NOT in reactor
- [x] 5.3 Validate p95 targets: AES-GCM encrypt < 250µs, blind index < 50µs, DEK cache hit < 5µs
- [x] 5.4 Test comparison script with synthetic regression (modify baseline to trigger failure)
