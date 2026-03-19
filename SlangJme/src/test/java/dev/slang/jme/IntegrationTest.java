package dev.slang.jme;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.TechniqueDef;
import dev.slang.api.GlobalSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    private static final String SHADER = """
        uniform float4x4 WorldViewProjectionMatrix;
        uniform float roughness;
        uniform float3 baseColor;
        Texture2D albedoTex;
        SamplerState linearSampler;

        struct VsIn {
            float3 position : POSITION;
            float3 normal   : NORMAL;
            float2 uv       : TEXCOORD0;
        };
        struct VsOut {
            float4 pos     : SV_Position;
            float3 normal  : NORMAL;
            float2 uv      : TEXCOORD0;
        };

        [shader("vertex")]
        VsOut vertexMain(VsIn input) {
            VsOut o;
            o.pos = mul(WorldViewProjectionMatrix, float4(input.position, 1.0));
            o.normal = input.normal;
            o.uv = input.uv;
            return o;
        }

        [shader("fragment")]
        float4 fragmentMain(VsOut input) : SV_Target {
            float4 tex = albedoTex.Sample(linearSampler, input.uv);
            return float4(tex.rgb * baseColor * roughness, 1.0);
        }
        """;

    private static final String SHADOW_SHADER = """
        uniform float4x4 WorldViewProjectionMatrix;

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
            return float4(1, 1, 1, 1);
        }
        """;

    static GlobalSession global;
    static AssetManager assetManager;

    @BeforeAll
    static void setup() {
        global = GlobalSession.create();
        assetManager = new DesktopAssetManager(true);
    }

    @AfterAll
    static void teardown() {
        if (global != null) global.close();
    }

    @Test
    void postProcessedGlslHasJmeConventions() throws Exception {
        var generator = new SlangShaderGenerator(global);
        var postProcessor = new GlslPostProcessor();

        var compiled = generator.compileWithReflection("inspect", SHADER,
            "vertexMain", "fragmentMain", Map.of(), java.util.List.of());

        var matNames = new java.util.HashSet<String>();
        for (var p : compiled.reflection().materialParams()) matNames.add(p.name());
        var worldNames = new java.util.HashSet<String>();
        for (var b : compiled.reflection().worldBindings()) worldNames.add(b.name());

        String vertexGlsl = postProcessor.process(
            compiled.sources().vertexGlsl(), matNames, worldNames);
        String fragmentGlsl = postProcessor.process(
            compiled.sources().fragmentGlsl(), matNames, worldNames);

        System.out.println("=== POST-PROCESSED VERTEX GLSL ===");
        System.out.println(vertexGlsl);
        System.out.println("=== POST-PROCESSED FRAGMENT GLSL ===");
        System.out.println(fragmentGlsl);

        // World params should have g_ prefix
        assertTrue(vertexGlsl.contains("g_WorldViewProjectionMatrix"),
            "Vertex shader should contain g_WorldViewProjectionMatrix");

        // Material params should have m_ prefix
        assertTrue(fragmentGlsl.contains("m_roughness"),
            "Fragment shader should contain m_roughness");
        assertTrue(fragmentGlsl.contains("m_baseColor"),
            "Fragment shader should contain m_baseColor");

        // Textures should be combined sampler2D
        assertTrue(fragmentGlsl.contains("uniform sampler2D m_albedoTex"),
            "Fragment shader should have combined sampler2D for albedoTex");

        // Version should be adjusted
        assertTrue(vertexGlsl.contains("#version 330"),
            "Should use GLSL 330");

        // Vertex attributes should be remapped to jME conventions
        assertTrue(vertexGlsl.contains("inPosition"),
            "Vertex inputs should use jME naming (inPosition)");
    }

    @Test
    void endToEndMaterialWithModes() throws Exception {
        try (var system = new SlangMaterialSystem(assetManager)) {
            system.registerModeFromSource("Shadow", SHADOW_SHADER,
                ModeConfig.builder()
                    .shadowMode(TechniqueDef.ShadowMode.PostPass)
                    .build());

            Material mat = system.loadMaterialFromSource("PBR", SHADER,
                SlangTechniqueConfig.builder()
                    .mode("Shadow")
                    .build());

            // Set parameters
            mat.setFloat("roughness", 0.5f);

            // Verify material is functional
            assertNotNull(mat.getMaterialDef());
            assertEquals(0.5f, (float) mat.getParamValue("roughness"));
            assertNotNull(mat.getMaterialDef().getTechniqueDefs("Default"));
            assertNotNull(mat.getMaterialDef().getTechniqueDefs("Shadow"));
        }
    }
}
