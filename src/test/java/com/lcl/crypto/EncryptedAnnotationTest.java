package com.lcl.crypto;

import com.lcl.crypto.annotation.Encrypted;
import com.lcl.crypto.annotation.SymmetricAlgorithm;
import com.lcl.crypto.testmodel.TestUser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;

/**
 * 2.8 Test: @Encrypted annotation defaults
 */
class EncryptedAnnotationTest {

    @Test
    void annotationDefaults() throws NoSuchFieldException {
        Field phoneField = TestUser.class.getDeclaredField("phone");
        Encrypted encrypted = phoneField.getAnnotation(Encrypted.class);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted.algorithm()).isEqualTo(SymmetricAlgorithm.AES_256_GCM);
        assertThat(encrypted.blindIndex()).isTrue(); // phone has blindIndex=true
    }

    @Test
    void defaultBlindIndexIsFalse() throws NoSuchFieldException {
        // TestEmployee.age has @Encrypted without blindIndex -> default false
        Class<?> clazz = com.lcl.crypto.testmodel.TestEmployee.class;
        Field ageField = clazz.getDeclaredField("age");
        Encrypted encrypted = ageField.getAnnotation(Encrypted.class);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted.blindIndex()).isFalse();
        assertThat(encrypted.fieldName()).isEqualTo("");
        assertThat(encrypted.algorithm()).isEqualTo(SymmetricAlgorithm.AES_256_GCM);
    }
}
