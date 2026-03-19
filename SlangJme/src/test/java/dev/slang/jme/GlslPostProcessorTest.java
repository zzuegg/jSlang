package dev.slang.jme;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GlslPostProcessorTest {

    // Actual Slang GLSL output fragment (simplified)
    private static final String VERTEX_GLSL = """
        #version 450
        layout(column_major) uniform;
        layout(column_major) buffer;

        struct GlobalParams_0
        {
            mat4x4 WorldViewProjectionMatrix_0;
            float roughness_0;
            vec3 baseColor_0;
        };

        layout(binding = 0)
        layout(std140) uniform block_GlobalParams_0
        {
            mat4x4 WorldViewProjectionMatrix_0;
            float roughness_0;
            vec3 baseColor_0;
        }globalParams_0;

        layout(location = 0)
        out vec2 entryPointParam_vertexMain_uv_0;

        layout(location = 0)
        in vec3 input_position_0;

        layout(location = 2)
        in vec2 input_uv_0;

        void main()
        {
            gl_Position = vec4(input_position_0, 1.0) * globalParams_0.WorldViewProjectionMatrix_0;
            entryPointParam_vertexMain_uv_0 = input_uv_0;
        }
        """;

    private static final String FRAGMENT_GLSL = """
        #version 450
        layout(column_major) uniform;
        layout(column_major) buffer;

        layout(binding = 1)
        uniform texture2D albedoTex_0;

        layout(binding = 2)
        uniform sampler linearSampler_0;

        struct GlobalParams_0
        {
            mat4x4 WorldViewProjectionMatrix_0;
            float roughness_0;
            vec3 baseColor_0;
        };

        layout(binding = 0)
        layout(std140) uniform block_GlobalParams_0
        {
            mat4x4 WorldViewProjectionMatrix_0;
            float roughness_0;
            vec3 baseColor_0;
        }globalParams_0;

        layout(location = 0)
        out vec4 entryPointParam_fragmentMain_0;

        layout(location = 0)
        in vec2 input_uv_0;

        void main()
        {
            entryPointParam_fragmentMain_0 = vec4(texture(sampler2D(albedoTex_0,linearSampler_0), input_uv_0).xyz * globalParams_0.baseColor_0 * globalParams_0.roughness_0, 1.0);
        }
        """;

    @Test
    void convertsUboToLooseUniforms() {
        var processor = new GlslPostProcessor();
        String result = processor.convertUboToLooseUniforms(VERTEX_GLSL,
            Set.of("roughness", "baseColor"),
            Set.of("WorldViewProjectionMatrix"));
        assertTrue(result.contains("uniform mat4x4 g_WorldViewProjectionMatrix;"));
        assertTrue(result.contains("uniform float m_roughness;"));
        assertTrue(result.contains("uniform vec3 m_baseColor;"));
        assertFalse(result.contains("block_GlobalParams_0"));
    }

    @Test
    void replacesUboReferences() {
        var processor = new GlslPostProcessor();
        String result = processor.convertUboToLooseUniforms(VERTEX_GLSL,
            Set.of("roughness", "baseColor"),
            Set.of("WorldViewProjectionMatrix"));
        assertTrue(result.contains("g_WorldViewProjectionMatrix"));
        assertFalse(result.contains("globalParams_0."));
    }

    @Test
    void combinesSeparateTextureSampler() {
        var processor = new GlslPostProcessor();
        String result = processor.combineSeparateTextureSampler(FRAGMENT_GLSL);
        assertTrue(result.contains("uniform sampler2D m_albedoTex;"));
        assertFalse(result.contains("uniform texture2D"));
        assertFalse(result.contains("uniform sampler linearSampler"));
    }

    @Test
    void replacesCombinedSamplerCall() {
        var processor = new GlslPostProcessor();
        String result = processor.combineSeparateTextureSampler(FRAGMENT_GLSL);
        assertTrue(result.contains("texture(m_albedoTex,"));
        assertFalse(result.contains("sampler2D(albedoTex_0"));
    }

    @Test
    void remapsVertexAttributes() {
        var processor = new GlslPostProcessor();
        String result = processor.remapAttributes(VERTEX_GLSL);
        assertTrue(result.contains("inPosition"));
        assertTrue(result.contains("inTexCoord"));
        assertFalse(result.contains("input_position_0"));
        assertFalse(result.contains("input_uv_0"));
    }

    @Test
    void adjustsGlslVersion() {
        var processor = new GlslPostProcessor();
        String result = processor.adjustVersion(VERTEX_GLSL);
        assertTrue(result.contains("#version 330"));
        assertFalse(result.contains("#version 450"));
        assertFalse(result.contains("layout(column_major)"));
    }

    @Test
    void fullProcessProducesJmeCompatibleGlsl() {
        var processor = new GlslPostProcessor();
        String result = processor.process(FRAGMENT_GLSL,
            Set.of("roughness", "baseColor", "albedoTex"),
            Set.of("WorldViewProjectionMatrix"));
        assertTrue(result.contains("#version 330"));
        assertTrue(result.contains("uniform sampler2D m_albedoTex;"));
        assertTrue(result.contains("m_roughness"));
        assertTrue(result.contains("m_baseColor"));
        assertFalse(result.contains("globalParams_0"));
        assertFalse(result.contains("texture2D albedoTex_0"));
    }
}
