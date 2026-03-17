package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionConfigTest {

    private static final String MACRO_SHADER = """
        [shader("compute")]
        [numthreads(1,1,1)]
        void csMain(uint3 tid : SV_DispatchThreadID, uniform RWStructuredBuffer<float> output)
        {
        #ifdef USE_FAST_PATH
            output[tid.x] = 42.0;
        #else
            output[tid.x] = 0.0;
        #endif
        }
        """;

    @Test
    void preprocessorMacroAffectsOutput() throws Exception {
        var global = GlobalSession.create();

        // Compile WITH macro
        var sessionWith = global.createSession(
            new SessionDescBuilder()
                .addTarget(new TargetDescBuilder().format(CompileTarget.HLSL))
                .addMacro("USE_FAST_PATH", "1"));

        var moduleWith = sessionWith.loadModuleFromSourceString(
            "macro-test-with", "macro-test-with.slang", MACRO_SHADER);
        var epWith = moduleWith.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
        var compositeWith = sessionWith.createCompositeComponentType(moduleWith, epWith);
        var linkedWith = compositeWith.link();

        String hlslWith;
        try (var code = linkedWith.getEntryPointCode(0, 0)) {
            hlslWith = new String(code.toByteArray());
        }

        linkedWith.close();
        compositeWith.close();
        sessionWith.close();

        // Compile WITHOUT macro
        var sessionWithout = global.createSession(
            new SessionDescBuilder()
                .addTarget(new TargetDescBuilder().format(CompileTarget.HLSL)));

        var moduleWithout = sessionWithout.loadModuleFromSourceString(
            "macro-test-without", "macro-test-without.slang", MACRO_SHADER);
        var epWithout = moduleWithout.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
        var compositeWithout = sessionWithout.createCompositeComponentType(moduleWithout, epWithout);
        var linkedWithout = compositeWithout.link();

        String hlslWithout;
        try (var code = linkedWithout.getEntryPointCode(0, 0)) {
            hlslWithout = new String(code.toByteArray());
        }

        linkedWithout.close();
        compositeWithout.close();
        sessionWithout.close();
        global.close();

        // The two HLSL outputs should differ
        assertNotEquals(hlslWith, hlslWithout,
            "Preprocessor macro should produce different output");
        assertTrue(hlslWith.contains("42"), "WITH macro should contain 42");
        assertTrue(hlslWithout.contains("0"), "WITHOUT macro should contain 0");
    }

    @Test
    void flagsCanBeSet() throws Exception {
        var global = GlobalSession.create();

        var session = global.createSession(
            new SessionDescBuilder()
                .addTarget(new TargetDescBuilder().format(CompileTarget.HLSL))
                .flags(0));

        var module = session.loadModuleFromSourceString(
            "flags-test", "flags-test.slang", MACRO_SHADER);
        assertNotNull(module);

        session.close();
        global.close();
    }
}
