package io.github.emmansun.lightcrypto.benchmark;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for single-field AES-256-GCM encryption and decryption.
 *
 * <p>Target: p95 < 250µs per field operation on reference hardware (4-core, JDK 17).
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
public class AesGcmBenchmark {

    private byte[] dek;
    private byte[] plaintext;
    private Namespace namespace;
    private String encryptedBlob;

    @Setup(Level.Trial)
    public void setup() {
        SecureRandom random = new SecureRandom();
        dek = new byte[32]; // AES-256 key
        random.nextBytes(dek);

        plaintext = "sensitive-field-value-12345".getBytes(StandardCharsets.UTF_8);
        namespace = Namespace.of("bench", "test", "User", "email");

        // Pre-encrypt for decrypt benchmark
        encryptedBlob = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_GCM, namespace, 1);
    }

    @Benchmark
    public String encryptField() {
        return CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_GCM, namespace, 1);
    }

    @Benchmark
    public byte[] decryptField() {
        return CryptoCodec.decrypt(dek, encryptedBlob);
    }
}
