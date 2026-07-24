package io.github.emmansun.lightcrypto.example.basiccrudv4;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Demo entity — fields annotated with @Encrypted are transparently
 * encrypted/decrypted by LightCrypto-Link. No extra code required.
 */
@Data
@Document
public class User {

    @Id
    private String id;

    /** Plain-text field (not encrypted). */
    private String name;

    /**
     * Encrypted with blind index — supports findByPhone() exact-match queries.
     * Stored as ciphertext + HMAC blind index in MongoDB.
     */
    @Encrypted(blindIndex = true)
    private String phone;

    /** Encrypted field — ciphertext only, no query support. */
    @Encrypted
    private String email;
}
