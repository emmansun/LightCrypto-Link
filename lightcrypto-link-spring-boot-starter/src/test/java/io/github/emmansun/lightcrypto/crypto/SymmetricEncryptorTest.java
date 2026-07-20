package io.github.emmansun.lightcrypto.crypto;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.exception.CryptoException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SymmetricEncryptorTest {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @ParameterizedTest
    @EnumSource(value = SymmetricAlgorithm.class, names = "DEFAULT", mode = EnumSource.Mode.EXCLUDE)
    void encryptDecryptRoundtrip(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = createEncryptor(algorithm);
        byte[] key = generateKey(algorithm);
        byte[] plaintext = "Hello, World! 你好世界".getBytes();

        byte[] encrypted = encryptor.encrypt(key, plaintext);
        byte[] decrypted = encryptor.decrypt(key, encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @ParameterizedTest
    @EnumSource(value = SymmetricAlgorithm.class, names = "DEFAULT", mode = EnumSource.Mode.EXCLUDE)
    void encryptProducesUniqueCiphertext(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = createEncryptor(algorithm);
        byte[] key = generateKey(algorithm);
        byte[] plaintext = "Same plaintext".getBytes();

        byte[] encrypted1 = encryptor.encrypt(key, plaintext);
        byte[] encrypted2 = encryptor.encrypt(key, plaintext);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @ParameterizedTest
    @EnumSource(value = SymmetricAlgorithm.class, names = "DEFAULT", mode = EnumSource.Mode.EXCLUDE)
    void kcvConsistency(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = createEncryptor(algorithm);
        byte[] key = generateKey(algorithm);

        String kcv1 = encryptor.computeKcv(key);
        String kcv2 = encryptor.computeKcv(key);

        assertThat(kcv1).isEqualTo(kcv2);
        assertThat(kcv1).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(value = SymmetricAlgorithm.class, names = "DEFAULT", mode = EnumSource.Mode.EXCLUDE)
    void kcvDetectsKeyChange(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = createEncryptor(algorithm);
        byte[] key1 = generateKey(algorithm);
        byte[] key2 = generateKey(algorithm);

        String kcv1 = encryptor.computeKcv(key1);
        String kcv2 = encryptor.computeKcv(key2);

        assertThat(kcv1).isNotEqualTo(kcv2);
    }

    @ParameterizedTest
    @EnumSource(value = SymmetricAlgorithm.class, names = "DEFAULT", mode = EnumSource.Mode.EXCLUDE)
    void getAlgorithmReturnsCorrectValue(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = createEncryptor(algorithm);
        assertThat(encryptor.getAlgorithm()).isEqualTo(algorithm);
    }

    @ParameterizedTest
    @EnumSource(value = SymmetricAlgorithm.class, names = "DEFAULT", mode = EnumSource.Mode.EXCLUDE)
    void decryptWithWrongKeyThrowsCryptoException(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = createEncryptor(algorithm);
        byte[] key1 = generateKey(algorithm);
        byte[] key2 = generateKey(algorithm);
        byte[] plaintext = "secret data".getBytes();

        byte[] encrypted = encryptor.encrypt(key1, plaintext);

        assertThatThrownBy(() -> encryptor.decrypt(key2, encrypted))
                .isInstanceOf(CryptoException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SymmetricAlgorithm.class, names = "DEFAULT", mode = EnumSource.Mode.EXCLUDE)
    void decryptTamperedCiphertextThrowsCryptoException(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = createEncryptor(algorithm);
        byte[] key = generateKey(algorithm);
        byte[] plaintext = "integrity check".getBytes();

        byte[] encrypted = encryptor.encrypt(key, plaintext);
        // Flip a byte in the ciphertext portion (after IV)
        byte[] tampered = Arrays.copyOf(encrypted, encrypted.length);
        tampered[tampered.length - 1] ^= 0xFF;

        assertThatThrownBy(() -> encryptor.decrypt(key, tampered))
                .isInstanceOf(CryptoException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SymmetricAlgorithm.class, names = "DEFAULT", mode = EnumSource.Mode.EXCLUDE)
    void decryptTruncatedDataThrowsCryptoException(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = createEncryptor(algorithm);
        byte[] key = generateKey(algorithm);
        byte[] plaintext = "some data to truncate".getBytes();

        byte[] encrypted = encryptor.encrypt(key, plaintext);
        // Truncate to just IV + 1 byte
        byte[] truncated = Arrays.copyOf(encrypted, 13);

        assertThatThrownBy(() -> encryptor.decrypt(key, truncated))
                .isInstanceOf(CryptoException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SymmetricAlgorithm.class, names = "DEFAULT", mode = EnumSource.Mode.EXCLUDE)
    void encryptEmptyPlaintextRoundtrips(SymmetricAlgorithm algorithm) {
        SymmetricEncryptor encryptor = createEncryptor(algorithm);
        byte[] key = generateKey(algorithm);
        byte[] plaintext = new byte[0];

        byte[] encrypted = encryptor.encrypt(key, plaintext);
        byte[] decrypted = encryptor.decrypt(key, encrypted);

        assertThat(decrypted).isEmpty();
    }

    private SymmetricEncryptor createEncryptor(SymmetricAlgorithm algorithm) {
        return switch (algorithm) {
            case AES_256_GCM -> new AesGcmEncryptor();
            case AES_256_CBC -> new AesCbcEncryptor();
            case SM4_GCM -> new Sm4GcmEncryptor();
            case SM4_CBC -> new Sm4CbcEncryptor();
            case DEFAULT -> throw new IllegalArgumentException("DEFAULT should not reach encryptor");
        };
    }

    private byte[] generateKey(SymmetricAlgorithm algorithm) {
        // SM4 uses 16-byte keys, but our encryptors take 32-byte DEK and derive 16-byte key
        // So we always generate 32-byte keys here for consistency
        byte[] key = new byte[32];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }
}
