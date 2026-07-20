package io.github.emmansun.lightcrypto.testmodel;

import lombok.Data;

@Data
public class TestCircularRefB {
    private TestCircularRefA a;
}
