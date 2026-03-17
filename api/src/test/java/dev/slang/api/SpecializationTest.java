package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.raw.IComponentType;
import dev.slang.bindings.raw.SlangReflection;
import org.junit.jupiter.api.Test;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;

class SpecializationTest {

    private static final String GENERIC_SHADER = """
        interface IMaterial
        {
            float3 evaluate(float3 normal, float3 lightDir);
        };

        struct LambertMaterial : IMaterial
        {
            float3 albedo;

            float3 evaluate(float3 normal, float3 lightDir)
            {
                return albedo * max(0.0, dot(normal, lightDir));
            }
        };

        struct PhongMaterial : IMaterial
        {
            float3 diffuseColor;
            float3 specularColor;
            float  shininess;

            float3 evaluate(float3 normal, float3 lightDir)
            {
                float NdotL = max(0.0, dot(normal, lightDir));
                float3 reflected = reflect(-lightDir, normal);
                float spec = pow(max(0.0, reflected.z), shininess);
                return diffuseColor * NdotL + specularColor * spec;
            }
        };

        [shader("compute")]
        [numthreads(64, 1, 1)]
        void shadeMaterial<T : IMaterial>(
            uint3 tid : SV_DispatchThreadID,
            uniform StructuredBuffer<T> materials,
            uniform StructuredBuffer<float3> normals,
            uniform float3 lightDir,
            uniform RWStructuredBuffer<float3> output)
        {
            T mat = materials[tid.x];
            float3 n = normalize(normals[tid.x]);
            output[tid.x] = mat.evaluate(n, normalize(lightDir));
        }
        """;

    private IComponentType specializeWith(IComponentType composite, Arena arena, String typeName) {
        MemorySegment layout = composite.getLayout(arena, 0);
        MemorySegment type = SlangReflection.findTypeByName(layout, arena, typeName);
        assertFalse(type.equals(MemorySegment.NULL), typeName + " should be found");
        System.out.println("Specializing with: " + SlangReflection.getTypeName(type));

        // SpecializationArg: { int32_t kind=0 (Type); SlangReflectionType* type }
        // Try natural alignment: kind at 0, type at 8 (with padding)
        // But also try packed: kind at 0, type at 4
        // Use 16 bytes to be safe
        MemorySegment specArgs = arena.allocate(16);
        specArgs.set(ValueLayout.JAVA_INT, 0, 1); // kind=1 (Type), not 0 (Unknown)
        specArgs.set(ValueLayout.ADDRESS, 8, type);

        return composite.specialize(arena, specArgs, 1);
    }

    @Test
    void specializeGenericShader() throws Exception {
        System.out.println("=== SLANG INPUT (Generic Shader) ===");
        System.out.println(GENERIC_SHADER);

        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.HLSL)));

        var module = session.loadModuleFromSourceString(
            "generic", "generic.slang", GENERIC_SHADER);
        var entryPoint = module.findEntryPoint("shadeMaterial");
        var composite = session.createCompositeComponentType(module, entryPoint);

        long paramCount = composite.getSpecializationParamCount();
        System.out.println("Specialization parameters: " + paramCount);
        assertTrue(paramCount > 0, "Generic shader should have specialization parameters");

        try (Arena arena = Arena.ofConfined()) {
            // --- Specialize with LambertMaterial ---
            IComponentType lambertSpecialized = specializeWith(composite.raw(), arena, "LambertMaterial");
            IComponentType lambertLinked = lambertSpecialized.link(arena);

            var lambertBlob = lambertLinked.getEntryPointCode(arena, 0, 0);
            String lambertHlsl = new String(lambertBlob.toByteArray());

            System.out.println("\n=== SPECIALIZED: shadeMaterial<LambertMaterial> → HLSL ===");
            System.out.println(lambertHlsl);

            assertTrue(lambertHlsl.contains("albedo"),
                "Lambert HLSL should reference albedo field");

            lambertBlob.close();
            lambertLinked.close();
            lambertSpecialized.close();

            // --- Specialize with PhongMaterial ---
            IComponentType phongSpecialized = specializeWith(composite.raw(), arena, "PhongMaterial");
            IComponentType phongLinked = phongSpecialized.link(arena);

            var phongBlob = phongLinked.getEntryPointCode(arena, 0, 0);
            String phongHlsl = new String(phongBlob.toByteArray());

            System.out.println("\n=== SPECIALIZED: shadeMaterial<PhongMaterial> → HLSL ===");
            System.out.println(phongHlsl);

            assertTrue(phongHlsl.contains("shininess") || phongHlsl.contains("specularColor"),
                "Phong HLSL should reference Phong-specific fields");

            // The two outputs should be different
            assertNotEquals(lambertHlsl, phongHlsl,
                "Different specializations should produce different code");

            phongBlob.close();
            phongLinked.close();
            phongSpecialized.close();
        }

        composite.close();
        session.close();
        global.close();
    }
}
