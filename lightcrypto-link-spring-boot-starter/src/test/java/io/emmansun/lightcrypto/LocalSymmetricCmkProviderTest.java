package io.emmansun.lightcrypto;

import io.emmansun.lightcrypto.model.WrappedKey;
import io.emmansun.lightcrypto.provider.CmkProvider;
import io.emmansun.lightcrypto.provider.LocalSymmetricCmkProvider;
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
    void wrappedKeyAlgorithmReturnsAesGcm() {
        CmkProvider provider = createTestCmkProvider();
        WrappedKey wrapped = provider.wrap(generateRandomKey());

        assertThat(wrapped.algorithm()).isEqualTo("AES-256-GCM");
    }
}
