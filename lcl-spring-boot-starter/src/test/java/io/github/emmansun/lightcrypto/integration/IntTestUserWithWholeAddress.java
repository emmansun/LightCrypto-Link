package io.github.emmansun.lightcrypto.integration;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
public class IntTestUserWithWholeAddress {
    @Id
    private String id;

    private String name;

    @Encrypted
    private Address address;

    @Data
    public static class Address {
        private String street;
        private String zipCode;
        private String city;
    }
}
