package io.github.emmansun.lightcrypto;

import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SymmetricAlgorithmTest {

    @Test
    void allFourAlgorithmsExist() {
        SymmetricAlgorithm[] values = SymmetricAlgorithm.values();
        assertThat(values).hasSize(4);
        assertThat(values).containsExactly(
                SymmetricAlgorithm.AES_256_GCM,
                SymmetricAlgorithm.AES_256_CBC,
                SymmetricAlgorithm.SM4_GCM,
                SymmetricAlgorithm.SM4_CBC
        );
    }

    @Test
    void valueOfWorksForAllAlgorithms() {
        assertThat(SymmetricAlgorithm.valueOf("AES_256_GCM")).isEqualTo(SymmetricAlgorithm.AES_256_GCM);
        assertThat(SymmetricAlgorithm.valueOf("AES_256_CBC")).isEqualTo(SymmetricAlgorithm.AES_256_CBC);
        assertThat(SymmetricAlgorithm.valueOf("SM4_GCM")).isEqualTo(SymmetricAlgorithm.SM4_GCM);
        assertThat(SymmetricAlgorithm.valueOf("SM4_CBC")).isEqualTo(SymmetricAlgorithm.SM4_CBC);
    }
}
