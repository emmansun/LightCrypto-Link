package io.github.emmansun.lightcrypto.example.basiccrud;

import io.github.emmansun.lightcrypto.service.FieldCryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates LightCrypto-Link transparent encryption, decryption, blind index queries,
 * and manual decryption via FieldCryptoService for raw Documents.
 */
@Component
@ConditionalOnProperty(name = "lcl.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AdvancedUserRepository advancedUserRepository;
    private final MongoTemplate mongoTemplate;
    private final FieldCryptoService fieldCryptoService;

    public DemoRunner(UserRepository userRepository,
                      AdvancedUserRepository advancedUserRepository,
                      MongoTemplate mongoTemplate,
                      FieldCryptoService fieldCryptoService) {
        this.userRepository = userRepository;
        this.advancedUserRepository = advancedUserRepository;
        this.mongoTemplate = mongoTemplate;
        this.fieldCryptoService = fieldCryptoService;
    }

    @Override
    public void run(String... args) {
        // Clean up previous runs
        mongoTemplate.dropCollection(User.class);
        mongoTemplate.dropCollection(AdvancedUser.class);

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

        // 6. Nested object + collection encryption examples
        AdvancedUser advancedUser = new AdvancedUser();
        advancedUser.setName("NestedAndCollection");

        AdvancedUser.Address nestedAddress = new AdvancedUser.Address();
        nestedAddress.setStreet("No.1 Encrypted Rd");
        nestedAddress.setCity("Shanghai");
        advancedUser.setHomeAddress(nestedAddress);

        advancedUser.setTags(List.of("java", "security"));
        advancedUser.setSettings(Map.of("theme", "dark"));
        advancedUser.setSecureTags(List.of("internal", "restricted"));
        advancedUser.setSecureSettings(Map.of("token", "abc-xyz"));

        AdvancedUser.WholeAddress wholeAddress = new AdvancedUser.WholeAddress();
        wholeAddress.setStreet("No.8 WholeObject Ave");
        wholeAddress.setCity("Beijing");
        advancedUser.setSecureAddresses(List.of(wholeAddress));

        advancedUserRepository.save(advancedUser);

        AdvancedUser loadedAdvanced = advancedUserRepository.findById(advancedUser.getId()).orElseThrow();
        System.out.println("[NESTED] homeAddress.street = " + loadedAdvanced.getHomeAddress().getStreet());
        System.out.println("[COLLECTION] tags = " + loadedAdvanced.getTags());
        System.out.println("[COLLECTION] settings.theme = " + loadedAdvanced.getSettings().get("theme"));
        System.out.println("[WHOLE SIMPLE COL] secureTags = " + loadedAdvanced.getSecureTags());
        System.out.println("[WHOLE SIMPLE MAP] secureSettings.token = " + loadedAdvanced.getSecureSettings().get("token"));
        System.out.println("[WHOLE COL] secureAddresses[0].street = "
            + loadedAdvanced.getSecureAddresses().get(0).getStreet() + "\n");

        AdvancedUser foundByTag = advancedUserRepository.findByTagsContaining("java");
        System.out.println("[QUERY] findByTagsContaining(\"java\") -> " + foundByTag.getName());

        Document rawAdvanced = mongoTemplate.findOne(
            new Query().addCriteria(new org.springframework.data.mongodb.core.query.Criteria("_id").is(advancedUser.getId())),
            Document.class,
            "advancedUser"
        );
        if (rawAdvanced != null) {
            System.out.println("[RAW ADVANCED] MongoDB document (as stored):");
            System.out.println("  homeAddress      = " + rawAdvanced.get("homeAddress")
                + "  (nested street encrypted)");
            System.out.println("  tags             = " + rawAdvanced.get("tags")
                + "  (array element encryption)");
            System.out.println("  settings         = " + rawAdvanced.get("settings")
                + "  (map value encryption)");
            System.out.println("  secureTags       = " + rawAdvanced.get("secureTags")
                + "  (whole simple list as COL blob)");
            System.out.println("  secureSettings   = " + rawAdvanced.get("secureSettings")
                + "  (whole simple map as MAP blob)");
            System.out.println("  secureAddresses  = " + rawAdvanced.get("secureAddresses")
                + "  (whole-collection encrypted blob)\n");
        }

        System.out.println("=== Demo complete ===");
    }
}
