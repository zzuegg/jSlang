package dev.slang.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlobalSessionTest {

    @Test
    void createAndGetBuildTag() {
        try (var global = GlobalSession.create()) {
            String tag = global.getBuildTagString();
            assertNotNull(tag);
            assertFalse(tag.isEmpty());
            System.out.println("Slang version: " + tag);
        }
    }

    @Test
    void findProfile() {
        try (var global = GlobalSession.create()) {
            int spirv = global.findProfile("spirv_1_5");
            assertTrue(spirv >= 0);
        }
    }
}
