package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SymmetricAlgorithmTest {

    @Test
    void allAlgorithmsExist() {
        SymmetricAlgorithm[] values = SymmetricAlgorithm.values();
        assertThat(values).hasSize(5);
        assertThat(values).containsExactly(
                SymmetricAlgorithm.DEFAULT,
                SymmetricAlgorithm.AES_256_GCM,
                SymmetricAlgorithm.AES_256_CBC,
                SymmetricAlgorithm.SM4_GCM,
                SymmetricAlgorithm.SM4_CBC
        );
    }

    @Test
    void valueOfWorksForAllAlgorithms() {
        assertThat(SymmetricAlgorithm.valueOf("DEFAULT")).isEqualTo(SymmetricAlgorithm.DEFAULT);
        assertThat(SymmetricAlgorithm.valueOf("AES_256_GCM")).isEqualTo(SymmetricAlgorithm.AES_256_GCM);
        assertThat(SymmetricAlgorithm.valueOf("AES_256_CBC")).isEqualTo(SymmetricAlgorithm.AES_256_CBC);
        assertThat(SymmetricAlgorithm.valueOf("SM4_GCM")).isEqualTo(SymmetricAlgorithm.SM4_GCM);
        assertThat(SymmetricAlgorithm.valueOf("SM4_CBC")).isEqualTo(SymmetricAlgorithm.SM4_CBC);
    }
}
