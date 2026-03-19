package dev.slang.jme;

import com.jme3.shader.VarType;
import dev.slang.api.*;
import dev.slang.api.reflect.*;
import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionMapperTest {

    private static final String SHADER = """
        uniform float4x4 WorldViewProjectionMatrix;
        uniform float3 CameraPosition;
        uniform float roughness;
        uniform float3 baseColor;
        uniform bool useNormalMap;
        Texture2D albedoTex;
        SamplerState linearSampler;

        struct VsIn {
            float3 position : POSITION;
            float3 normal   : NORMAL;
            float2 uv       : TEXCOORD0;
        };
        struct VsOut {
            float4 pos : SV_Position;
            float2 uv  : TEXCOORD0;
        };

        [shader("vertex")]
        VsOut vertexMain(VsIn input) {
            VsOut o;
            o.pos = mul(WorldViewProjectionMatrix, float4(input.position, 1.0));
            o.uv = input.uv;
            return o;
        }

        [shader("fragment")]
        float4 fragmentMain(VsOut input) : SV_Target {
            float4 tex = albedoTex.Sample(linearSampler, input.uv);
            return float4(tex.rgb * baseColor * roughness, 1.0);
        }
        """;

    static GlobalSession global;
    static ProgramLayout layout;
    static ComponentType linked;
    static Session session;

    @BeforeAll
    static void setup() throws Exception {
        global = GlobalSession.create();
        int profile = global.findProfile("glsl_330");
        session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.GLSL)
                    .profile(profile)));
        var module = session.loadModuleFromSourceString("test", "test.slang", SHADER);
        var vsEp = module.findAndCheckEntryPoint("vertexMain", Stage.VERTEX);
        var fsEp = module.findAndCheckEntryPoint("fragmentMain", Stage.FRAGMENT);
        var composite = session.createCompositeComponentType(module, vsEp, fsEp);
        linked = composite.link();
        layout = linked.getLayout(0);
    }

    @AfterAll
    static void teardown() {
        if (linked != null) linked.close();
        if (session != null) session.close();
        if (global != null) global.close();
    }

    @Test
    void mapsScalarFloat() {
        var mapper = new ReflectionMapper();
        var params = mapper.extractParameters(layout);
        var roughnessParam = params.stream()
            .filter(p -> p.name().equals("roughness")).findFirst().orElseThrow();
        assertEquals(VarType.Float, roughnessParam.varType());
    }

    @Test
    void mapsVector3() {
        var mapper = new ReflectionMapper();
        var params = mapper.extractParameters(layout);
        var baseColorParam = params.stream()
            .filter(p -> p.name().equals("baseColor")).findFirst().orElseThrow();
        assertEquals(VarType.Vector3, baseColorParam.varType());
    }

    @Test
    void mapsBoolean() {
        var mapper = new ReflectionMapper();
        var params = mapper.extractParameters(layout);
        var useNormalMapParam = params.stream()
            .filter(p -> p.name().equals("useNormalMap")).findFirst().orElseThrow();
        assertEquals(VarType.Boolean, useNormalMapParam.varType());
    }

    @Test
    void mapsTexture2D() {
        var mapper = new ReflectionMapper();
        var params = mapper.extractParameters(layout);
        var albedoParam = params.stream()
            .filter(p -> p.name().equals("albedoTex")).findFirst().orElseThrow();
        assertEquals(VarType.Texture2D, albedoParam.varType());
    }

    @Test
    void identifiesWorldParams() {
        var mapper = new ReflectionMapper();
        var worldBindings = mapper.extractWorldBindings(layout);
        var names = worldBindings.stream()
            .map(Enum::name).toList();
        assertTrue(names.contains("WorldViewProjectionMatrix"));
        assertTrue(names.contains("CameraPosition"));
    }

    @Test
    void excludesWorldParamsFromMaterialParams() {
        var mapper = new ReflectionMapper();
        var params = mapper.extractParameters(layout);
        var names = params.stream().map(ReflectionMapper.MatParamMapping::name).toList();
        assertFalse(names.contains("WorldViewProjectionMatrix"));
        assertFalse(names.contains("CameraPosition"));
    }

    @Test
    void excludesSamplerState() {
        var mapper = new ReflectionMapper();
        var params = mapper.extractParameters(layout);
        var names = params.stream().map(ReflectionMapper.MatParamMapping::name).toList();
        assertFalse(names.contains("linearSampler"));
    }

    @Test
    void generatesDefinesForBooleansAndTextures() {
        var mapper = new ReflectionMapper();
        var defines = mapper.extractDefines(layout);
        assertTrue(defines.containsKey("useNormalMap"));
        assertTrue(defines.containsKey("albedoTex"));
    }
}
