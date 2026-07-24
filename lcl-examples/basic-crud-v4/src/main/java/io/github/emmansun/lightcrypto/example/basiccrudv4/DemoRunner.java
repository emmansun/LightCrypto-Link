package io.github.emmansun.lightcrypto.example.basiccrudv4;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Demonstrates LightCrypto-Link transparent encryption, decryption,
 * and blind index queries on Spring Boot 4.x.
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

        System.out.println("=== LightCrypto-Link Basic CRUD Demo (Spring Boot 4.x) ===\n");

        // 1. Save — fields are encrypted transparently
        User alice = new User();
        alice.setName("Alice");
        alice.setPhone("13800138001");
        alice.setEmail("alice@example.com");
        userRepository.save(alice);
        System.out.println("[SAVE] Alice saved — @Encrypted fields are AES-256-GCM encrypted on write.\n");

        // 2. Read — fields are decrypted transparently
        User loaded = userRepository.findById(alice.getId()).orElseThrow();
        System.out.println("[READ] Loaded user:");
        System.out.println("  name  = " + loaded.getName() + "  (plain text)");
        System.out.println("  phone = " + loaded.getPhone() + "  (decrypted from ciphertext)");
        System.out.println("  email = " + loaded.getEmail() + "  (decrypted from ciphertext)\n");

        // 3. Blind index query — findByPhone uses HMAC index, not plaintext
        User found = userRepository.findByPhone("13800138001");
        System.out.println("[QUERY] findByPhone(\"13800138001\") → " + found.getName());
        System.out.println("  The query matched the HMAC blind index, not the ciphertext.\n");

        // 4. Show raw MongoDB document — reveals encrypted content
        Document rawDoc = mongoTemplate.getDb().getCollection("user")
                .find(new Document("name", "Alice")).first();
        if (rawDoc != null) {
            System.out.println("[RAW] MongoDB document (as stored on disk):");
            System.out.println("  name  = " + rawDoc.getString("name") + "  (plain text, not encrypted)");
            System.out.println("  phone = " + rawDoc.get("phone") + "  (encrypted sub-document)");
            System.out.println("  email = " + rawDoc.get("email") + "  (encrypted sub-document)\n");
        }

        System.out.println("=== Demo complete ===");
    }
}
