package com.lcl.crypto.testmodel;

import com.lcl.crypto.annotation.Encrypted;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
public class TestUser {
    @Id
    private String id;

    private String name;

    @Encrypted(blindIndex = true)
    private String phone;

    private String email;
}
