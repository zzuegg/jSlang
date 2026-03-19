package dev.slang.jme;

import dev.slang.api.*;
import dev.slang.bindings.enums.CompileTarget;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SlangShaderGeneratorTest {

    private static final String SHADER = """
        uniform float4x4 WorldViewProjectionMatrix;
        uniform float roughness;
        Texture2D albedoTex;
        SamplerState linearSampler;

        struct VsIn {
            float3 position : POSITION;
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
            return float4(tex.rgb * roughness, 1.0);
        }
        """;

    static GlobalSession global;

    @BeforeAll
    static void setup() {
        global = GlobalSession.create();
    }

    @AfterAll
    static void teardown() {
        if (global != null) global.close();
    }

    @Test
    void compilesVertexAndFragmentGlsl() throws Exception {
        var generator = new SlangShaderGenerator(global);
        var result = generator.compile("test", SHADER,
            "vertexMain", "fragmentMain", Map.of());
        assertNotNull(result.vertexGlsl());
        assertNotNull(result.fragmentGlsl());
        assertTrue(result.vertexGlsl().contains("gl_Position"));
        assertTrue(result.fragmentGlsl().contains("void main"));
    }

    @Test
    void compilesWithDefines() throws Exception {
        String shaderWithDefine = """
            uniform float4x4 WorldViewProjectionMatrix;
            uniform float roughness;

            struct VsIn { float3 position : POSITION; };
            struct VsOut { float4 pos : SV_Position; };

            [shader("vertex")]
            VsOut vertexMain(VsIn input) {
                VsOut o;
                o.pos = mul(WorldViewProjectionMatrix, float4(input.position, 1.0));
                return o;
            }

            [shader("fragment")]
            float4 fragmentMain(VsOut input) : SV_Target {
                #ifdef HAS_FEATURE
                return float4(1,1,1,1);
                #else
                return float4(roughness, roughness, roughness, 1.0);
                #endif
            }
            """;

        var generator = new SlangShaderGenerator(global);
        var withDefine = generator.compile("test1", shaderWithDefine,
            "vertexMain", "fragmentMain", Map.of("HAS_FEATURE", "1"));
        var withoutDefine = generator.compile("test2", shaderWithDefine,
            "vertexMain", "fragmentMain", Map.of());

        // The two variants should produce different fragment shaders
        assertNotEquals(withDefine.fragmentGlsl(), withoutDefine.fragmentGlsl());
    }

    @Test
    void returnsReflectionData() throws Exception {
        var generator = new SlangShaderGenerator(global);
        var result = generator.compileWithReflection("test", SHADER,
            "vertexMain", "fragmentMain", Map.of(), java.util.List.of());
        assertNotNull(result.reflection());
        assertFalse(result.reflection().materialParams().isEmpty());
    }
}
