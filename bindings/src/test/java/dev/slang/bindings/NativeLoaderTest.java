package dev.slang.bindings;

import org.junit.jupiter.api.Test;
import java.lang.foreign.SymbolLookup;
import static org.junit.jupiter.api.Assertions.*;

class NativeLoaderTest {

    @Test
    void loadsSlangLibrary() {
        SymbolLookup lookup = NativeLoader.load();
        assertNotNull(lookup);
        assertTrue(lookup.find("slang_createGlobalSession2").isPresent(),
            "slang_createGlobalSession2 should be found in loaded library");
    }

    @Test
    void detectsPlatform() {
        String libName = NativeLoader.platformLibraryName();
        assertNotNull(libName);
        assertTrue(libName.endsWith(".so") || libName.endsWith(".dll") || libName.endsWith(".dylib"),
            "Library name should have platform-appropriate extension: " + libName);
    }
}
