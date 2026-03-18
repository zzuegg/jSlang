package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GLSLExtensionTest {

    private static final String SHADER = """
        [shader("compute")]
        [numthreads(8,8,1)]
        void csMain(
            uint3 tid : SV_DispatchThreadID,
            uniform StructuredBuffer<float3> colors,
            uniform Texture2D tex,
            uniform SamplerState samp,
            uniform RWTexture2D<float4> output)
        {
            float2 uv = float2(tid.xy) / 512.0;
            float4 texColor = tex.SampleLevel(samp, uv, 0);
            output[tid.xy] = texColor * float4(colors[0], 1.0);
        }
        """;

    @Test
    void injectGLSLExtensionsViaPrelude() throws Exception {
        var global = GlobalSession.create();

        // Set a GLSL language prelude — this gets prepended to ALL GLSL output
        // SourceLanguage.GLSL = 3
        global.setLanguagePrelude(3,
            "#extension GL_ARB_bindless_texture : require\n" +
            "#extension GL_ARB_gpu_shader5 : enable\n");

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.GLSL)));

        var module = session.loadModuleFromSourceString(
            "ext-test", "test.slang", SHADER);
        var ep = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, ep);
        var linked = composite.link();

        try (var blob = linked.getEntryPointCode(0, 0)) {
            String glsl = new String(blob.toByteArray());
            System.out.println("=== GLSL with injected extensions ===");
            System.out.println(glsl);

            assertTrue(glsl.contains("GL_ARB_bindless_texture"),
                "Prelude should inject bindless texture extension");
            assertTrue(glsl.contains("GL_ARB_gpu_shader5"),
                "Prelude should inject gpu_shader5 extension");
            assertTrue(glsl.contains("#version"),
                "Should still have version directive");
        }

        linked.close();
        composite.close();
        session.close();

        // Reset prelude for other tests
        global.setLanguagePrelude(3, "");
        global.close();
    }

    @Test
    void postProcessGLSLOutput() throws Exception {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.GLSL)));

        var module = session.loadModuleFromSourceString(
            "post-test", "test.slang", SHADER);
        var ep = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, ep);
        var linked = composite.link();

        try (var blob = linked.getEntryPointCode(0, 0)) {
            String glsl = new String(blob.toByteArray());

            // Post-process: inject extensions after #version line
            String extensions =
                "#extension GL_ARB_bindless_texture : require\n" +
                "#extension GL_ARB_shading_language_include : require\n" +
                "#extension GL_NV_gpu_shader5 : enable\n";

            String modified;
            int versionEnd = glsl.indexOf('\n');
            if (versionEnd > 0) {
                modified = glsl.substring(0, versionEnd + 1) + extensions + glsl.substring(versionEnd + 1);
            } else {
                modified = extensions + glsl;
            }

            System.out.println("=== Post-processed GLSL (first 20 lines) ===");
            String[] lines = modified.split("\n");
            for (int i = 0; i < Math.min(20, lines.length); i++) {
                System.out.println(lines[i]);
            }
            System.out.println("... (" + modified.length() + " chars total)");

            assertTrue(modified.contains("#version 450"));
            assertTrue(modified.contains("GL_ARB_bindless_texture"));
            // Extensions should come right after #version
            int versionIdx = modified.indexOf("#version");
            int extIdx = modified.indexOf("#extension");
            assertTrue(extIdx > versionIdx, "Extensions should follow #version");
        }

        linked.close();
        composite.close();
        session.close();
        global.close();
    }

    @Test
    void compareBindingLayoutAcrossTargets() throws Exception {
        var global = GlobalSession.create();

        // Show how the same Slang resources map to different binding models
        CompileTarget[] targets = { CompileTarget.GLSL, CompileTarget.HLSL, CompileTarget.SPIRV };

        for (var target : targets) {
            var session = global.createSession(
                new SessionDescBuilder().addTarget(
                    new TargetDescBuilder().format(target)
                        .profile(target == CompileTarget.SPIRV
                            ? global.findProfile("spirv_1_5") : 0)));

            var module = session.loadModuleFromSourceString(
                "bind-" + target, "test.slang", SHADER);
            var ep = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
            var composite = session.createCompositeComponentType(module, ep);
            var linked = composite.link();

            if (target != CompileTarget.SPIRV) {
                try (var blob = linked.getEntryPointCode(0, 0)) {
                    String code = new String(blob.toByteArray());
                    // Extract binding lines
                    System.out.println("--- " + target + " bindings ---");
                    for (String line : code.split("\n")) {
                        String trimmed = line.trim();
                        if (trimmed.contains("register(") || trimmed.contains("layout(binding")
                            || trimmed.contains("layout(std") || trimmed.contains("uniform image")
                            || trimmed.contains("uniform texture") || trimmed.contains("uniform sampler")) {
                            System.out.println("  " + trimmed);
                        }
                    }
                    System.out.println();
                }
            } else {
                try (var blob = linked.getEntryPointCode(0, 0)) {
                    System.out.println("--- SPIRV: " + blob.toByteArray().length + " bytes ---\n");
                }
            }

            linked.close();
            composite.close();
            session.close();
        }

        global.close();
    }
}
