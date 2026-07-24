package io.github.emmansun.lightcrypto.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for DEK cache hit vs cold-path CMK unwrap simulation.
 *
 * <p>Cache hit target: p95 < 5µs (ConcurrentHashMap lookup).
 * Cold path: no hard target (simulates AES-256-GCM key unwrap).
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
public class DekCacheBenchmark {

    private ConcurrentHashMap<String, byte[]> dekCache;
    private String cacheKey;
    private byte[] cachedDek;

    // Cold path simulation: wrapped DEK + KEK (key encryption key)
    private byte[] wrappedDek;
    private byte[] kek;
    private byte[] coldIv;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        SecureRandom random = new SecureRandom();

        // Setup cache with a DEK
        dekCache = new ConcurrentHashMap<>();
        cacheKey = "bench.test.User#email";
        cachedDek = new byte[32];
        random.nextBytes(cachedDek);
        dekCache.put(cacheKey, cachedDek);

        // Setup cold path: wrap a DEK with KEK using AES-256-GCM
        kek = new byte[32];
        random.nextBytes(kek);

        byte[] dekToWrap = new byte[32];
        random.nextBytes(dekToWrap);

        coldIv = new byte[12];
        random.nextBytes(coldIv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(kek, "AES"),
                new GCMParameterSpec(128, coldIv));
        wrappedDek = cipher.doFinal(dekToWrap);
    }

    /**
     * Cache hit: simple ConcurrentHashMap lookup.
     */
    @Benchmark
    public byte[] cacheHit() {
        return dekCache.get(cacheKey);
    }

    /**
     * Cold path: unwrap DEK from wrapped form (simulates CMK decrypt).
     */
    @Benchmark
    public void coldPath(Blackhole bh) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(kek, "AES"),
                new GCMParameterSpec(128, coldIv));
        byte[] dek = cipher.doFinal(wrappedDek);
        bh.consume(dek);
    }
}
