package dev.slang.bindings.raw;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.structs.SessionDesc;
import dev.slang.bindings.structs.TargetDesc;
import org.junit.jupiter.api.Test;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;

class CompilationRawTest {

    @Test
    void compileSimpleShaderToSpirv() {
        var globalSession = IGlobalSession.create();
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
                void computeMain(uint3 tid : SV_DispatchThreadID)
                {
                }
                """;

            var module = session.loadModuleFromSourceString(
                arena, "test-module", "test.slang", shaderSource);
            assertNotNull(module);

            var entryPoint = module.findAndCheckEntryPoint(
                arena, "computeMain", 6 /* SLANG_STAGE_COMPUTE */);
            assertNotNull(entryPoint);

            var composite = session.createCompositeComponentType(arena, module, entryPoint);
            var linked = composite.link(arena);

            var spirvBlob = linked.getEntryPointCode(arena, 0, 0);
            byte[] spirvBytes = spirvBlob.toByteArray();
            assertTrue(spirvBytes.length > 0, "SPIRV output should not be empty");

            // SPIRV magic number check (0x07230203)
            assertTrue(spirvBytes.length >= 4, "SPIRV should be at least 4 bytes");
            int magic = (spirvBytes[0] & 0xFF)
                | ((spirvBytes[1] & 0xFF) << 8)
                | ((spirvBytes[2] & 0xFF) << 16)
                | ((spirvBytes[3] & 0xFF) << 24);
            assertEquals(0x07230203, magic, "SPIRV magic number mismatch");

            System.out.println("SPIRV output size: " + spirvBytes.length + " bytes");

            // Release output objects; session owns module/entryPoint
            spirvBlob.close();
            linked.close();
            composite.close();
            session.close();
        }
        globalSession.close();
    }

    @Test
    void compileToHLSL() {
        var globalSession = IGlobalSession.create();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment targetDesc = TargetDesc.allocate(arena, CompileTarget.HLSL, 0);
            MemorySegment sessionDesc = SessionDesc.allocate(arena, targetDesc, 1);

            MemorySegment outSession = arena.allocate(ValueLayout.ADDRESS);
            globalSession.createSessionRaw(sessionDesc, outSession);
            var session = new ISession(outSession.get(ValueLayout.ADDRESS, 0));

            String shaderSource = """
                [shader("compute")]
                [numthreads(1, 1, 1)]
                void computeMain(uint3 tid : SV_DispatchThreadID)
                {
                }
                """;

            var module = session.loadModuleFromSourceString(
                arena, "test-hlsl", "test.slang", shaderSource);
            var entryPoint = module.findAndCheckEntryPoint(arena, "computeMain", 6);
            var composite = session.createCompositeComponentType(arena, module, entryPoint);
            var linked = composite.link(arena);

            var hlslBlob = linked.getEntryPointCode(arena, 0, 0);
            byte[] hlslBytes = hlslBlob.toByteArray();
            String hlsl = new String(hlslBytes);

            assertTrue(hlsl.contains("computeMain") || hlsl.contains("void"),
                "HLSL output should contain shader code");
            System.out.println("HLSL output:\n" + hlsl);

            // Release output objects; session owns module/entryPoint
            hlslBlob.close();
            linked.close();
            composite.close();
            session.close();
        }
        globalSession.close();
    }
}
