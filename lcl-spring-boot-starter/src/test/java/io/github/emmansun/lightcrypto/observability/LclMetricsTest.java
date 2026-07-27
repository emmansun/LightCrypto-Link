package io.github.emmansun.lightcrypto.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LclMetricsTest {

    @Test
    void recordsTimersWithFallbackTagsWhenInputsAreNull() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LclMetrics metrics = new LclMetrics(registry, false);

        metrics.recordEncryptDuration(null, null, 150);
        metrics.recordDecryptDuration(null, null, 250);
        metrics.recordBlindIndexDuration(null, 350);
        metrics.recordKeyVaultLoadDuration(null, 450);
        metrics.recordRotationDuration(null, 550);

        Timer encrypt = registry.find("lcl.crypto.encrypt.duration")
                .tag("algorithm", "unknown")
                .tag("namespace", "unknown")
                .timer();
        Timer decrypt = registry.find("lcl.crypto.decrypt.duration")
                .tag("algorithm", "unknown")
                .tag("namespace", "unknown")
                .timer();
        Timer blindIndex = registry.find("lcl.blind_index.compute.duration")
                .tag("namespace", "unknown")
                .timer();
        Timer keyVault = registry.find("lcl.keyvault.load.duration")
                .tag("namespace", "unknown")
                .timer();
        Timer rotation = registry.find("lcl.rotation.duration")
                .tag("namespace", "unknown")
                .timer();

        assertThat(encrypt).isNotNull();
        assertThat(decrypt).isNotNull();
        assertThat(blindIndex).isNotNull();
        assertThat(keyVault).isNotNull();
        assertThat(rotation).isNotNull();

        assertThat(encrypt.count()).isEqualTo(1L);
        assertThat(decrypt.count()).isEqualTo(1L);
        assertThat(blindIndex.count()).isEqualTo(1L);
        assertThat(keyVault.count()).isEqualTo(1L);
        assertThat(rotation.count()).isEqualTo(1L);

        assertThat(encrypt.totalTime(TimeUnit.MICROSECONDS)).isGreaterThanOrEqualTo(150d);
        assertThat(decrypt.totalTime(TimeUnit.MICROSECONDS)).isGreaterThanOrEqualTo(250d);
        assertThat(blindIndex.totalTime(TimeUnit.MICROSECONDS)).isGreaterThanOrEqualTo(350d);
        assertThat(keyVault.totalTime(TimeUnit.MICROSECONDS)).isGreaterThanOrEqualTo(450d);
        assertThat(rotation.totalTime(TimeUnit.MICROSECONDS)).isGreaterThanOrEqualTo(550d);
    }

    @Test
    void recordsCountersWithExpectedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LclMetrics metrics = new LclMetrics(registry, false);

        metrics.incrementEncryptCount("AES_256_GCM", "success");
        metrics.incrementDecryptCount(null, "failure");
        metrics.incrementRotationCount("success");

        Counter encrypt = registry.find("lcl.crypto.encrypt.total")
                .tag("algorithm", "AES_256_GCM")
                .tag("result", "success")
                .counter();
        Counter decrypt = registry.find("lcl.crypto.decrypt.total")
                .tag("algorithm", "unknown")
                .tag("result", "failure")
                .counter();
        Counter rotation = registry.find("lcl.rotation.total")
                .tag("result", "success")
                .counter();

        assertThat(encrypt).isNotNull();
        assertThat(decrypt).isNotNull();
        assertThat(rotation).isNotNull();
        assertThat(encrypt.count()).isEqualTo(1d);
        assertThat(decrypt.count()).isEqualTo(1d);
        assertThat(rotation.count()).isEqualTo(1d);
    }

    @Test
    void reusesProvidedRegistryAndSeparatesMetersByTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LclMetrics metrics = new LclMetrics(registry, true);

        metrics.recordEncryptDuration("AES_256_GCM", "tenant.a.User#email", 100);
        metrics.recordEncryptDuration("SM4_GCM", "tenant.b.User#phone", 200);

        Timer first = registry.find("lcl.crypto.encrypt.duration")
                .tag("algorithm", "AES_256_GCM")
                .tag("namespace", "tenant.a.User#email")
                .timer();
        Timer second = registry.find("lcl.crypto.encrypt.duration")
                .tag("algorithm", "SM4_GCM")
                .tag("namespace", "tenant.b.User#phone")
                .timer();

        assertThat(metrics.getRegistry()).isSameAs(registry);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first).isNotSameAs(second);
        assertThat(first.count()).isEqualTo(1L);
        assertThat(second.count()).isEqualTo(1L);
    }
}