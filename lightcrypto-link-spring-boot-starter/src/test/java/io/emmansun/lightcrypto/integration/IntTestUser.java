package io.emmansun.lightcrypto.integration;

import io.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
public class IntTestUser {
    @Id
    private String id;

    private String name;

    @Encrypted(blindIndex = true)
    private String phone;

    private String email;
}
