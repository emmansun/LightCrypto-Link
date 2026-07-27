package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.core.format.AlgorithmId;
import io.github.emmansun.lightcrypto.exception.DecryptionException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgrammaticCryptoServiceUnitTest {

    @Test
    void encryptValueRejectsInvalidArguments() {
        ProgrammaticCryptoService service = createService();

        assertThatThrownBy(() -> service.encryptValue(null, "default.default.Test#field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value must not be null");

        assertThatThrownBy(() -> service.encryptValue("v", (String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace must not be null");

        assertThatThrownBy(() -> service.encryptValue("v", "default.default.Test#field", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm must not be null");
    }

    @Test
    void decryptValueRejectsNullAndNonMap() {
        ProgrammaticCryptoService service = createService();

        assertThatThrownBy(() -> service.decryptValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encryptedSubDocument must not be null");

        assertThatThrownBy(() -> service.decryptValue("not-a-map"))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("must be a Map-like object");
    }

    @Test
    void decryptValueRejectsMissingRequiredFields() {
        ProgrammaticCryptoService service = createService();

        assertThatThrownBy(() -> service.decryptValue(Map.of("_e", 1, "c", "abc")))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("missing '_t'");

        assertThatThrownBy(() -> service.decryptValue(Map.of("_e", 1, "_t", "STR")))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("missing 'c'");
    }

    @Test
    private static ProgrammaticCryptoService createService() {
        return new ProgrammaticCryptoService(
                new TypeSerializer(),
                new TypeDeserializer(),
                                null,
                                null,
                                null);
    }
}
