package io.github.emmansun.lightcrypto.example.azurekeyvault;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Demonstrates LightCrypto-Link transparent encryption with Azure Key Vault.
 *
 * <p>How it works:
 * <ol>
 *   <li>On startup, the provider fetches the RSA public key from Key Vault (or uses a cached PEM).</li>
 *   <li>On save: a random DEK is generated, fields are encrypted with AES-256-GCM,
 *       the DEK is wrapped locally using RSA-OAEP (SHA-256 + MGF1-SHA-256) — zero network overhead.</li>
 *   <li>On read: the wrapped DEK is sent to Key Vault CryptographyClient.unwrapKey() to recover the plaintext DEK,
 *       then fields are decrypted locally.</li>
 * </ol>
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public DemoRunner(UserRepository userRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        // Clean up previous runs
        mongoTemplate.dropCollection(User.class);

        System.out.println("=== LightCrypto-Link Azure Key Vault Demo ===\n");
        System.out.println("CMK Provider: Azure Key Vault (RSA-OAEP SHA-256)");
        System.out.println("  - Wrap: local RSA-OAEP (zero network overhead)");
        System.out.println("  - Unwrap: Key Vault CryptographyClient.unwrapKey()\n");

        // 1. Save — fields are encrypted transparently
        User alice = new User();
        alice.setName("Alice");
        alice.setPhone("13800138001");
        alice.setAge(30);
        alice.setBirthDate(LocalDate.of(1995, 6, 15));
        userRepository.save(alice);
        System.out.println("[SAVE] Alice saved — @Encrypted fields encrypted with AES-256-GCM.");
        System.out.println("  DEK wrapped locally via RSA-OAEP (no Key Vault network call on write).\n");

        // 2. Read — fields are decrypted transparently
        User loaded = userRepository.findById(alice.getId()).orElseThrow();
        System.out.println("[READ] Loaded user:");
        System.out.println("  name      = " + loaded.getName() + "  (plain text)");
        System.out.println("  phone     = " + loaded.getPhone() + "  (decrypted from ciphertext)");
        System.out.println("  age       = " + loaded.getAge() + "  (decrypted Integer)");
        System.out.println("  birthDate = " + loaded.getBirthDate() + "  (decrypted LocalDate)");
        System.out.println("  DEK unwrapped via Key Vault CryptographyClient on read.\n");

        // 3. Blind index query — findByPhone uses HMAC index, not plaintext
        User found = userRepository.findByPhone("13800138001");
        System.out.println("[QUERY] findByPhone(\"13800138001\") → " + found.getName());
        System.out.println("  The query matched the HMAC blind index, not the ciphertext.\n");

        // 4. Show raw MongoDB document — reveals encrypted content
        Document rawDoc = mongoTemplate.findOne(
                new Query().addCriteria(new org.springframework.data.mongodb.core.query.Criteria("_id").is(alice.getId())),
                Document.class,
                "user"
        );
        if (rawDoc != null) {
            System.out.println("[RAW] MongoDB document (as stored):");
            System.out.println("  _id       = " + rawDoc.getObjectId("_id"));
            System.out.println("  name      = " + rawDoc.getString("name") + "  (plain text, not encrypted)");
            System.out.println("  phone     = " + rawDoc.get("phone") + "  (encrypted sub-document)");
            System.out.println("  age       = " + rawDoc.get("age") + "  (encrypted sub-document)");
            System.out.println("  birthDate = " + rawDoc.get("birthDate") + "  (encrypted sub-document)");
            System.out.println("  _keyVault = " + rawDoc.get("_keyVault") + "  (wrapped DEK + metadata)");
        }

        System.out.println("\n=== Demo complete ===");
    }
}
