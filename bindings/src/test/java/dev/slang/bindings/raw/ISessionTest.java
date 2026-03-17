package dev.slang.bindings.raw;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.structs.SessionDesc;
import dev.slang.bindings.structs.TargetDesc;
import org.junit.jupiter.api.Test;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;

class ISessionTest {

    private static final String SHADER_A = """
        [shader("compute")]
        [numthreads(1, 1, 1)]
        void mainA(uint3 tid : SV_DispatchThreadID)
        {
        }
        """;

    private static final String SHADER_B = """
        [shader("compute")]
        [numthreads(1, 1, 1)]
        void mainB(uint3 tid : SV_DispatchThreadID)
        {
        }
        """;

    private ISession createSession(Arena arena, IGlobalSession globalSession) {
        int profileId = globalSession.findProfile(arena, "spirv_1_5");
        MemorySegment targetDesc = TargetDesc.allocate(arena, CompileTarget.SPIRV, profileId);
        MemorySegment sessionDesc = SessionDesc.allocate(arena, targetDesc, 1);
        MemorySegment outSession = arena.allocate(ValueLayout.ADDRESS);
        globalSession.createSessionRaw(sessionDesc, outSession);
        return new ISession(outSession.get(ValueLayout.ADDRESS, 0));
    }

    @Test
    void getLoadedModuleCount() {
        var globalSession = IGlobalSession.create();
        try (Arena arena = Arena.ofConfined()) {
            var session = createSession(arena, globalSession);

            session.loadModuleFromSourceString(arena, "moduleA", "moduleA.slang", SHADER_A);
            session.loadModuleFromSourceString(arena, "moduleB", "moduleB.slang", SHADER_B);

            long count = session.getLoadedModuleCount();
            assertTrue(count >= 2, "Expected at least 2 loaded modules, got: " + count);

            session.close();
        }
        globalSession.close();
    }

    @Test
    void getLoadedModule() {
        var globalSession = IGlobalSession.create();
        try (Arena arena = Arena.ofConfined()) {
            var session = createSession(arena, globalSession);

            session.loadModuleFromSourceString(arena, "testMod", "testMod.slang", SHADER_A);

            long count = session.getLoadedModuleCount();
            assertTrue(count >= 1, "Expected at least 1 loaded module");

            boolean found = false;
            for (long i = 0; i < count; i++) {
                IModule mod = session.getLoadedModule(i);
                if ("testMod".equals(mod.getName())) {
                    found = true;
                    break;
                }
                // Don't close mod — session owns it
            }
            assertTrue(found, "Should find module 'testMod' among loaded modules");

            session.close();
        }
        globalSession.close();
    }

    @Test
    void getGlobalSession() {
        var globalSession = IGlobalSession.create();
        try (Arena arena = Arena.ofConfined()) {
            var session = createSession(arena, globalSession);

            MemorySegment globalPtr = session.getGlobalSession();
            assertNotNull(globalPtr);
            assertNotEquals(MemorySegment.NULL, globalPtr, "getGlobalSession should return non-null");

            session.close();
        }
        globalSession.close();
    }
}
