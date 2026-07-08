package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import io.github.emmansun.lightcrypto.config.CryptoProperties;
import io.github.emmansun.lightcrypto.exception.UnsupportedTypeException;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.model.PathSegmentType;
import io.github.emmansun.lightcrypto.testmodel.TestArticle;
import io.github.emmansun.lightcrypto.testmodel.TestCircularRefA;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestPlainEntity;
import io.github.emmansun.lightcrypto.testmodel.TestUnsupportedEntity;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithAddresses;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithExcludedNested;
import io.github.emmansun.lightcrypto.testmodel.TestUserWithTooDeepNesting;
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
    void hasEncryptedFieldsReturnsTrueWhenEncryptedFieldExists() {
        assertThat(cache.hasEncryptedFields(TestUser.class)).isTrue();
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

    @Test
    void pojoFieldElementModeRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestPojoFieldWithElementMode.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("mode=ELEMENT")
                .hasMessageContaining("POJO fields");
    }

    @Test
    void circularReferenceIsRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestCircularRefA.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Circular reference detected");
    }

    @Test
    void tooDeepNestingIsRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestUserWithTooDeepNesting.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum recursion depth");
    }

    @Test
    void dbRefFieldIsExcludedWhileOtherNestedFieldsAreScanned() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestUserWithExcludedNested.class);

        assertThat(fields).hasSize(2);
        assertThat(fields.stream().map(EncryptedFieldMetadata::bsonFieldName).toList())
                .containsExactly("addresses.zipCode", "addressMap.zipCode");

        EncryptedFieldMetadata listMeta = fields.stream()
                .filter(f -> "addresses.zipCode".equals(f.bsonFieldName()))
                .findFirst()
                .orElseThrow();
        assertThat(listMeta.pathTypes()).containsExactly(PathSegmentType.LIST_ITER, PathSegmentType.FIELD);

        EncryptedFieldMetadata mapMeta = fields.stream()
                .filter(f -> "addressMap.zipCode".equals(f.bsonFieldName()))
                .findFirst()
                .orElseThrow();
        assertThat(mapMeta.pathTypes()).containsExactly(PathSegmentType.MAP_ITER, PathSegmentType.FIELD);
    }

    @Test
    void returnedEncryptedFieldsListIsImmutable() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestUser.class);

        assertThatThrownBy(() -> fields.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rawCollectionFieldWithoutGenericTypeIsRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestRawCollectionWithoutGeneric.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("must declare a generic element type");
    }

    @Test
    void rawMapFieldWithoutGenericTypeIsRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestRawMapWithoutGeneric.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("must declare generic key/value types");
    }

    @Test
    void mapWithNonStringKeyTypeIsRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestMapWithNonStringKey.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("must use String keys");
    }

    @Test
    void scalarWholeModeFallsBackToFieldLevelEncryption() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestScalarWholeMode.class);

        assertThat(fields).hasSize(1);
        EncryptedFieldMetadata meta = fields.get(0);
        assertThat(meta.wholeObject()).isFalse();
        assertThat(meta.pathTypes()).containsExactly(PathSegmentType.FIELD);
    }

    @Test
    void customFieldNameIsUsedForBlindIndexFieldName() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestCustomFieldNameEntity.class);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).blindIndexFieldName()).isEqualTo("phone_cipher");
    }

    @Test
    void transientEncryptedFieldIsExcluded() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestWithTransientEncryptedField.class);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).bsonFieldName()).isEqualTo("phone");
    }

    @Test
    void collectionWithUnsupportedElementTypeIsRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestCollectionWithUnsupportedElementType.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("unsupported collection element type");
    }

    @Test
    void mapWithUnsupportedValueTypeIsRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestMapWithUnsupportedValueType.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("unsupported map value type");
    }

    @Test
    void collectionWithParameterizedElementTypeIsRejected() {
        assertThatThrownBy(() -> cache.getEncryptedFields(TestCollectionWithParameterizedElementType.class))
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("unsupported collection element type");
    }

    @Test
    void defaultAlgorithmComesFromCryptoProperties() {
        CryptoProperties props = new CryptoProperties();
        props.setAlgorithm(SymmetricAlgorithm.SM4_GCM);
        EntityMetadataCache customCache = new EntityMetadataCache(props);

        List<EncryptedFieldMetadata> fields = customCache.getEncryptedFields(TestUser.class);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).algorithm()).isEqualTo(SymmetricAlgorithm.SM4_GCM);
    }

    @Test
    void explicitAlgorithmOverridesGlobalDefault() {
        CryptoProperties props = new CryptoProperties();
        props.setAlgorithm(SymmetricAlgorithm.SM4_GCM);
        EntityMetadataCache customCache = new EntityMetadataCache(props);

        List<EncryptedFieldMetadata> fields = customCache.getEncryptedFields(TestExplicitAlgorithmEntity.class);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).algorithm()).isEqualTo(SymmetricAlgorithm.AES_256_CBC);
    }

    @Test
    void inheritedEncryptedFieldIsDiscovered() {
        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestInheritedEncryptedEntity.class);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).bsonFieldName()).isEqualTo("phone");
    }

    static class TestPojoFieldWithElementMode {
        @Encrypted(mode = io.github.emmansun.lightcrypto.annotation.EncryptionMode.ELEMENT)
        private TestUserWithWholeAddress.Address address;
    }

    @SuppressWarnings("rawtypes")
    static class TestRawCollectionWithoutGeneric {
        @Encrypted
        private List tags;
    }

    @SuppressWarnings("rawtypes")
    static class TestRawMapWithoutGeneric {
        @Encrypted
        private java.util.Map settings;
    }

    static class TestMapWithNonStringKey {
        @Encrypted
        private java.util.Map<Integer, String> settings;
    }

    static class TestScalarWholeMode {
        @Encrypted(mode = io.github.emmansun.lightcrypto.annotation.EncryptionMode.WHOLE)
        private String phone;
    }

    static class TestCustomFieldNameEntity {
        @Encrypted(fieldName = "phone_cipher")
        private String phone;
    }

    static class TestWithTransientEncryptedField {
        @org.springframework.data.annotation.Transient
        @Encrypted
        private String ignored;

        @Encrypted
        private String phone;
    }

    static class TestCollectionWithUnsupportedElementType {
        @Encrypted
        private List<Object> values;
    }

    static class TestMapWithUnsupportedValueType {
        @Encrypted
        private java.util.Map<String, Object> values;
    }

    static class TestCollectionWithParameterizedElementType {
        @Encrypted
        private List<List<String>> values;
    }

    static class TestExplicitAlgorithmEntity {
        @Encrypted(algorithm = SymmetricAlgorithm.AES_256_CBC)
        private String phone;
    }

    static class TestBaseEncryptedEntity {
        @Encrypted
        private String phone;
    }

    static class TestInheritedEncryptedEntity extends TestBaseEncryptedEntity {
    }
}
