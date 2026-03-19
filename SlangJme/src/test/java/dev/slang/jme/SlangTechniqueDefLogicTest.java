package dev.slang.jme;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.TechniqueDef;
import com.jme3.renderer.Caps;
import com.jme3.shader.DefineList;
import com.jme3.shader.Shader;
import com.jme3.shader.VarType;
import dev.slang.api.GlobalSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class SlangTechniqueDefLogicTest {

    private static final String SHADER = """
        uniform float4x4 WorldViewProjectionMatrix;
        uniform float roughness;

        struct VsIn { float3 position : POSITION; float2 uv : TEXCOORD0; };
        struct VsOut { float4 pos : SV_Position; float2 uv : TEXCOORD0; };

        [shader("vertex")]
        VsOut vertexMain(VsIn input) {
            VsOut o;
            o.pos = mul(WorldViewProjectionMatrix, float4(input.position, 1.0));
            o.uv = input.uv;
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
    void producesShaderFromDefineList() {
        var generator = new SlangShaderGenerator(global);
        var postProcessor = new GlslPostProcessor();
        var techniqueDef = new TechniqueDef("Default", 0);

        var logic = new SlangTechniqueDefLogic(
            techniqueDef, null, generator, postProcessor,
            "test", SHADER, "vertexMain", "fragmentMain");

        var defines = techniqueDef.createDefineList();
        var caps = EnumSet.of(Caps.OpenGL33);

        Shader shader = logic.makeCurrent(
            new DesktopAssetManager(true), null, caps, null, defines);
        assertNotNull(shader);
    }

    @Test
    void cachesShaderForSameDefines() {
        var generator = new SlangShaderGenerator(global);
        var postProcessor = new GlslPostProcessor();
        var techniqueDef = new TechniqueDef("Default", 0);

        var logic = new SlangTechniqueDefLogic(
            techniqueDef, null, generator, postProcessor,
            "test", SHADER, "vertexMain", "fragmentMain");

        var defines = techniqueDef.createDefineList();
        var caps = EnumSet.of(Caps.OpenGL33);
        var am = new DesktopAssetManager(true);

        Shader first = logic.makeCurrent(am, null, caps, null, defines);
        Shader second = logic.makeCurrent(am, null, caps, null, defines);
        assertSame(first, second, "Same defines should return cached shader");
    }

    @Test
    void differentDefinesProduceDifferentShaders() {
        var generator = new SlangShaderGenerator(global);
        var postProcessor = new GlslPostProcessor();
        var techniqueDef = new TechniqueDef("Default", 0);
        int defineId = techniqueDef.addShaderUnmappedDefine("HAS_FEATURE", VarType.Boolean);

        var logic = new SlangTechniqueDefLogic(
            techniqueDef, null, generator, postProcessor,
            "test", SHADER, "vertexMain", "fragmentMain");

        var caps = EnumSet.of(Caps.OpenGL33);
        var am = new DesktopAssetManager(true);

        var definesOff = techniqueDef.createDefineList();
        Shader shaderOff = logic.makeCurrent(am, null, caps, null, definesOff);

        var definesOn = techniqueDef.createDefineList();
        definesOn.set(defineId, true);
        Shader shaderOn = logic.makeCurrent(am, null, caps, null, definesOn);

        assertNotSame(shaderOff, shaderOn, "Different defines should produce different shaders");
    }
}
