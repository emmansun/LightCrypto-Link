package io.github.emmansun.lightcrypto.testmodel;

import io.github.emmansun.lightcrypto.annotation.Encrypted;
import lombok.Data;

@Data
public class TestUserWithTooDeepNesting {
    private Level1 level1;

    @Data
    public static class Level1 {
        private Level2 level2;
    }

    @Data
    public static class Level2 {
        private Level3 level3;
    }

    @Data
    public static class Level3 {
        private Level4 level4;
    }

    @Data
    public static class Level4 {
        private Level5 level5;
    }

    @Data
    public static class Level5 {
        private Level6 level6;
    }

    @Data
    public static class Level6 {
        @Encrypted
        private String secret;
    }
}
