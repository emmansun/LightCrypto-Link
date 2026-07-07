package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
public class TestUserWithAddress {
    @Id
    private String id;

    private String name;

    private Address address;

    @Data
    public static class Address {
        @Encrypted(blindIndex = true)
        private String zipCode;

        @Encrypted
        private String street;

        private String city;
    }
}
