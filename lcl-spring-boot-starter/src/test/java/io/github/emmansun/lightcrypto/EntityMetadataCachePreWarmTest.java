package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.config.CryptographyProperties;
import io.github.emmansun.lightcrypto.config.TenantProperties;
import io.github.emmansun.lightcrypto.listener.EntityMetadataCache;
import io.github.emmansun.lightcrypto.model.EncryptedFieldMetadata;
import io.github.emmansun.lightcrypto.testmodel.TestEmployee;
import io.github.emmansun.lightcrypto.testmodel.TestUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for EntityMetadataCache.preWarm() — verifies that cache pre-warming
 * at startup eliminates cold-start latency.
 */
class EntityMetadataCachePreWarmTest {

    private final EntityMetadataCache cache = new EntityMetadataCache(new CryptographyProperties(), new TenantProperties());

    @Test
    void preWarmPopulatesCacheForMultipleClasses() {
        cache.preWarm(TestUser.class, TestEmployee.class);

        // After pre-warming, subsequent calls should return the same cached instance
        List<EncryptedFieldMetadata> userFields1 = cache.getEncryptedFields(TestUser.class);
        List<EncryptedFieldMetadata> userFields2 = cache.getEncryptedFields(TestUser.class);
        assertThat(userFields1).isSameAs(userFields2);
        assertThat(userFields1).hasSize(1);

        List<EncryptedFieldMetadata> empFields1 = cache.getEncryptedFields(TestEmployee.class);
        List<EncryptedFieldMetadata> empFields2 = cache.getEncryptedFields(TestEmployee.class);
        assertThat(empFields1).isSameAs(empFields2);
        assertThat(empFields1).hasSize(2);
    }

    @Test
    void preWarmWithNoArgsIsNoOp() {
        assertThatNoException().isThrownBy(() -> cache.preWarm());
    }

    @Test
    void methodHandleGetterReadsFieldValue() throws Throwable {
        cache.preWarm(TestUser.class);

        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestUser.class);
        assertThat(fields).hasSize(1);

        EncryptedFieldMetadata phoneMeta = fields.get(0);
        assertThat(phoneMeta.fieldName()).isEqualTo("phone");

        // Verify MethodHandle getter can read the actual field value
        TestUser user = new TestUser();
        user.setPhone("13800138000");
        Object value = phoneMeta.getter().invoke(user);
        assertThat(value).isEqualTo("13800138000");
    }

    @Test
    void methodHandleGetterReturnsNullForUnsetField() throws Throwable {
        cache.preWarm(TestUser.class);

        EncryptedFieldMetadata phoneMeta = cache.getEncryptedFields(TestUser.class).get(0);
        TestUser user = new TestUser();
        Object value = phoneMeta.getter().invoke(user);
        assertThat(value).isNull();
    }

    @Test
    void methodHandleGetterWorksWithPrimitiveWrapperTypes() throws Throwable {
        cache.preWarm(TestEmployee.class);

        List<EncryptedFieldMetadata> fields = cache.getEncryptedFields(TestEmployee.class);
        EncryptedFieldMetadata ageMeta = fields.stream()
                .filter(f -> "age".equals(f.fieldName()))
                .findFirst()
                .orElseThrow();

        TestEmployee emp = new TestEmployee();
        emp.setAge(42);
        Object value = ageMeta.getter().invoke(emp);
        assertThat(value).isEqualTo(42);
    }
}
