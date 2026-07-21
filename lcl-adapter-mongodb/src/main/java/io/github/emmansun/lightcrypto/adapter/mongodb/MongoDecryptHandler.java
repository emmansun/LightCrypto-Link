package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.service.FieldCryptoService;
import io.github.emmansun.lightcrypto.spi.DecryptHandler;
import org.bson.Document;

/**
 * MongoDB-specific decryption handler that delegates to {@link FieldCryptoService}
 * for decryption logic.
 *
 * @since 1.0.0
 */
public class MongoDecryptHandler implements DecryptHandler {

    private final FieldCryptoService fieldCryptoService;

    public MongoDecryptHandler(FieldCryptoService fieldCryptoService) {
        this.fieldCryptoService = fieldCryptoService;
    }

    @Override
    public void decrypt(Object document, Class<?> entityClass) {
        if (document instanceof Document bsonDoc) {
            fieldCryptoService.decryptDocument(bsonDoc, entityClass);
        }
    }
}
