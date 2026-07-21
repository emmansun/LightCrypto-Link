package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

@Data
public class TestUserWithAddress {
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
