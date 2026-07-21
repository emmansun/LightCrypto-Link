package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
public class IntTestUserWithAddress {
    @Id
    private String id;

    private String name;

    private Address address;

    @Data
    public static class Address {
        @Encrypted
        private String street;

        @Encrypted(blindIndex = true)
        private String zipCode;

        private String city;
    }
}
