package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

import java.util.List;

@Data
public class TestUserWithAddresses {

    private List<Address> addresses;

    @Data
    public static class Address {
        @Encrypted
        private String street;

        private String city;
    }
}
