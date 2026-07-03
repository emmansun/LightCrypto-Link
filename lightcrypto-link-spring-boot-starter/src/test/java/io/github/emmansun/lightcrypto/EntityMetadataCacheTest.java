package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.exception.UnsupportedTypeException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestPlainEntity;
import io.github.emmansun.lightcrypto.testmodel.TestUnsupportedEntity;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 7.5-7.9 Tests: EntityMetadataCache
 */
class EntityMetadataCacheTest {

    private final EntityMetadataCache cache = new EntityMetadataCache();

    @Test
    void testUserHasOneEncryptedField() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestUser.class);
        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).fieldName()).isEqualTo("phone");
        assertThat(fields.get(0).blindIndex()).isTrue();
        assertThat(fields.get(0).javaType()).isEqualTo(String.class);
    }

    @Test
    void testEmployeeHasTwoEncryptedFields() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestEmployee.class);
        assertThat(fields).hasSize(2);
        assertThat(fields.stream().map(EncryptedFieldMetadata::fieldName).toList())
                .containsExactly("age", "birthDate");
    }

    @Test
    void testPlainEntityHasNoEncryptedFields() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestPlainEntity.class);
        assertThat(fields).isEmpty();
        assertThat(cache.hasEncryptedFields(TestPlainEntity.class)).isFalse();
    }

    @Test
    void cachedResultIsSameInstance() {
        List<EncryptedFieldMetadata> first = cache.getEncryptedFields(TestUser.class);
        List<EncryptedFieldMetadata> second = cache.getEncryptedFields(TestUser.class);
        assertThat(first).isSameAs(second);
    }

    @Test
    void unsupportedTypeThrowsAtScanTime() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestUnsupportedEntity.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("unsupportedField");
    }
}
