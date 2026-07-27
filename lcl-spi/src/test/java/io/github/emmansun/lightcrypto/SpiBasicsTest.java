package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.exception.CryptoException;
import io.github.emmansun.lightcrypto.exception.OptimisticLockException;
import io.github.emmansun.lightcrypto.model.GeneratedKey;
import io.github.emmansun.lightcrypto.model.LclAlgorithms;
import io.github.emmansun.lightcrypto.model.WrappedKey;
import io.github.emmansun.lightcrypto.spi.VaultDocument;
import io.github.emmansun.lightcrypto.util.CryptoUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpiBasicsTest {

    @Test
    void randomBytesGenerationUsesRequestedLength() {
        byte[] bytes = CryptoUtils.generateRandomBytes(32);

        assertThat(bytes).hasSize(32);
        assertThat(CryptoUtils.getSecureRandom()).isNotNull();
    }

    @Test
    void wrappedKeyCopiesMetadataAndDefaultsToEmptyMap() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("kid", "v1-abcdef12");

        WrappedKey withMetadata = new WrappedKey(new byte[]{1, 2, 3}, LclAlgorithms.AES_256_GCM, mutable);
        mutable.put("kid", "changed");

        assertThat(withMetadata.metadata()).containsEntry("kid", "v1-abcdef12");
        assertThatThrownBy(() -> withMetadata.metadata().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);

        WrappedKey withoutMetadata = new WrappedKey(new byte[]{9}, LclAlgorithms.SM4_GCM);
        assertThat(withoutMetadata.metadata()).isEmpty();
    }

    @Test
    void generatedKeyAndVaultDocumentRetainValues() {
        WrappedKey wrapped = new WrappedKey(new byte[]{7, 8}, LclAlgorithms.RSA_OAEP_256);
        GeneratedKey generatedKey = new GeneratedKey(new byte[]{1, 2, 3, 4}, wrapped);

        Instant now = Instant.now();
        VaultDocument.KeyEntry keyEntry = new VaultDocument.KeyEntry(
                "v1-abcdef12",
                VaultDocument.KeyStatus.ACTIVE,
                new byte[]{10},
                new byte[]{11},
                LclAlgorithms.AES_256_GCM,
                "abc",
                "def",
                "binding",
                now
        );
        VaultDocument vault = new VaultDocument(
                "default.default.User#phone",
                List.of(keyEntry),
                "v1-abcdef12",
                1L,
                "local",
                "local-cmk",
                now,
                now
        );

        assertThat(generatedKey.rawKey()).hasSize(4);
        assertThat(generatedKey.wrappedKey().algorithm()).isEqualTo(LclAlgorithms.RSA_OAEP_256);
        assertThat(vault.namespace()).isEqualTo("default.default.User#phone");
        assertThat(vault.keys()).hasSize(1);
        assertThat(vault.keys().get(0).status()).isEqualTo(VaultDocument.KeyStatus.ACTIVE);
    }

    @Test
    void exceptionsPreserveMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");

        CryptoException base = new CryptoException("crypto-failed", cause);
        OptimisticLockException optimistic = new OptimisticLockException("conflict", cause);

        assertThat(base).hasMessage("crypto-failed").hasCause(cause);
        assertThat(optimistic).hasMessage("conflict").hasCause(cause);
    }
}
