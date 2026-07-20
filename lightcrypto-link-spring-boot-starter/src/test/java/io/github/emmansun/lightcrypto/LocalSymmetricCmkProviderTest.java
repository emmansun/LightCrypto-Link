package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.exception.CryptoException;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.provider.CmkProvider;
import io.github.emmansun.lightcrypto.provider.LocalSymmetricCmkProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 3.3-3.6 Tests: CmkProvider SPI
 */
class LocalSymmetricCmkProviderTest extends LclTestBase {

    @Test
    void wrapUnwrapRoundtrip() {
        CmkProvider provider = createTestCmkProvider();
        byte[] originalKey = generateRandomKey();

        WrappedKey wrapped = provider.wrap(originalKey);
        byte[] unwrapped = provider.unwrap(wrapped);

        assertThat(unwrapped).isEqualTo(originalKey);
    }

    @Test
    void wrapSameKeyTwiceProducesDifferentCiphertexts() {
        CmkProvider provider = createTestCmkProvider();
        byte[] key = generateRandomKey();

        WrappedKey w1 = provider.wrap(key);
        WrappedKey w2 = provider.wrap(key);

        assertThat(w1.ciphertext()).isNotEqualTo(w2.ciphertext());
    }

    @Test
    void invalidCmkLengthThrowsException() {
        assertThatThrownBy(() -> new LocalSymmetricCmkProvider(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void nullCmkThrowsException() {
        assertThatThrownBy(() -> new LocalSymmetricCmkProvider(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void wrappedKeyAlgorithmReturnsAesGcm() {
        CmkProvider provider = createTestCmkProvider();
        WrappedKey wrapped = provider.wrap(generateRandomKey());

        assertThat(wrapped.algorithm()).isEqualTo("AES-256-GCM");
    }

    @Test
    void unwrapNullThrowsIllegalArgument() {
        CmkProvider provider = createTestCmkProvider();
        assertThatThrownBy(() -> provider.unwrap(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void unwrapUnsupportedAlgorithmThrows() {
        CmkProvider provider = createTestCmkProvider();
        WrappedKey bad = new WrappedKey(new byte[48], "RSA-OAEP");
        assertThatThrownBy(() -> provider.unwrap(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported algorithm");
    }

    @Test
    void unwrapCorruptedCiphertextThrowsCryptoException() {
        CmkProvider provider = createTestCmkProvider();
        byte[] corrupted = new byte[48]; // IV(12) + garbage
        WrappedKey bad = new WrappedKey(corrupted, "AES-256-GCM");
        assertThatThrownBy(() -> provider.unwrap(bad))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("Failed to unwrap key");
    }

    @Test
    void supportsAlgorithmReturnsFalseForNonAesGcm() {
        CmkProvider provider = createTestCmkProvider();
        assertThat(provider.supportsAlgorithm("AES-256-GCM")).isTrue();
        assertThat(provider.supportsAlgorithm("SM4-GCM")).isFalse();
        assertThat(provider.supportsAlgorithm("RSA-OAEP")).isFalse();
    }

    @Test
    void mapAlgorithmReturnsInputUnchanged() {
        CmkProvider provider = createTestCmkProvider();
        assertThat(provider.mapAlgorithm("AES-256-GCM")).isEqualTo("AES-256-GCM");
    }

    @Test
    void providerIdIsLocalSymmetric() {
        CmkProvider provider = createTestCmkProvider();
        assertThat(provider.getProviderId()).isEqualTo("local-symmetric");
    }

    @Test
    void publicReferenceIsDeterministicAndPrefixed() {
        CmkProvider provider = createTestCmkProvider();
        String ref = provider.getPublicReference();
        assertThat(ref).startsWith("local-cmk-sha256:");
        assertThat(ref).hasSize("local-cmk-sha256:".length() + 16); // 8 bytes hex = 16 chars
    }

    @Test
    void differentCmkProducesDifferentPublicReference() {
        CmkProvider p1 = createTestCmkProvider();
        byte[] otherCmk = generateRandomKey();
        CmkProvider p2 = new LocalSymmetricCmkProvider(otherCmk);
        assertThat(p1.getPublicReference()).isNotEqualTo(p2.getPublicReference());
    }
}
