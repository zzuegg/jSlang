package dev.slang.bindings.raw;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.structs.SessionDesc;
import dev.slang.bindings.structs.TargetDesc;
import org.junit.jupiter.api.Test;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;

class IGlobalSessionExtTest {

    @Test
    void checkCompileTargetSupport() {
        try (var session = IGlobalSession.create()) {
            // SPIRV should always be supported
            assertTrue(session.checkCompileTargetSupport(CompileTarget.SPIRV.value()),
                "SPIRV should be supported");

            // HLSL should also be supported
            assertTrue(session.checkCompileTargetSupport(CompileTarget.HLSL.value()),
                "HLSL should be supported");

            // GLSL should also be supported
            assertTrue(session.checkCompileTargetSupport(CompileTarget.GLSL.value()),
                "GLSL should be supported");
        }
    }

    @Test
    void findCapability() {
        try (var session = IGlobalSession.create()) {
            try (Arena arena = Arena.ofConfined()) {
                int cap = session.findCapability(arena, "spirv_1_5");
                assertTrue(cap >= 0, "spirv_1_5 capability should be found, got: " + cap);
            }
        }
    }

    @Test
    void getCompilerElapsedTime() {
        try (var globalSession = IGlobalSession.create()) {
            // Compile something first so elapsed time is non-trivial
            try (Arena arena = Arena.ofConfined()) {
                int profileId = globalSession.findProfile(arena, "spirv_1_5");
                MemorySegment targetDesc = TargetDesc.allocate(arena, CompileTarget.SPIRV, profileId);
                MemorySegment sessionDesc = SessionDesc.allocate(arena, targetDesc, 1);
                MemorySegment outSession = arena.allocate(ValueLayout.ADDRESS);
                globalSession.createSessionRaw(sessionDesc, outSession);
                var session = new ISession(outSession.get(ValueLayout.ADDRESS, 0));

                String shaderSource = """
                    [shader("compute")]
                    [numthreads(1, 1, 1)]
                    void computeMain(uint3 tid : SV_DispatchThreadID) {}
                    """;
                var module = session.loadModuleFromSourceString(
                    arena, "elapsed-test", "elapsed.slang", shaderSource);
                var entryPoint = module.findAndCheckEntryPoint(arena, "computeMain", 6);
                var composite = session.createCompositeComponentType(arena, module, entryPoint);
                var linked = composite.link(arena);
                var spirvBlob = linked.getEntryPointCode(arena, 0, 0);
                spirvBlob.close();
                linked.close();
                composite.close();
                session.close();
            }

            // Now check elapsed time
            try (Arena arena = Arena.ofConfined()) {
                double[] times = globalSession.getCompilerElapsedTime(arena);
                assertNotNull(times);
                assertEquals(2, times.length);
                assertTrue(times[0] >= 0, "Total time should be >= 0, got: " + times[0]);
                assertTrue(times[1] >= 0, "Downstream time should be >= 0, got: " + times[1]);
                System.out.println("Compiler elapsed: total=" + times[0] + "s, downstream=" + times[1] + "s");
            }
        }
    }

    @Test
    void getSharedLibraryLoader() {
        try (var session = IGlobalSession.create()) {
            // May return NULL if no custom loader is set — that's fine
            MemorySegment loader = session.getSharedLibraryLoader();
            assertNotNull(loader, "getSharedLibraryLoader should return a non-null MemorySegment (possibly NULL address)");
            System.out.println("Shared library loader: " + loader);
        }
    }

    @Test
    void setAndGetLanguagePrelude() {
        try (var session = IGlobalSession.create()) {
            String prelude = "// test prelude for Slang language\n";
            int sourceLanguageSlang = 1;

            try (Arena arena = Arena.ofConfined()) {
                session.setLanguagePrelude(arena, sourceLanguageSlang, prelude);
            }

            try (Arena arena = Arena.ofConfined()) {
                ISlangBlob blob = session.getLanguagePrelude(arena, sourceLanguageSlang);
                assertNotNull(blob, "Prelude blob should not be null");
                byte[] bytes = blob.toByteArray();
                String retrieved = new String(bytes);
                assertEquals(prelude, retrieved, "Retrieved prelude should match what was set");
                System.out.println("Language prelude: " + retrieved);
                blob.close();
            }
        }
    }
}
