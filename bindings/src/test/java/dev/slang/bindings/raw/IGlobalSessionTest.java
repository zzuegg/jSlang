package dev.slang.bindings.raw;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IGlobalSessionTest {

    @Test
    void createAndGetBuildTag() {
        try (var session = IGlobalSession.create()) {
            String tag = session.getBuildTagString();
            assertNotNull(tag);
            assertFalse(tag.isEmpty(), "Build tag should not be empty");
            System.out.println("Slang build tag: " + tag);
        }
    }

    @Test
    void findProfile() {
        try (var session = IGlobalSession.create()) {
            try (var arena = java.lang.foreign.Arena.ofConfined()) {
                int profileId = session.findProfile(arena, "spirv_1_5");
                assertTrue(profileId >= 0, "spirv_1_5 profile should be found, got: " + profileId);
            }
        }
    }
}
