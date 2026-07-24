package io.github.emmansun.lightcrypto.benchmark;

import io.github.emmansun.lightcrypto.core.CryptoCodec;
import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for single-field SM4-GCM encryption and decryption.
 *
 * <p>Requires Bouncy Castle provider registration (done in setup).
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
public class Sm4GcmBenchmark {

    private byte[] dek;
    private byte[] plaintext;
    private Namespace namespace;
    private String encryptedBlob;

    @Setup(Level.Trial)
    public void setup() {
        // Ensure Bouncy Castle provider is registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        SecureRandom random = new SecureRandom();
        dek = new byte[16]; // SM4 key (128-bit)
        random.nextBytes(dek);

        plaintext = "sensitive-field-value-12345".getBytes(StandardCharsets.UTF_8);
        namespace = Namespace.of("bench", "test", "User", "phone");

        // Pre-encrypt for decrypt benchmark
        encryptedBlob = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.SM4_GCM, namespace, 1);
    }

    @Benchmark
    public String encryptField() {
        return CryptoCodec.encrypt(dek, plaintext, AlgorithmId.SM4_GCM, namespace, 1);
    }

    @Benchmark
    public byte[] decryptField() {
        return CryptoCodec.decrypt(dek, encryptedBlob);
    }
}
