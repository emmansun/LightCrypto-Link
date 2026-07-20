package io.github.emmansun.lightcrypto.example.basiccrud;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import io.github.emmansun.lightcrypto.annotation.EncryptionMode;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

/**
 * Example entity for nested object and collection encryption scenarios.
 */
@Data
@Document
public class AdvancedUser {

    @Id
    private String id;

    private String name;

    /** Nested object encryption: only nested encrypted fields are protected. */
    private Address homeAddress;

    /** Element-level list encryption + blind index query support. */
    @Encrypted(blindIndex = true)
    private List<String> tags;

    /** Element-level map value encryption. */
    @Encrypted
    private Map<String, String> settings;

    /** Whole-mode simple list encryption, stored as one COL blob. */
    @Encrypted(mode = EncryptionMode.WHOLE)
    private List<String> secureTags;

    /** Whole-mode simple map encryption, stored as one MAP blob. */
    @Encrypted(mode = EncryptionMode.WHOLE)
    private Map<String, String> secureSettings;

    /** Whole-collection encryption for POJO list, stored as one encrypted blob. */
    @Encrypted
    private List<WholeAddress> secureAddresses;

    @Data
    public static class Address {
        @Encrypted
        private String street;
        private String city;
    }

    @Data
    public static class WholeAddress {
        private String street;
        private String city;
    }
}
