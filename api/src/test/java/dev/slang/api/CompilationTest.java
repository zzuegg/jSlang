package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompilationTest {

    private static final String SIMPLE_COMPUTE_SHADER = """
        [shader("compute")]
        [numthreads(1, 1, 1)]
        void computeMain(uint3 tid : SV_DispatchThreadID)
        {
        }
        """;

    @Test
    void compileToSpirv() throws Exception {
        var global = GlobalSession.create();
        int profile = global.findProfile("spirv_1_5");

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));

        var module = session.loadModuleFromSourceString(
            "test", "test.slang", SIMPLE_COMPUTE_SHADER);
        var entryPoint = module.findAndCheckEntryPoint("computeMain", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, entryPoint);
        var linked = composite.link();

        try (var spirv = linked.getEntryPointCode(0, 0)) {
            byte[] bytes = spirv.toByteArray();
            assertTrue(bytes.length > 0);

            // SPIRV magic number
            int magic = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
            assertEquals(0x07230203, magic);
        }

        // Release output objects, then session (which owns module/entryPoint)
        linked.close();
        composite.close();
        session.close();
        global.close();
    }

    @Test
    void compileToGLSL() throws Exception {
        var global = GlobalSession.create();

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.GLSL)));

        var module = session.loadModuleFromSourceString(
            "test-glsl", "test.slang", SIMPLE_COMPUTE_SHADER);
        var entryPoint = module.findAndCheckEntryPoint("computeMain", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, entryPoint);
        var linked = composite.link();

        try (var glsl = linked.getEntryPointCode(0, 0)) {
            String code = new String(glsl.toByteArray());
            assertTrue(code.contains("#version") || code.contains("void main"),
                "GLSL output should contain GLSL code");
            System.out.println("GLSL:\n" + code);
        }

        linked.close();
        composite.close();
        session.close();
        global.close();
    }

    @Test
    void invalidShaderThrows() throws Exception {
        var global = GlobalSession.create();

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.SPIRV)));

        assertThrows(SlangException.class, () -> {
            session.loadModuleFromSourceString(
                "bad", "bad.slang", "this is not valid slang code!!!");
        });

        session.close();
        global.close();
    }
}
