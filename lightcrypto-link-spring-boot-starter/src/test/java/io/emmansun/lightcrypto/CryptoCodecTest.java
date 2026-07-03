package io.emmansun.lightcrypto;

import io.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.emmansun.lightcrypto.exception.CryptoException;
import io.emmansun.lightcrypto.service.CryptoCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @ParameterizedTest
    @EnumSource(SymmetricAlgorithm.class)
    void encryptDecryptRoundtripPerAlgorithm(SymmetricAlgorithm algorithm) {
        byte[] plaintext = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = codec.encrypt(TEST_DEK, plaintext, algorithm);
        byte[] decrypted = codec.decrypt(TEST_DEK, encrypted, algorithm);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @ParameterizedTest
    @EnumSource(SymmetricAlgorithm.class)
    void kcvPerAlgorithm(SymmetricAlgorithm algorithm) {
        String kcv1 = codec.computeKcv(TEST_DEK, algorithm);
        String kcv2 = codec.computeKcv(TEST_DEK, algorithm);
        assertThat(kcv1).isEqualTo(kcv2);
        assertThat(kcv1).isNotBlank();
    }

    @Test
    void kcvVariesByAlgorithm() {
        String kcvGcm = codec.computeKcv(TEST_DEK, SymmetricAlgorithm.AES_256_GCM);
        String kcvCbc = codec.computeKcv(TEST_DEK, SymmetricAlgorithm.AES_256_CBC);
        String kcvSm4Gcm = codec.computeKcv(TEST_DEK, SymmetricAlgorithm.SM4_GCM);
        String kcvSm4Cbc = codec.computeKcv(TEST_DEK, SymmetricAlgorithm.SM4_CBC);

        assertThat(kcvGcm).isNotEqualTo(kcvCbc);
        assertThat(kcvGcm).isNotEqualTo(kcvSm4Gcm);
        assertThat(kcvGcm).isNotEqualTo(kcvSm4Cbc);
        assertThat(kcvCbc).isNotEqualTo(kcvSm4Gcm);
        assertThat(kcvSm4Gcm).isNotEqualTo(kcvSm4Cbc);
    }

    @Test
    void algorithmMismatchThrowsException() {
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        // Encrypt with AES-256-GCM, try to decrypt with AES-256-CBC
        byte[] encrypted = codec.encrypt(TEST_DEK, plaintext, SymmetricAlgorithm.AES_256_GCM);
        assertThatThrownBy(() -> codec.decrypt(TEST_DEK, encrypted, SymmetricAlgorithm.AES_256_CBC))
                .isInstanceOf(CryptoException.class);

        // Encrypt with SM4-GCM, try to decrypt with SM4-CBC
        byte[] encryptedSm4 = codec.encrypt(TEST_DEK, plaintext, SymmetricAlgorithm.SM4_GCM);
        assertThatThrownBy(() -> codec.decrypt(TEST_DEK, encryptedSm4, SymmetricAlgorithm.SM4_CBC))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void deprecatedMethodsUseDefaultAesGcm() {
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);

        // Old methods should work the same as AES-256-GCM
        @SuppressWarnings("deprecation")
        byte[] encrypted = codec.encrypt(TEST_DEK, plaintext);
        @SuppressWarnings("deprecation")
        byte[] decrypted = codec.decrypt(TEST_DEK, encrypted);
        assertThat(decrypted).isEqualTo(plaintext);

        @SuppressWarnings("deprecation")
        String kcv = codec.computeKcv(TEST_DEK);
        String kcvExplicit = codec.computeKcv(TEST_DEK, SymmetricAlgorithm.AES_256_GCM);
        assertThat(kcv).isEqualTo(kcvExplicit);
    }
}
