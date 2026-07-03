package com.lcl.crypto.testmodel;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
public class TestPlainEntity {
    @Id
    private String id;
    private String name;
    private int value;
}
