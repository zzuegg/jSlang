package dev.slang.bindings.raw;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.structs.SessionDesc;
import dev.slang.bindings.structs.TargetDesc;
import org.junit.jupiter.api.Test;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;

class IComponentTypeTest {

    private static final String SIMPLE_SHADER = """
        [shader("compute")]
        [numthreads(1, 1, 1)]
        void csMain(uint3 tid : SV_DispatchThreadID)
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

    private IModule loadModule(Arena arena, ISession session) {
        return session.loadModuleFromSourceString(arena, "test-module", "test.slang", SIMPLE_SHADER);
    }

    @Test
    void getSpecializationParamCount() {
        var globalSession = IGlobalSession.create();
        try (Arena arena = Arena.ofConfined()) {
            var session = createSession(arena, globalSession);
            var module = loadModule(arena, session);
            var entryPoint = module.findAndCheckEntryPoint(arena, "csMain", 6);
            var composite = session.createCompositeComponentType(arena, module, entryPoint);

            long paramCount = composite.getSpecializationParamCount();
            assertEquals(0, paramCount, "Simple shader should have 0 specialization params");

            composite.close();
            session.close();
        }
        globalSession.close();
    }

    @Test
    void getSession() {
        var globalSession = IGlobalSession.create();
        try (Arena arena = Arena.ofConfined()) {
            var session = createSession(arena, globalSession);
            var module = loadModule(arena, session);
            var entryPoint = module.findAndCheckEntryPoint(arena, "csMain", 6);
            var composite = session.createCompositeComponentType(arena, module, entryPoint);

            MemorySegment sessionPtr = composite.getSession();
            assertNotNull(sessionPtr);
            assertNotEquals(MemorySegment.NULL, sessionPtr, "getSession should return non-null");

            composite.close();
            session.close();
        }
        globalSession.close();
    }

    @Test
    void renameEntryPoint() {
        var globalSession = IGlobalSession.create();
        try (Arena arena = Arena.ofConfined()) {
            var session = createSession(arena, globalSession);
            var module = loadModule(arena, session);
            var entryPoint = module.findAndCheckEntryPoint(arena, "csMain", 6);
            var composite = session.createCompositeComponentType(arena, module, entryPoint);

            var renamed = composite.renameEntryPoint(arena, "myMain");
            assertNotNull(renamed);

            // Verify the renamed component can still link and produce code
            var linked = renamed.link(arena);
            var spirvBlob = linked.getEntryPointCode(arena, 0, 0);
            byte[] spirvBytes = spirvBlob.toByteArray();
            assertTrue(spirvBytes.length > 0, "Renamed component should still produce SPIRV");

            // Verify SPIRV magic number
            int magic = (spirvBytes[0] & 0xFF)
                | ((spirvBytes[1] & 0xFF) << 8)
                | ((spirvBytes[2] & 0xFF) << 16)
                | ((spirvBytes[3] & 0xFF) << 24);
            assertEquals(0x07230203, magic, "SPIRV magic number mismatch");

            spirvBlob.close();
            linked.close();
            renamed.close();
            composite.close();
            session.close();
        }
        globalSession.close();
    }

    @Test
    void getEntryPointHash() {
        var globalSession = IGlobalSession.create();
        try (Arena arena = Arena.ofConfined()) {
            var session = createSession(arena, globalSession);
            var module = loadModule(arena, session);
            var entryPoint = module.findAndCheckEntryPoint(arena, "csMain", 6);
            var composite = session.createCompositeComponentType(arena, module, entryPoint);
            var linked = composite.link(arena);

            ISlangBlob hashBlob = linked.getEntryPointHash(arena, 0, 0);
            assertNotNull(hashBlob, "Entry point hash should not be null");
            byte[] hashBytes = hashBlob.toByteArray();
            assertTrue(hashBytes.length > 0, "Hash should not be empty");

            System.out.println("Entry point hash size: " + hashBytes.length + " bytes");

            hashBlob.close();
            linked.close();
            composite.close();
            session.close();
        }
        globalSession.close();
    }
}
