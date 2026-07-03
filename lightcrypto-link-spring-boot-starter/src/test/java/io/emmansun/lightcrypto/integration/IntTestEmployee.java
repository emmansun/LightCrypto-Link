package io.emmansun.lightcrypto.integration;

import io.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Data
@Document
public class IntTestEmployee {
    @Id
    private String id;

    private String name;

    @Encrypted
    private Integer age;

    @Encrypted
    private LocalDate birthDate;
}
