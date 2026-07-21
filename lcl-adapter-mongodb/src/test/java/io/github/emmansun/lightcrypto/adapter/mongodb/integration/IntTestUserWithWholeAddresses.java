package io.github.emmansun.lightcrypto.adapter.mongodb.integration;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document
public class IntTestUserWithWholeAddresses {
    @Id
    private String id;

    private String name;

    @Encrypted
    private List<Address> addresses;

    @Data
    public static class Address {
        private String street;
        private String city;
    }
}
