package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.exception.UnsupportedTypeException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.model.PathSegmentType;
import io.github.emmansun.lightcrypto.testmodel.TestArticle;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestPlainEntity;
import io.github.emmansun.lightcrypto.testmodel.TestUnsupportedEntity;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithAddresses;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithWholeAddress;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithWholeAddresses;
import io.github.emmansun.lightcrypto.testmodel.TestWholeSimpleCollections;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 7.5-7.9 Tests: EntityMetadataCache
 */
class EntityMetadataCacheTest {

    private final EntityMetadataCache cache = new EntityMetadataCache(new CryptoProperties());

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

    @Test
    void encryptedCollectionAndMapHaveIterPathTypes() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestArticle.class);
        assertThat(fields).hasSize(2);

        EncryptedFieldMetadata tagsMeta = fields.stream()
                .filter(f -> f.bsonFieldName().equals("tags"))
                .findFirst()
                .orElseThrow();
        assertThat(tagsMeta.pathTypes()).containsExactly(PathSegmentType.LIST_ITER);
        assertThat(tagsMeta.javaType()).isEqualTo(String.class);
        assertThat(tagsMeta.blindIndex()).isTrue();

        EncryptedFieldMetadata settingsMeta = fields.stream()
                .filter(f -> f.bsonFieldName().equals("settings"))
                .findFirst()
                .orElseThrow();
        assertThat(settingsMeta.pathTypes()).containsExactly(PathSegmentType.MAP_ITER);
        assertThat(settingsMeta.javaType()).isEqualTo(String.class);
        assertThat(settingsMeta.blindIndex()).isFalse();
    }

    @Test
    void pojoCollectionRecursiveScanProducesListIterFieldPath() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestUserWithAddresses.class);
        assertThat(fields).hasSize(1);

        EncryptedFieldMetadata meta = fields.get(0);
        assertThat(meta.bsonFieldName()).isEqualTo("addresses.street");
        assertThat(meta.pathTypes()).containsExactly(PathSegmentType.LIST_ITER, PathSegmentType.FIELD);
        assertThat(meta.javaType()).isEqualTo(String.class);
    }

    @Test
    void wholeObjectPojoFieldHasDocPathAndFlag() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestUserWithWholeAddress.class);
        assertThat(fields).hasSize(1);

        EncryptedFieldMetadata meta = fields.get(0);
        assertThat(meta.bsonFieldName()).isEqualTo("address");
        assertThat(meta.pathTypes()).containsExactly(PathSegmentType.FIELD);
        assertThat(meta.wholeObject()).isTrue();
    }

    @Test
    void wholeObjectCollectionHasIterPathAndFlag() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestUserWithWholeAddresses.class);
        assertThat(fields).hasSize(1);

        EncryptedFieldMetadata meta = fields.get(0);
        assertThat(meta.bsonFieldName()).isEqualTo("addresses");
        assertThat(meta.pathTypes()).containsExactly(PathSegmentType.LIST_ITER);
        assertThat(meta.wholeObject()).isTrue();
    }

    @Test
    void wholeSimpleCollectionsCanUseWholeMode() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestWholeSimpleCollections.class);
        assertThat(fields).hasSize(2);

        EncryptedFieldMetadata tagsMeta = fields.stream()
                .filter(f -> f.bsonFieldName().equals("tags"))
                .findFirst()
                .orElseThrow();
        assertThat(tagsMeta.pathTypes()).containsExactly(PathSegmentType.LIST_ITER);
        assertThat(tagsMeta.wholeObject()).isTrue();

        EncryptedFieldMetadata settingsMeta = fields.stream()
                .filter(f -> f.bsonFieldName().equals("settings"))
                .findFirst()
                .orElseThrow();
        assertThat(settingsMeta.pathTypes()).containsExactly(PathSegmentType.MAP_ITER);
        assertThat(settingsMeta.wholeObject()).isTrue();
    }

    @Test
    void wholeObjectCollectionWithBlindIndexRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestWholeObjectCollectionWithBlindIndex.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("blindIndex=true");
    }

    @Test
    void wholeSimpleCollectionWithBlindIndexRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestWholeSimpleCollectionWithBlindIndex.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("blindIndex=true");
    }

    @Test
    void wholeObjectWithNestedEncryptedFieldsRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestWholeObjectWithNestedEncrypted.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("whole-object encryption");
    }

    static class TestWholeObjectCollectionWithBlindIndex {
        @Encrypted(blindIndex = true)
        private List<TestUserWithWholeAddresses.Address> addresses;
    }

    static class TestWholeObjectWithNestedEncrypted {
        @Encrypted
        private NestedAddress address;

        static class NestedAddress {
            @Encrypted
            private String street;
        }
    }

    static class TestWholeSimpleCollectionWithBlindIndex {
        @Encrypted(mode = io.github.emmansun.lightcrypto.annotation.EncryptionMode.WHOLE, blindIndex = true)
        private List<String> tags;
    }

    static class TestPojoCollectionWithElementMode {
        @Encrypted(mode = io.github.emmansun.lightcrypto.annotation.EncryptionMode.ELEMENT)
        private List<TestUserWithWholeAddresses.Address> addresses;
    }

    @Test
    void pojoCollectionElementModeRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestPojoCollectionWithElementMode.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("mode=ELEMENT")
                .hasMessageContaining("POJO collection/map values");
    }
}
