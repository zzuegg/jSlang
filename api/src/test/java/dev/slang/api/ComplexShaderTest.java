package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComplexShaderTest {

    private static final String BUFFER_COMPUTE_SHADER = """
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
            float3 worldPos    : TEXCOORD1;
        };

        struct MaterialParams
        {
            float4 baseColor;
            float roughness;
            float metallic;
        };

        uniform float4x4 modelViewProjection;
        uniform float4x4 modelMatrix;
        uniform float3 lightDir;
        uniform MaterialParams material;
        Texture2D albedoTex;
        SamplerState linearSampler;

        [shader("vertex")]
        VertexOutput vertexMain(VertexInput input)
        {
            VertexOutput output;
            output.sv_position = mul(modelViewProjection, float4(input.position, 1.0));
            output.worldNormal = mul((float3x3)modelMatrix, input.normal);
            output.uv = input.uv;
            output.worldPos = mul(modelMatrix, float4(input.position, 1.0)).xyz;
            return output;
        }

        [shader("fragment")]
        float4 fragmentMain(VertexOutput input) : SV_Target
        {
            float3 N = normalize(input.worldNormal);
            float3 L = normalize(-lightDir);
            float NdotL = saturate(dot(N, L));

            float4 texColor = albedoTex.Sample(linearSampler, input.uv);
            float3 diffuse = texColor.rgb * material.baseColor.rgb * NdotL;
            float3 ambient = texColor.rgb * material.baseColor.rgb * 0.1;

            return float4(diffuse + ambient, texColor.a);
        }
        """;

    @Test
    void compileParticleComputeToSpirv() {
        var global = GlobalSession.create();
        int profile = global.findProfile("spirv_1_5");

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));

        var module = session.loadModuleFromSourceString(
            "particles", "particles.slang", BUFFER_COMPUTE_SHADER);
        assertNotNull(module);
        assertEquals("particles", module.getName());

        var entryPoint = module.findAndCheckEntryPoint("updateParticles", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, entryPoint);
        var linked = composite.link();

        try (var spirv = linked.getEntryPointCode(0, 0)) {
            byte[] bytes = spirv.toByteArray();
            assertTrue(bytes.length > 200,
                "Complex compute shader SPIRV should be substantial, got " + bytes.length + " bytes");

            int magic = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
            assertEquals(0x07230203, magic);
            System.out.println("Particle compute SPIRV: " + bytes.length + " bytes");
        }

        linked.close();
        composite.close();
        session.close();
        global.close();
    }

    @Test
    void compileParticleComputeToHLSL() {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.HLSL)));

        var module = session.loadModuleFromSourceString(
            "particles-hlsl", "particles.slang", BUFFER_COMPUTE_SHADER);
        var entryPoint = module.findAndCheckEntryPoint("updateParticles", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, entryPoint);
        var linked = composite.link();

        try (var hlsl = linked.getEntryPointCode(0, 0)) {
            String code = new String(hlsl.toByteArray());
            assertTrue(code.contains("updateParticles"), "Should contain entry point name");
            assertTrue(code.contains("Particle") || code.contains("position"),
                "Should reference struct fields");
            assertTrue(code.contains("deltaTime") || code.contains("particleCount"),
                "Should reference uniform params");
            System.out.println("Particle compute HLSL (" + code.length() + " chars):\n" + code);
        }

        linked.close();
        composite.close();
        session.close();
        global.close();
    }

    @Test
    void compileVertexFragmentToSpirv() {
        var global = GlobalSession.create();
        int profile = global.findProfile("spirv_1_5");

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));

        var module = session.loadModuleFromSourceString(
            "material", "material.slang", VERTEX_FRAGMENT_SHADER);

        // Compile vertex shader
        var vertexEP = module.findAndCheckEntryPoint("vertexMain", Stage.VERTEX);
        var vertexComposite = session.createCompositeComponentType(module, vertexEP);
        var vertexLinked = vertexComposite.link();

        try (var spirv = vertexLinked.getEntryPointCode(0, 0)) {
            byte[] bytes = spirv.toByteArray();
            assertTrue(bytes.length > 100, "Vertex SPIRV should be non-trivial, got " + bytes.length);
            int magic = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
            assertEquals(0x07230203, magic);
            System.out.println("Vertex SPIRV: " + bytes.length + " bytes");
        }

        vertexLinked.close();
        vertexComposite.close();

        // Compile fragment shader
        var fragmentEP = module.findAndCheckEntryPoint("fragmentMain", Stage.FRAGMENT);
        var fragmentComposite = session.createCompositeComponentType(module, fragmentEP);
        var fragmentLinked = fragmentComposite.link();

        try (var spirv = fragmentLinked.getEntryPointCode(0, 0)) {
            byte[] bytes = spirv.toByteArray();
            assertTrue(bytes.length > 100, "Fragment SPIRV should be non-trivial, got " + bytes.length);
            int magic = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
            assertEquals(0x07230203, magic);
            System.out.println("Fragment SPIRV: " + bytes.length + " bytes");
        }

        fragmentLinked.close();
        fragmentComposite.close();
        session.close();
        global.close();
    }

    @Test
    void compileVertexFragmentToGLSL() {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.GLSL)));

        var module = session.loadModuleFromSourceString(
            "material-glsl", "material.slang", VERTEX_FRAGMENT_SHADER);

        var vertexEP = module.findAndCheckEntryPoint("vertexMain", Stage.VERTEX);
        var vertexComposite = session.createCompositeComponentType(module, vertexEP);
        var vertexLinked = vertexComposite.link();

        try (var glsl = vertexLinked.getEntryPointCode(0, 0)) {
            String code = new String(glsl.toByteArray());
            assertTrue(code.contains("#version"), "Should be valid GLSL");
            assertTrue(code.contains("gl_Position") || code.contains("main"),
                "Vertex shader should set position");
            System.out.println("Vertex GLSL (" + code.length() + " chars):\n" + code);
        }

        vertexLinked.close();
        vertexComposite.close();
        session.close();
        global.close();
    }
}
