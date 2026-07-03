package com.lcl.crypto;

import com.lcl.crypto.exception.CryptoException;
import com.lcl.crypto.service.CryptoCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * 5.5-5.11 Tests: CryptoCodec
 */
class CryptoCodecTest extends LclTestBase {

    private final CryptoCodec codec = createTestCryptoCodec();

    @Test
    void encryptDecryptRoundtrip() {
        String[] inputs = {"", "hello", "a".repeat(1000)};
        for (String input : inputs) {
            byte[] plaintext = input.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = codec.encrypt(TEST_DEK, plaintext);
            byte[] decrypted = codec.decrypt(TEST_DEK, encrypted);
            assertThat(decrypted).isEqualTo(plaintext);
        }
    }

    @Test
    void encryptSamePlaintextTwiceProducesDifferentCiphertexts() {
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
        byte[] e1 = codec.encrypt(TEST_DEK, plaintext);
        byte[] e2 = codec.encrypt(TEST_DEK, plaintext);
        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void tamperedCiphertextThrowsException() {
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = codec.encrypt(TEST_DEK, plaintext);
        encrypted[encrypted.length - 1] ^= 0xFF; // flip a byte

        assertThatThrownBy(() -> codec.decrypt(TEST_DEK, encrypted))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void blindIndexIsDeterministic() {
        String h1 = codec.generateBlindIndex(TEST_HMAC_KEY, "phone", "13800138000".getBytes(StandardCharsets.UTF_8));
        String h2 = codec.generateBlindIndex(TEST_HMAC_KEY, "phone", "13800138000".getBytes(StandardCharsets.UTF_8));
        assertThat(h1).isEqualTo(h2);
        // Verify base64url output (43 chars for HMAC-SHA-256, only URL-safe chars)
        assertThat(h1).hasSize(43);
        assertThat(h1).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void blindIndexFieldIsolation() {
        String h1 = codec.generateBlindIndex(TEST_HMAC_KEY, "phone", "13800138000".getBytes(StandardCharsets.UTF_8));
        String h2 = codec.generateBlindIndex(TEST_HMAC_KEY, "mobile", "13800138000".getBytes(StandardCharsets.UTF_8));
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void blindIndexColonSeparatorPreventsCollision() {
        // Verify that "ab:cd" and "a:bcd" produce different HMAC values
        String h1 = codec.generateBlindIndex(TEST_HMAC_KEY, "ab", "cd".getBytes(StandardCharsets.UTF_8));
        String h2 = codec.generateBlindIndex(TEST_HMAC_KEY, "a", "bcd".getBytes(StandardCharsets.UTF_8));
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void kcvIsDeterministic() {
        String kcv1 = codec.computeKcv(TEST_DEK);
        String kcv2 = codec.computeKcv(TEST_DEK);
        assertThat(kcv1).isEqualTo(kcv2);

        // Different key produces different KCV
        byte[] otherKey = generateRandomKey();
        String kcv3 = codec.computeKcv(otherKey);
        assertThat(kcv1).isNotEqualTo(kcv3);
    }

    @Test
    void bindingIsDeterministicAndAsymmetric() {
        String b1 = codec.computeBinding(TEST_HMAC_KEY, TEST_DEK);
        String b2 = codec.computeBinding(TEST_HMAC_KEY, TEST_DEK);
        assertThat(b1).isEqualTo(b2);

        // Swapped keys produce different binding
        String b3 = codec.computeBinding(TEST_DEK, TEST_HMAC_KEY);
        assertThat(b1).isNotEqualTo(b3);
    }
}
