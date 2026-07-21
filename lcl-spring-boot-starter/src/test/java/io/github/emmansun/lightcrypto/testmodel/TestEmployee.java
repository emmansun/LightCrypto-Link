package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TestEmployee {
    private String id;

    private String name;

    @Encrypted
    private Integer age;

    @Encrypted
    private LocalDate birthDate;
}
