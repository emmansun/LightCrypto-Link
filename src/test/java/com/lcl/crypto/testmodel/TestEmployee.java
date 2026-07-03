package com.lcl.crypto.testmodel;

import com.lcl.crypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Data
@Document
public class TestEmployee {
    @Id
    private String id;

    private String name;

    @Encrypted
    private Integer age;

    @Encrypted
    private LocalDate birthDate;
}
