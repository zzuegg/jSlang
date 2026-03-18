package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GLSLVersionTest {

    private static final String SHADER = """
        struct Light {
            float3 position;
            float3 color;
            float intensity;
        };

        [shader("compute")]
        [numthreads(8, 8, 1)]
        void csMain(
            uint3 tid : SV_DispatchThreadID,
            uniform StructuredBuffer<Light> lights,
            uniform uint lightCount,
            uniform RWStructuredBuffer<float3> output)
        {
            float3 color = float3(0);
            for (uint i = 0; i < lightCount; i++)
                color += lights[i].color * lights[i].intensity;
            output[tid.x] = color;
        }
        """;

    @Test
    void probeGLSLProfiles() throws Exception {
        var global = GlobalSession.create();

        // Try various GLSL/OpenGL profile names
        String[] profileNames = {
            "glsl_110", "glsl_120", "glsl_130", "glsl_140", "glsl_150",
            "glsl_300_es", "glsl_310_es", "glsl_320_es",
            "glsl_330", "glsl_400", "glsl_410", "glsl_420", "glsl_430",
            "glsl_440", "glsl_450", "glsl_460",
            "sm_4_0", "sm_5_0", "sm_5_1", "sm_6_0", "sm_6_5",
            "spirv_1_0", "spirv_1_3", "spirv_1_5", "spirv_1_6",
        };

        System.out.println("=== Profile ID Lookup ===");
        System.out.printf("  %-20s  %s%n", "Profile", "ID");
        System.out.println("  " + "-".repeat(35));
        for (String name : profileNames) {
            int id = global.findProfile(name);
            System.out.printf("  %-20s  %s%n", name, id >= 0 ? String.valueOf(id) : "NOT FOUND");
        }

        System.out.println("\n=== GLSL Output with Different Profiles ===\n");

        // Compile with various GLSL profiles and show the #version line
        String[] glslProfiles = {
            "glsl_430", "glsl_440", "glsl_450", "glsl_460",
            "glsl_310_es", "glsl_320_es",
        };

        for (String profile : glslProfiles) {
            int profileId = global.findProfile(profile);
            if (profileId < 0) {
                System.out.println("--- " + profile + ": not available ---\n");
                continue;
            }

            try {
                var session = global.createSession(
                    new SessionDescBuilder().addTarget(
                        new TargetDescBuilder()
                            .format(CompileTarget.GLSL)
                            .profile(profileId)));

                var module = session.loadModuleFromSourceString(
                    "test-" + profile, "test.slang", SHADER);
                var ep = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
                var composite = session.createCompositeComponentType(module, ep);
                var linked = composite.link();

                try (var blob = linked.getEntryPointCode(0, 0)) {
                    String code = new String(blob.toByteArray());
                    // Extract first few lines to show #version
                    String[] lines = code.split("\n");
                    System.out.println("--- " + profile + " (id=" + profileId + ") ---");
                    for (int i = 0; i < Math.min(6, lines.length); i++) {
                        String line = lines[i].trim();
                        if (!line.isEmpty()) System.out.println("  " + line);
                    }
                    System.out.println("  ... (" + code.length() + " chars total)\n");
                }

                linked.close();
                composite.close();
                session.close();
            } catch (Exception e) {
                System.out.println("--- " + profile + ": FAILED — " + e.getMessage() + " ---\n");
            }
        }

        global.close();
    }
}
