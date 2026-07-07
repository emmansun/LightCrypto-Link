package io.github.emmansun.lightcrypto.testmodel;

import lombok.Data;

@Data
public class TestCircularRefA {
    private TestCircularRefB b;
}
