package io.github.emmansun.lightcrypto.benchmark;

import io.github.emmansun.lightcrypto.core.blindindex.BlindIndexEngine;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import org.openjdk.jmh.annotations.*;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for HMAC-SHA256 blind index computation.
 *
 * <p>Target: p95 < 50µs per computation on reference hardware.
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
public class BlindIndexBenchmark {

    private BlindIndexEngine engine;
    private Namespace namespace;
    private String fieldName;
    private String value;

    @Setup(Level.Trial)
    public void setup() {
        SecureRandom random = new SecureRandom();
        byte[] masterKey = new byte[32];
        random.nextBytes(masterKey);

        engine = new BlindIndexEngine(masterKey);
        namespace = Namespace.of("bench", "test", "User", "email");
        fieldName = "email";
        value = "user@example.com";
    }

    @Benchmark
    public String computeIndex() {
        return engine.computeBlindIndex(namespace, fieldName, value);
    }
}
