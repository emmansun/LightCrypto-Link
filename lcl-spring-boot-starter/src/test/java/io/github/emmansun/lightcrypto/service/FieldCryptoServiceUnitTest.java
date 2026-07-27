package io.github.emmansun.lightcrypto.service;

import io.github.emmansun.lightcrypto.config.CryptographyProperties;
import io.github.emmansun.lightcrypto.config.TenantProperties;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCryptoServiceUnitTest {

    @Test
    void decryptDocumentRejectsNullInputs() {
        FieldCryptoService service = createServiceWithEmptyMetadata();

        assertThatThrownBy(() -> service.decryptDocument(null, DemoEntity.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawDocument must not be null");

        assertThatThrownBy(() -> service.decryptDocument(new HashMap<>(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entityClass must not be null");
    }

    @Test
    void decryptDocumentReturnsInputWhenNoEncryptedFields() {
        FieldCryptoService service = createServiceWithEmptyMetadata();

        Map<String, Object> raw = new HashMap<>();
        raw.put("name", "alice");
        Object out = service.decryptDocument(raw, DemoEntity.class);

        assertThat(out).isSameAs(raw);
    }

    private static FieldCryptoService createServiceWithEmptyMetadata() {
        EntityMetadataCache metadataCache = new EntityMetadataCache(
            new CryptographyProperties(),
            new TenantProperties());

        return new FieldCryptoService(
                metadataCache,
                new TypeDeserializer(),
            null,
            null,
            null,
            null);
    }

    private static final class DemoEntity {
        private DemoEntity() {
        }
    }
}
