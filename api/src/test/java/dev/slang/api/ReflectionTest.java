package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import dev.slang.bindings.raw.SlangReflection;
import org.junit.jupiter.api.Test;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionTest {

    private static final String PARTICLE_SHADER = """
        struct Particle
        {
            float3 position;
            float3 velocity;
            float mass;
            float lifetime;
        };

        [shader("compute")]
        [numthreads(64, 1, 1)]
        void updateParticles(
            uint3 tid : SV_DispatchThreadID,
            uniform RWStructuredBuffer<Particle> particles,
            uniform StructuredBuffer<float3> forces,
            uniform float deltaTime,
            uniform uint particleCount)
        {
            if (tid.x >= particleCount)
                return;

            Particle p = particles[tid.x];
            float3 force = forces[tid.x];
            float3 acceleration = force / p.mass;
            p.velocity += acceleration * deltaTime;
            p.position += p.velocity * deltaTime;
            p.lifetime -= deltaTime;
            particles[tid.x] = p;
        }
        """;

    private static final String VERTEX_FRAGMENT_SHADER = """
        struct VertexInput
        {
            float3 position : POSITION;
            float3 normal   : NORMAL;
            float2 uv       : TEXCOORD0;
        };

        struct VertexOutput
        {
            float4 sv_position : SV_Position;
            float3 worldNormal : NORMAL;
            float2 uv          : TEXCOORD0;
        };

        uniform float4x4 modelViewProjection;
        uniform float3 lightDir;
        Texture2D albedoTex;
        SamplerState linearSampler;

        [shader("vertex")]
        VertexOutput vertexMain(VertexInput input)
        {
            VertexOutput output;
            output.sv_position = mul(modelViewProjection, float4(input.position, 1.0));
            output.worldNormal = input.normal;
            output.uv = input.uv;
            return output;
        }

        [shader("fragment")]
        float4 fragmentMain(VertexOutput input) : SV_Target
        {
            float3 N = normalize(input.worldNormal);
            float NdotL = saturate(dot(N, normalize(-lightDir)));
            float4 texColor = albedoTex.Sample(linearSampler, input.uv);
            return float4(texColor.rgb * NdotL, texColor.a);
        }
        """;

    @Test
    void reflectComputeShaderParameters() {
        var global = GlobalSession.create();
        int profile = global.findProfile("spirv_1_5");

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));

        var module = session.loadModuleFromSourceString(
            "particles", "particles.slang", PARTICLE_SHADER);
        var entryPoint = module.findAndCheckEntryPoint("updateParticles", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, entryPoint);
        var linked = composite.link();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment layout = linked.raw().getLayout(arena, 0);
            assertNotNull(layout);
            assertFalse(layout.equals(MemorySegment.NULL));

            // Check global parameters
            int paramCount = SlangReflection.getParameterCount(layout);
            System.out.println("Global parameter count: " + paramCount);

            List<String> paramNames = new ArrayList<>();
            for (int i = 0; i < paramCount; i++) {
                MemorySegment param = SlangReflection.getParameterByIndex(layout, i);
                MemorySegment variable = SlangReflection.getVariable(param);
                String name = SlangReflection.getVariableName(variable);
                paramNames.add(name);

                MemorySegment type = SlangReflection.getVariableType(variable);
                int kind = SlangReflection.getTypeKind(type);
                String typeName = SlangReflection.getTypeName(type);
                System.out.println("  param[" + i + "]: " + name
                    + " (kind=" + kind + ", type=" + typeName + ")");
            }

            // Check entry points
            int entryPointCount = SlangReflection.getEntryPointCount(layout);
            assertEquals(1, entryPointCount, "Should have 1 entry point");

            MemorySegment ep = SlangReflection.getEntryPointByIndex(layout, 0);
            String epName = SlangReflection.getEntryPointName(ep);
            assertEquals("updateParticles", epName);

            int stage = SlangReflection.getEntryPointStage(ep);
            assertEquals(Stage.COMPUTE.value(), stage, "Should be compute stage");

            // Check thread group size
            long[] threadGroupSize = SlangReflection.getComputeThreadGroupSize(arena, ep);
            assertEquals(64, threadGroupSize[0], "X thread group size");
            assertEquals(1, threadGroupSize[1], "Y thread group size");
            assertEquals(1, threadGroupSize[2], "Z thread group size");
            System.out.println("Thread group size: " + threadGroupSize[0]
                + "x" + threadGroupSize[1] + "x" + threadGroupSize[2]);

            // Check entry point parameters
            int epParamCount = SlangReflection.getEntryPointParameterCount(ep);
            System.out.println("Entry point parameter count: " + epParamCount);
            for (int i = 0; i < epParamCount; i++) {
                MemorySegment epParam = SlangReflection.getEntryPointParameterByIndex(ep, i);
                MemorySegment epVar = SlangReflection.getVariable(epParam);
                String name = SlangReflection.getVariableName(epVar);
                System.out.println("  ep param[" + i + "]: " + name);
            }
        }

        linked.close();
        composite.close();
        session.close();
        global.close();
    }

    @Test
    void reflectVertexFragmentParameters() {
        var global = GlobalSession.create();
        int profile = global.findProfile("spirv_1_5");

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));

        var module = session.loadModuleFromSourceString(
            "material", "material.slang", VERTEX_FRAGMENT_SHADER);

        var vertexEP = module.findAndCheckEntryPoint("vertexMain", Stage.VERTEX);
        var fragmentEP = module.findAndCheckEntryPoint("fragmentMain", Stage.FRAGMENT);
        var composite = session.createCompositeComponentType(module, vertexEP, fragmentEP);
        var linked = composite.link();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment layout = linked.raw().getLayout(arena, 0);

            // Global parameters (uniforms, textures, samplers)
            int paramCount = SlangReflection.getParameterCount(layout);
            System.out.println("Global parameter count: " + paramCount);

            List<String> paramNames = new ArrayList<>();
            for (int i = 0; i < paramCount; i++) {
                MemorySegment param = SlangReflection.getParameterByIndex(layout, i);
                MemorySegment variable = SlangReflection.getVariable(param);
                String name = SlangReflection.getVariableName(variable);
                paramNames.add(name);

                MemorySegment type = SlangReflection.getVariableType(variable);
                int kind = SlangReflection.getTypeKind(type);
                String typeName = SlangReflection.getTypeName(type);
                System.out.println("  param[" + i + "]: " + name
                    + " (kind=" + kind + ", type=" + typeName + ")");
            }

            // Should have 2 entry points
            int entryPointCount = SlangReflection.getEntryPointCount(layout);
            assertEquals(2, entryPointCount, "Should have vertex + fragment entry points");

            for (int i = 0; i < entryPointCount; i++) {
                MemorySegment ep = SlangReflection.getEntryPointByIndex(layout, i);
                String name = SlangReflection.getEntryPointName(ep);
                int stage = SlangReflection.getEntryPointStage(ep);
                String stageName = switch (stage) {
                    case 1 -> "VERTEX";
                    case 5 -> "FRAGMENT";
                    default -> "UNKNOWN(" + stage + ")";
                };
                System.out.println("Entry point[" + i + "]: " + name + " (" + stageName + ")");
            }

            // Verify vertex entry point
            MemorySegment vep = SlangReflection.getEntryPointByIndex(layout, 0);
            assertEquals("vertexMain", SlangReflection.getEntryPointName(vep));
            assertEquals(Stage.VERTEX.value(), SlangReflection.getEntryPointStage(vep));

            // Verify fragment entry point
            MemorySegment fep = SlangReflection.getEntryPointByIndex(layout, 1);
            assertEquals("fragmentMain", SlangReflection.getEntryPointName(fep));
            assertEquals(Stage.FRAGMENT.value(), SlangReflection.getEntryPointStage(fep));
        }

        linked.close();
        composite.close();
        session.close();
        global.close();
    }

    @Test
    void reflectStructFields() {
        var global = GlobalSession.create();
        int profile = global.findProfile("spirv_1_5");

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));

        var module = session.loadModuleFromSourceString(
            "particles-refl", "particles.slang", PARTICLE_SHADER);
        var entryPoint = module.findAndCheckEntryPoint("updateParticles", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, entryPoint);
        var linked = composite.link();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment layout = linked.raw().getLayout(arena, 0);

            // Entry point parameters contain the resource bindings
            MemorySegment ep = SlangReflection.getEntryPointByIndex(layout, 0);
            int epParamCount = SlangReflection.getEntryPointParameterCount(ep);
            System.out.println("Inspecting entry point parameters for struct types:");

            boolean foundParticleStruct = false;
            for (int i = 0; i < epParamCount; i++) {
                MemorySegment param = SlangReflection.getEntryPointParameterByIndex(ep, i);
                MemorySegment variable = SlangReflection.getVariable(param);
                String name = SlangReflection.getVariableName(variable);
                MemorySegment type = SlangReflection.getVariableType(variable);
                int kind = SlangReflection.getTypeKind(type);
                String typeName = SlangReflection.getTypeName(type);

                System.out.println("  " + name + ": kind=" + kind + " type=" + typeName);

                // If it's a resource type, check the element type for struct
                if (kind == SlangReflection.TYPE_KIND_RESOURCE) {
                    MemorySegment elemType = SlangReflection.getElementType(type);
                    if (!elemType.equals(MemorySegment.NULL)) {
                        int elemKind = SlangReflection.getTypeKind(elemType);
                        String elemName = SlangReflection.getTypeName(elemType);
                        System.out.println("    element type: " + elemName + " (kind=" + elemKind + ")");

                        if (elemKind == SlangReflection.TYPE_KIND_STRUCT) {
                            int fieldCount = SlangReflection.getTypeFieldCount(elemType);
                            System.out.println("    fields (" + fieldCount + "):");
                            assertTrue(fieldCount > 0, "Struct should have fields");

                            List<String> fieldNames = new ArrayList<>();
                            for (int f = 0; f < fieldCount; f++) {
                                MemorySegment field = SlangReflection.getTypeFieldByIndex(elemType, f);
                                String fieldName = SlangReflection.getVariableName(field);
                                fieldNames.add(fieldName);
                                MemorySegment fieldType = SlangReflection.getVariableType(field);
                                String fieldTypeName = SlangReflection.getTypeName(fieldType);
                                System.out.println("      " + fieldName + ": " + fieldTypeName);
                            }

                            // Verify Particle struct fields
                            if (elemName != null && elemName.equals("Particle")) {
                                foundParticleStruct = true;
                                assertTrue(fieldNames.contains("position"), "Particle should have 'position'");
                                assertTrue(fieldNames.contains("velocity"), "Particle should have 'velocity'");
                                assertTrue(fieldNames.contains("mass"), "Particle should have 'mass'");
                                assertTrue(fieldNames.contains("lifetime"), "Particle should have 'lifetime'");
                                assertEquals(4, fieldCount, "Particle should have 4 fields");
                            }
                        }
                    }
                }
            }

            assertTrue(foundParticleStruct, "Should find Particle struct via reflection");
        }

        linked.close();
        composite.close();
        session.close();
        global.close();
    }
}
