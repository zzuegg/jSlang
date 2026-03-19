package dev.slang.jme;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.material.TechniqueDef;
import com.jme3.shader.VarType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlangMaterialSystemTest {

    private static final String PBR_SHADER = """
        uniform float4x4 WorldViewProjectionMatrix;
        uniform float3 CameraPosition;

        uniform float roughness;
        uniform float metallic;
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
            float3 color = tex.rgb * baseColor * (1.0 - metallic) * roughness;
            return float4(color, 1.0);
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

    static AssetManager assetManager;

    @BeforeAll
    static void setup() {
        assetManager = new DesktopAssetManager(true);
    }

    @Test
    void loadsMaterialDefWithAutoParameters() throws Exception {
        try (var system = new SlangMaterialSystem(assetManager)) {
            MaterialDef matDef = system.loadMaterialDefFromSource("PBR", PBR_SHADER,
                SlangTechniqueConfig.builder().build());

            assertNotNull(matDef);

            // Should have auto-discovered material params
            assertNotNull(matDef.getMaterialParam("roughness"));
            assertNotNull(matDef.getMaterialParam("metallic"));
            assertNotNull(matDef.getMaterialParam("baseColor"));
            assertNotNull(matDef.getMaterialParam("useNormalMap"));
            assertNotNull(matDef.getMaterialParam("albedoTex"));

            // Should NOT have world params as material params
            assertNull(matDef.getMaterialParam("WorldViewProjectionMatrix"));
            assertNull(matDef.getMaterialParam("CameraPosition"));
        }
    }

    @Test
    void materialDefHasDefaultTechnique() throws Exception {
        try (var system = new SlangMaterialSystem(assetManager)) {
            MaterialDef matDef = system.loadMaterialDefFromSource("PBR", PBR_SHADER,
                SlangTechniqueConfig.builder().build());

            var techniques = matDef.getTechniqueDefs("Default");
            assertNotNull(techniques);
            assertFalse(techniques.isEmpty());
        }
    }

    @Test
    void registersModeAndAppliesTechnique() throws Exception {
        try (var system = new SlangMaterialSystem(assetManager)) {
            system.registerModeFromSource("Shadow", SHADOW_SHADER,
                ModeConfig.builder()
                    .shadowMode(TechniqueDef.ShadowMode.PostPass)
                    .build());

            MaterialDef matDef = system.loadMaterialDefFromSource("PBR", PBR_SHADER,
                SlangTechniqueConfig.builder()
                    .mode("Shadow")
                    .build());

            assertNotNull(matDef.getTechniqueDefs("Default"));
            assertNotNull(matDef.getTechniqueDefs("Shadow"));
        }
    }

    @Test
    void loadsMaterialInstance() throws Exception {
        try (var system = new SlangMaterialSystem(assetManager)) {
            Material mat = system.loadMaterialFromSource("PBR", PBR_SHADER,
                SlangTechniqueConfig.builder().build());

            assertNotNull(mat);
            mat.setFloat("roughness", 0.5f);
            mat.setFloat("metallic", 0.0f);
            assertEquals(0.5f, (float) mat.getParamValue("roughness"));
        }
    }

    @Test
    void throwsOnUnknownMode() {
        try (var system = new SlangMaterialSystem(assetManager)) {
            assertThrows(IllegalArgumentException.class, () ->
                system.loadMaterialDefFromSource("PBR", PBR_SHADER,
                    SlangTechniqueConfig.builder()
                        .mode("NonExistent")
                        .build()));
        }
    }
}
