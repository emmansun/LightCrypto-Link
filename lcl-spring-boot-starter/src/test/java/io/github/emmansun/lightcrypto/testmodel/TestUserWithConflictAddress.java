package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

@Data
public class TestUserWithConflictAddress {

    @Encrypted
    private Address address;

    @Data
    public static class Address {
        @Encrypted
        private String street;
    }
}
