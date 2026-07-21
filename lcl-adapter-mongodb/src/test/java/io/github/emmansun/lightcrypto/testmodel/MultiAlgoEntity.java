package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.annotation.SymmetricAlgorithm;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Test entity with multiple algorithm-encrypted fields.
 */
@Data
@Document
public class MultiAlgoEntity {
    @Id
    private String id;

    private String plainField;

    @Encrypted(algorithm = SymmetricAlgorithm.AES_256_GCM)
    private String aesGcmField;

    @Encrypted(algorithm = SymmetricAlgorithm.AES_256_CBC)
    private String aesCbcField;

    @Encrypted(algorithm = SymmetricAlgorithm.SM4_GCM)
    private String sm4GcmField;

    @Encrypted(algorithm = SymmetricAlgorithm.SM4_CBC)
    private String sm4CbcField;
}
