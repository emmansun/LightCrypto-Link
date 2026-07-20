package io.github.emmansun.lightcrypto.core;

import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.core.namespace.Namespace;
import io.github.emmansun.lightcrypto.exception.CryptoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoCodecTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private byte[] generateKey(AlgorithmId alg) {
        byte[] key = new byte[alg.keyLength()];
        RANDOM.nextBytes(key);
        return key;
    }

    @ParameterizedTest
    @EnumSource(AlgorithmId.class)
    void encryptDecryptRoundtrip(AlgorithmId alg) {
        byte[] dek = generateKey(alg);
        byte[] plaintext = "Hello, Light Crypto Link!".getBytes(StandardCharsets.UTF_8);
        Namespace ns = Namespace.parse("default.default.User#email");

        String blob = CryptoCodec.encrypt(dek, plaintext, alg, ns, 1);
        byte[] decrypted = CryptoCodec.decrypt(dek, blob);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @ParameterizedTest
    @EnumSource(AlgorithmId.class)
    void encryptProducesUniqueCiphertext(AlgorithmId alg) {
        byte[] dek = generateKey(alg);
        byte[] plaintext = "same plaintext".getBytes(StandardCharsets.UTF_8);
        Namespace ns = Namespace.parse("User#field");

        String blob1 = CryptoCodec.encrypt(dek, plaintext, alg, ns, 1);
        String blob2 = CryptoCodec.encrypt(dek, plaintext, alg, ns, 1);

        assertThat(blob1).isNotEqualTo(blob2); // different IV
        assertThat(CryptoCodec.decrypt(dek, blob1)).isEqualTo(plaintext);
        assertThat(CryptoCodec.decrypt(dek, blob2)).isEqualTo(plaintext);
    }

    @Test
    void outputIsBase64UrlNoPadding() {
        byte[] dek = generateKey(AlgorithmId.AES_256_GCM);
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);

        String blob = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_GCM, "User#email", 1);

        assertThat(blob).matches("[A-Za-z0-9_-]+");
        assertThat(blob).doesNotContain("=");
    }

    @Test
    void shorthandNamespaceExpandsInBlob() {
        byte[] dek = generateKey(AlgorithmId.AES_256_GCM);
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);

        String blob = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_GCM, "User#email", 1);
        byte[] decrypted = CryptoCodec.decrypt(dek, blob);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void tamperedCiphertextDetectedByGcm() {
        byte[] dek = generateKey(AlgorithmId.AES_256_GCM);
        byte[] plaintext = "sensitive data".getBytes(StandardCharsets.UTF_8);

        String blob = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_GCM, "User#ssn", 1);

        // Tamper with the blob (decode, flip a byte, re-encode)
        byte[] raw = java.util.Base64.getUrlDecoder().decode(blob);
        raw[raw.length - 1] ^= 0xFF; // flip last byte (part of GCM tag)
        String tampered = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        assertThatThrownBy(() -> CryptoCodec.decrypt(dek, tampered))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void wrongKeyFailsDecryption() {
        byte[] dek1 = generateKey(AlgorithmId.AES_256_GCM);
        byte[] dek2 = generateKey(AlgorithmId.AES_256_GCM);
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        String blob = CryptoCodec.encrypt(dek1, plaintext, AlgorithmId.AES_256_GCM, "User#email", 1);

        assertThatThrownBy(() -> CryptoCodec.decrypt(dek2, blob))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void multiTenantIsolation() {
        byte[] dek = generateKey(AlgorithmId.AES_256_GCM);
        byte[] plaintext = "shared data".getBytes(StandardCharsets.UTF_8);

        String blobA = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_GCM, "tenantA.app.User#email", 1);
        String blobB = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_GCM, "tenantB.app.User#email", 1);

        // Both decrypt correctly with same key
        assertThat(CryptoCodec.decrypt(dek, blobA)).isEqualTo(plaintext);
        assertThat(CryptoCodec.decrypt(dek, blobB)).isEqualTo(plaintext);
        // But blobs are different (different namespace in AAD)
        assertThat(blobA).isNotEqualTo(blobB);
    }

    @Test
    void emptyPlaintextRoundtrip() {
        byte[] dek = generateKey(AlgorithmId.AES_256_GCM);
        byte[] plaintext = new byte[0];

        String blob = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_GCM, "User#empty", 1);
        byte[] decrypted = CryptoCodec.decrypt(dek, blob);

        assertThat(decrypted).isEmpty();
    }

    @Test
    void largePlaintextRoundtrip() {
        byte[] dek = generateKey(AlgorithmId.AES_256_CBC);
        byte[] plaintext = new byte[100_000];
        RANDOM.nextBytes(plaintext);

        String blob = CryptoCodec.encrypt(dek, plaintext, AlgorithmId.AES_256_CBC, "User#large", 1);
        byte[] decrypted = CryptoCodec.decrypt(dek, blob);

        assertThat(decrypted).isEqualTo(plaintext);
    }
}
