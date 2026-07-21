package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import io.github.emmansun.lightcrypto.testmodel.MultiAlgoEntity;
import io.github.emmansun.lightcrypto.testmodel.MultiAlgoEntityRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for multi-algorithm encryption support.
 * Tests all 4 supported algorithms: AES-256-GCM, AES-256-CBC, SM4-GCM, SM4-CBC.
 */
@SpringBootTest(classes = IntTestApplication.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = MongoCiInitializer.class)
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiAlgorithmIntegrationTest {

    @Autowired
    private MultiAlgoEntityRepository entityRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private String collectionName;

    @BeforeEach
    void cleanCollection() {
        collectionName = mongoTemplate.getCollectionName(MultiAlgoEntity.class);
        mongoTemplate.dropCollection(MultiAlgoEntity.class);
    }

    @Test
    @Order(1)
    void saveAndReadBackAesGcm() {
        MultiAlgoEntity entity = new MultiAlgoEntity();
        entity.setPlainField("plain");
        entity.setAesGcmField("aes-gcm-value");

        entityRepository.save(entity);

        MultiAlgoEntity loaded = entityRepository.findById(entity.getId()).orElseThrow();
        assertThat(loaded.getAesGcmField()).isEqualTo("aes-gcm-value");
        assertThat(loaded.getPlainField()).isEqualTo("plain");
    }

    @Test
    @Order(2)
    void saveAndReadBackAesCbc() {
        MultiAlgoEntity entity = new MultiAlgoEntity();
        entity.setPlainField("plain");
        entity.setAesCbcField("aes-cbc-value");

        entityRepository.save(entity);

        MultiAlgoEntity loaded = entityRepository.findById(entity.getId()).orElseThrow();
        assertThat(loaded.getAesCbcField()).isEqualTo("aes-cbc-value");
        assertThat(loaded.getPlainField()).isEqualTo("plain");
    }

    @Test
    @Order(3)
    void saveAndReadBackSm4Gcm() {
        MultiAlgoEntity entity = new MultiAlgoEntity();
        entity.setPlainField("plain");
        entity.setSm4GcmField("sm4-gcm-value");

        entityRepository.save(entity);

        MultiAlgoEntity loaded = entityRepository.findById(entity.getId()).orElseThrow();
        assertThat(loaded.getSm4GcmField()).isEqualTo("sm4-gcm-value");
        assertThat(loaded.getPlainField()).isEqualTo("plain");
    }

    @Test
    @Order(4)
    void saveAndReadBackSm4Cbc() {
        MultiAlgoEntity entity = new MultiAlgoEntity();
        entity.setPlainField("plain");
        entity.setSm4CbcField("sm4-cbc-value");

        entityRepository.save(entity);

        MultiAlgoEntity loaded = entityRepository.findById(entity.getId()).orElseThrow();
        assertThat(loaded.getSm4CbcField()).isEqualTo("sm4-cbc-value");
        assertThat(loaded.getPlainField()).isEqualTo("plain");
    }

    @Test
    @Order(5)
    void mixedAlgorithmsInSameEntity() {
        MultiAlgoEntity entity = new MultiAlgoEntity();
        entity.setPlainField("plain");
        entity.setAesGcmField("aes-gcm");
        entity.setAesCbcField("aes-cbc");
        entity.setSm4GcmField("sm4-gcm");
        entity.setSm4CbcField("sm4-cbc");

        entityRepository.save(entity);

        MultiAlgoEntity loaded = entityRepository.findById(entity.getId()).orElseThrow();
        assertThat(loaded.getPlainField()).isEqualTo("plain");
        assertThat(loaded.getAesGcmField()).isEqualTo("aes-gcm");
        assertThat(loaded.getAesCbcField()).isEqualTo("aes-cbc");
        assertThat(loaded.getSm4GcmField()).isEqualTo("sm4-gcm");
        assertThat(loaded.getSm4CbcField()).isEqualTo("sm4-cbc");
    }

    @Test
    @Order(6)
    void subDocumentContainsWireFormatV1Blob() {
        MultiAlgoEntity entity = new MultiAlgoEntity();
        entity.setPlainField("plain");
        entity.setAesGcmField("aes-gcm");
        entity.setSm4GcmField("sm4-gcm");

        entityRepository.save(entity);

        // Read raw document from MongoDB using dynamic collection name and ObjectId
        org.bson.Document rawDoc = mongoTemplate.getDb().getCollection(collectionName)
                .find(new org.bson.Document("_id", new ObjectId(entity.getId()))).first();
        assertThat(rawDoc).isNotNull();

        // Wire Format V1: algorithm is embedded in the blob (byte offset 1)
        org.bson.Document aesGcmSubDoc = (org.bson.Document) rawDoc.get("aesGcmField");
        String aesGcmBlob = aesGcmSubDoc.getString("c");
        assertThat(aesGcmBlob).isNotNull();
        byte[] aesGcmBytes = java.util.Base64.getUrlDecoder().decode(aesGcmBlob);
        assertThat(aesGcmBytes[0]).isEqualTo((byte) 0x01); // version
        assertThat(aesGcmBytes[1]).isEqualTo((byte) 0x01); // AES_256_GCM

        org.bson.Document sm4GcmSubDoc = (org.bson.Document) rawDoc.get("sm4GcmField");
        String sm4GcmBlob = sm4GcmSubDoc.getString("c");
        assertThat(sm4GcmBlob).isNotNull();
        byte[] sm4GcmBytes = java.util.Base64.getUrlDecoder().decode(sm4GcmBlob);
        assertThat(sm4GcmBytes[0]).isEqualTo((byte) 0x01); // version
        assertThat(sm4GcmBytes[1]).isEqualTo((byte) 0x03); // SM4_GCM
    }

    @Test
    @Order(7)
    void wireFormatV1BlobIsSelfDescribing() {
        // Wire Format V1 blobs are fully self-describing (algorithm embedded in blob)
        MultiAlgoEntity entity = new MultiAlgoEntity();
        entity.setPlainField("plain");
        entity.setAesGcmField("self-describing-value");
        entityRepository.save(entity);

        // Read back - decryption uses algorithm from the blob itself
        MultiAlgoEntity loaded = entityRepository.findById(entity.getId()).orElseThrow();
        assertThat(loaded.getAesGcmField()).isEqualTo("self-describing-value");
    }
}
