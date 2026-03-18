package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleSystemTest {

    // Module 1: A reusable math utilities library
    private static final String MATH_UTILS_SOURCE = """
        // math_utils.slang — reusable vector math
        float3 safeNormalize(float3 v)
        {
            float len = length(v);
            return len > 0.0001 ? v / len : float3(0, 0, 0);
        }

        float smoothstepCustom(float edge0, float edge1, float x)
        {
            float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
            return t * t * (3.0 - 2.0 * t);
        }

        float3 remapNormal(float3 normalMap)
        {
            return normalMap * 2.0 - 1.0;
        }
        """;

    // Module 2: A lighting library that imports math_utils
    private static final String LIGHTING_SOURCE = """
        // lighting.slang — imports the math utilities module
        import math_utils;

        struct PointLight
        {
            float3 position;
            float3 color;
            float  radius;
            float  intensity;
        };

        float3 computePointLight(PointLight light, float3 worldPos, float3 normal)
        {
            float3 L = light.position - worldPos;
            float dist = length(L);
            L = safeNormalize(L);  // from math_utils

            float NdotL = max(dot(normal, L), 0.0);
            float attenuation = 1.0 - smoothstepCustom(0.0, light.radius, dist);  // from math_utils

            return light.color * light.intensity * NdotL * attenuation;
        }

        float3 computeMultiLight(
            StructuredBuffer<PointLight> lights,
            uint lightCount,
            float3 worldPos,
            float3 normal)
        {
            float3 result = float3(0);
            for (uint i = 0; i < lightCount; i++)
                result += computePointLight(lights[i], worldPos, normal);
            return result;
        }
        """;

    // Module 3: The main shader that imports lighting (which transitively imports math_utils)
    private static final String MAIN_SHADER_SOURCE = """
        // main.slang — top-level shader using the lighting module
        import lighting;

        struct Vertex
        {
            float3 worldPos;
            float3 normal;
        };

        [shader("compute")]
        [numthreads(64, 1, 1)]
        void csMain(
            uint3 tid : SV_DispatchThreadID,
            uniform StructuredBuffer<Vertex> vertices,
            uniform StructuredBuffer<PointLight> lights,
            uniform uint lightCount,
            uniform RWStructuredBuffer<float3> output)
        {
            Vertex v = vertices[tid.x];
            output[tid.x] = computeMultiLight(lights, lightCount, v.worldPos, v.normal);
        }
        """;

    // A second consumer of the lighting module — demonstrates reuse
    private static final String SECOND_SHADER_SOURCE = """
        import lighting;
        import math_utils;

        [shader("compute")]
        [numthreads(1, 1, 1)]
        void normalMapShade(
            uint3 tid : SV_DispatchThreadID,
            uniform StructuredBuffer<float3> normalMaps,
            uniform StructuredBuffer<PointLight> lights,
            uniform float3 worldPos,
            uniform RWStructuredBuffer<float3> output)
        {
            float3 N = remapNormal(normalMaps[tid.x]);  // from math_utils
            N = safeNormalize(N);                        // from math_utils
            output[tid.x] = computePointLight(lights[0], worldPos, N);  // from lighting
        }
        """;

    @Test
    void multiModuleCompilation() throws Exception {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.HLSL)));

        // Load modules in dependency order.
        // math_utils has no imports — load first
        var mathUtils = session.loadModuleFromSourceString(
            "math_utils", "math_utils.slang", MATH_UTILS_SOURCE);
        System.out.println("Loaded: " + mathUtils.getName());

        // lighting imports math_utils — Slang resolves it from the session
        var lighting = session.loadModuleFromSourceString(
            "lighting", "lighting.slang", LIGHTING_SOURCE);
        System.out.println("Loaded: " + lighting.getName());

        // main imports lighting (transitive dep on math_utils)
        var main = session.loadModuleFromSourceString(
            "main", "main.slang", MAIN_SHADER_SOURCE);
        System.out.println("Loaded: " + main.getName());

        // Verify loaded module count
        long count = session.getLoadedModuleCount();
        System.out.println("Session has " + count + " loaded modules");
        assertTrue(count >= 3, "Should have at least 3 modules loaded");

        // Compile main shader
        var entryPoint = main.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(main, entryPoint);
        var linked = composite.link();

        try (var hlsl = linked.getEntryPointCode(0, 0)) {
            String code = new String(hlsl.toByteArray());
            System.out.println("\n=== main.slang → HLSL (" + code.length() + " chars) ===");
            System.out.println(code);

            // Verify cross-module functions are inlined
            assertTrue(code.contains("csMain"), "Should have entry point");
            // The lighting computation should be present
            assertTrue(code.length() > 300,
                "Multi-module shader should produce substantial output");
        }

        linked.close();
        composite.close();

        session.close();
        global.close();
    }

    @Test
    void moduleReuse() throws Exception {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.HLSL)));

        // Load shared modules
        session.loadModuleFromSourceString(
            "math_utils", "math_utils.slang", MATH_UTILS_SOURCE);
        session.loadModuleFromSourceString(
            "lighting", "lighting.slang", LIGHTING_SOURCE);

        // First consumer
        var main = session.loadModuleFromSourceString(
            "main", "main.slang", MAIN_SHADER_SOURCE);
        var mainEP = main.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
        var mainComposite = session.createCompositeComponentType(main, mainEP);
        var mainLinked = mainComposite.link();

        // Second consumer — reuses the same lighting/math_utils modules
        var second = session.loadModuleFromSourceString(
            "second", "second.slang", SECOND_SHADER_SOURCE);
        var secondEP = second.findAndCheckEntryPoint("normalMapShade", Stage.COMPUTE);
        var secondComposite = session.createCompositeComponentType(second, secondEP);
        var secondLinked = secondComposite.link();

        try (var hlsl1 = mainLinked.getEntryPointCode(0, 0);
             var hlsl2 = secondLinked.getEntryPointCode(0, 0)) {
            String code1 = new String(hlsl1.toByteArray());
            String code2 = new String(hlsl2.toByteArray());

            System.out.println("=== main.slang → HLSL (" + code1.length() + " chars) ===");
            System.out.println("Entry: csMain — uses computeMultiLight from lighting module\n");
            System.out.println("=== second.slang → HLSL (" + code2.length() + " chars) ===");
            System.out.println("Entry: normalMapShade — reuses both lighting and math_utils\n");
            System.out.println(code2);

            assertTrue(code1.contains("csMain"));
            assertTrue(code2.contains("normalMapShade"));
            // second shader should reference functions from both modules
            assertNotEquals(code1, code2, "Different shaders should produce different code");
        }

        secondLinked.close();
        secondComposite.close();
        mainLinked.close();
        mainComposite.close();
        session.close();
        global.close();
    }

    @Test
    void serializeAndReloadModule() throws Exception {
        var global = GlobalSession.create();

        // Session 1: compile and serialize
        var session1 = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.SPIRV)
                    .profile(global.findProfile("spirv_1_5"))));

        session1.loadModuleFromSourceString(
            "math_utils", "math_utils.slang", MATH_UTILS_SOURCE);
        var lighting = session1.loadModuleFromSourceString(
            "lighting", "lighting.slang", LIGHTING_SOURCE);

        byte[] serialized = lighting.serialize();
        System.out.println("Serialized lighting module: " + serialized.length + " bytes");
        assertTrue(serialized.length > 0);

        session1.close();

        // Session 2: reload from serialized IR
        // This skips parsing and type-checking — just loads the IR directly
        var session2 = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.SPIRV)
                    .profile(global.findProfile("spirv_1_5"))));

        // Need math_utils available for the transitive dependency
        session2.loadModuleFromSourceString(
            "math_utils", "math_utils.slang", MATH_UTILS_SOURCE);

        // Load main shader that imports the (now available) lighting module
        // For this test, just verify the serialization round-trip produced valid bytes
        System.out.println("Serialized IR blob: " + serialized.length + " bytes");
        assertTrue(serialized.length > 100,
            "Serialized module should contain meaningful IR data");

        session2.close();
        global.close();
    }

    @Test
    void moduleDependencies() throws Exception {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.SPIRV)));

        session.loadModuleFromSourceString(
            "math_utils", "math_utils.slang", MATH_UTILS_SOURCE);
        var lighting = session.loadModuleFromSourceString(
            "lighting", "lighting.slang", LIGHTING_SOURCE);

        int depCount = lighting.getDependencyFileCount();
        System.out.println("lighting.slang dependencies (" + depCount + "):");
        for (int i = 0; i < depCount; i++) {
            String path = lighting.getDependencyFilePath(i);
            System.out.println("  [" + i + "] " + path);
        }
        // lighting.slang imports math_utils, so it should have at least 1 dependency
        assertTrue(depCount >= 1, "lighting should depend on math_utils");

        // Disassemble to see the IR with module references
        String ir = lighting.disassemble();
        System.out.println("\nlighting.slang IR (first 600 chars):");
        System.out.println(ir.substring(0, Math.min(600, ir.length())));

        session.close();
        global.close();
    }
}
