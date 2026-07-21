package io.github.emmansun.lightcrypto.adapter.mongodb;

import io.github.emmansun.lightcrypto.spi.DecryptHandler;
import io.github.emmansun.lightcrypto.spi.EncryptHandler;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

/**
 * MongoDB event listener for transparent field encryption/decryption.
 *
 * <p>Extends {@link AbstractMongoEventListener} to intercept MongoDB lifecycle events:
 * <ul>
 *   <li>{@code onBeforeSave}: encrypts @Encrypted fields via {@link EncryptHandler}</li>
 *   <li>{@code onBeforeConvert}: decrypts encrypted payloads via {@link DecryptHandler}</li>
 * </ul>
 *
 * <p>The actual encryption/decryption logic is delegated to the handlers provided
 * by the starter module, keeping this adapter focused on MongoDB event binding.
 *
 * @since 1.0.0
 */
public class MongoCryptoEventListener extends AbstractMongoEventListener<Object> {

    private final EncryptHandler encryptHandler;
    private final DecryptHandler decryptHandler;

    public MongoCryptoEventListener(EncryptHandler encryptHandler, DecryptHandler decryptHandler) {
        this.encryptHandler = encryptHandler;
        this.decryptHandler = decryptHandler;
    }

    @Override
    public void onBeforeSave(BeforeSaveEvent<Object> event) {
        Object source = event.getSource();
        Document document = event.getDocument();

        if (source == null || document == null) {
            return;
        }

        encryptHandler.encrypt(document, source, source.getClass());
    }

    @Override
    public void onBeforeConvert(BeforeConvertEvent<Object> event) {
        Object source = event.getSource();
        Document document = event.getDocument();

        if (source == null || document == null) {
            return;
        }

        decryptHandler.decrypt(document, source.getClass());
    }
}
