package io.github.emmansun.lightcrypto.example.observability;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Sample entity with encrypted fields.
 */
@Data
@Document(collection = "user")
public class User {

    @Id
    private String id;

    private String name;

    @Encrypted(blindIndex = true)
    private String phone;

    @Encrypted
    private Integer age;
}
