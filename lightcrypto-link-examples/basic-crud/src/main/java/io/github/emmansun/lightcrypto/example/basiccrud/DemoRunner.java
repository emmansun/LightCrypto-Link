package io.github.emmansun.lightcrypto.example.basiccrud;

import io.github.emmansun.lightcrypto.service.FieldCryptoService;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Demonstrates LightCrypto-Link transparent encryption, decryption, blind index queries,
 * and manual decryption via FieldCryptoService for raw Documents.
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final FieldCryptoService fieldCryptoService;

    public DemoRunner(UserRepository userRepository,
                      MongoTemplate mongoTemplate,
                      FieldCryptoService fieldCryptoService) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.fieldCryptoService = fieldCryptoService;
    }

    @Override
    public void run(String... args) {
        // Clean up previous runs
        mongoTemplate.dropCollection(User.class);

        System.out.println("=== LightCrypto-Link Basic CRUD Demo ===\n");

        // 1. Save — fields are encrypted transparently
        User alice = new User();
        alice.setName("Alice");
        alice.setPhone("13800138001");
        alice.setAge(30);
        alice.setBirthDate(LocalDate.of(1995, 6, 15));
        userRepository.save(alice);
        System.out.println("[SAVE] Alice saved — @Encrypted fields are AES-256-GCM encrypted on write.\n");

        // 2. Read — fields are decrypted transparently
        User loaded = userRepository.findById(alice.getId()).orElseThrow();
        System.out.println("[READ] Loaded user:");
        System.out.println("  name      = " + loaded.getName() + "  (plain text)");
        System.out.println("  phone     = " + loaded.getPhone() + "  (decrypted from ciphertext)");
        System.out.println("  age       = " + loaded.getAge() + "  (decrypted Integer)");
        System.out.println("  birthDate = " + loaded.getBirthDate() + "  (decrypted LocalDate)\n");

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
            System.out.println("  birthDate = " + rawDoc.get("birthDate") + "  (encrypted sub-document)\n");

            // 5. Manual decryption — use FieldCryptoService when you bypass the Repository
            //    (e.g. aggregation pipelines, native driver queries, data migration scripts)
            System.out.println("[MANUAL DECRYPT] Using FieldCryptoService.decryptDocument():");
            fieldCryptoService.decryptDocument(rawDoc, User.class);
            System.out.println("  phone     = " + rawDoc.get("phone") + "  (decrypted manually)");
            System.out.println("  age       = " + rawDoc.get("age") + "  (decrypted manually)");
            System.out.println("  birthDate = " + rawDoc.get("birthDate") + "  (decrypted manually)");
            System.out.println("  Useful for: aggregation pipelines, MongoCollection queries, data migration, debugging.\n");
        }

        System.out.println("=== Demo complete ===");
    }
}
