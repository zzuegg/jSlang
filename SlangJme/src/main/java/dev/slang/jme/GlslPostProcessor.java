package dev.slang.jme;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlslPostProcessor {

    // Slang GLSL vertex input pattern → jME attribute name
    private static final Map<String, String> ATTRIBUTE_MAP = Map.of(
        "input_position_0", "inPosition",
        "input_normal_0", "inNormal",
        "input_tangent_0", "inTangent",
        "input_uv_0", "inTexCoord",
        "input_uv_1", "inTexCoord2",
        "input_color_0", "inColor"
    );

    /**
     * Applies all post-processing to GLSL source to make it compatible with jME.
     * Handles:
     * - Converting Slang's UBO-based uniforms to loose uniforms with jME prefixes
     * - Remapping vertex attribute names
     * - Combining separate texture/sampler into sampler2D
     * - Adjusting GLSL version
     */
    public String process(String glsl, Set<String> materialParamNames,
                          Set<String> worldParamNames) {
        String result = glsl;
        result = convertUboToLooseUniforms(result, materialParamNames, worldParamNames);
        result = combineSeparateTextureSampler(result);
        result = remapAttributes(result);
        result = adjustVersion(result);
        return result;
    }

    /**
     * Replaces the GlobalParams UBO block with loose uniform declarations,
     * and renames all references from globalParams_0.Foo_0 to m_Foo or g_Foo.
     */
    String convertUboToLooseUniforms(String glsl, Set<String> materialParamNames,
                                             Set<String> worldParamNames) {
        String result = glsl;

        // Remove the struct definition for GlobalParams_0
        result = result.replaceAll(
            "(?s)struct GlobalParams_0\\s*\\{[^}]*\\};\\s*", "");

        // Remove the layout/uniform block declaration, extract member types
        // Match: layout(...) layout(std140) uniform block_GlobalParams_0 { ... }globalParams_0;
        Pattern uboPattern = Pattern.compile(
            "(?s)(?:#line \\d+\\s*\\n)?layout\\(binding = \\d+\\)\\s*\\n" +
            "layout\\(std140\\) uniform block_GlobalParams_0\\s*\\{([^}]*)\\}globalParams_0;");
        Matcher uboMatcher = uboPattern.matcher(result);
        if (uboMatcher.find()) {
            String members = uboMatcher.group(1);
            StringBuilder uniforms = new StringBuilder();
            // Parse each member line: "    mat4x4 WorldViewProjectionMatrix_0;"
            for (String line : members.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // Extract type and name
                Pattern memberPattern = Pattern.compile("(\\S+)\\s+(\\w+)_0;");
                Matcher memberMatcher = memberPattern.matcher(line);
                if (memberMatcher.find()) {
                    String glslType = memberMatcher.group(1);
                    String originalName = memberMatcher.group(2);
                    String prefix = worldParamNames.contains(originalName) ? "g_" : "m_";
                    uniforms.append("uniform ").append(glslType).append(" ")
                        .append(prefix).append(originalName).append(";\n");
                }
            }
            result = uboMatcher.replaceFirst(Matcher.quoteReplacement(uniforms.toString()));
        }

        // Replace all references: globalParams_0.Foo_0 → m_Foo or g_Foo
        for (String name : worldParamNames) {
            result = result.replace("globalParams_0." + name + "_0", "g_" + name);
        }
        for (String name : materialParamNames) {
            result = result.replace("globalParams_0." + name + "_0", "m_" + name);
        }

        // Remove any remaining #line directives referencing the removed struct
        result = result.replaceAll("#line \\d+ 0\\s*\\n(?=\\s*\\n)", "");

        return result;
    }

    /**
     * Combines separate texture2D + sampler into sampler2D.
     * Slang emits: uniform texture2D albedoTex_0; uniform sampler linearSampler_0;
     * And uses: sampler2D(albedoTex_0, linearSampler_0)
     * jME expects: uniform sampler2D m_albedoTex;
     */
    String combineSeparateTextureSampler(String glsl) {
        String result = glsl;

        // Replace "uniform texture2D foo_0;" with "uniform sampler2D m_foo;"
        // and remove the layout(binding=N) prefix
        result = result.replaceAll(
            "(?:#line \\d+ \\d+\\s*\\n)?layout\\(binding = \\d+\\)\\s*\\n" +
            "uniform texture2D (\\w+)_0;",
            "uniform sampler2D m_$1;");

        // Remove separate sampler declarations entirely
        result = result.replaceAll(
            "(?:#line \\d+ \\d+\\s*\\n)?layout\\(binding = \\d+\\)\\s*\\n" +
            "uniform sampler (\\w+)_0;\\s*\\n?", "");

        // Replace sampler2D(texName_0, samplerName_0) with just m_texName
        result = result.replaceAll(
            "sampler2D\\((\\w+)_0\\s*,\\s*\\w+_0\\)",
            "m_$1");

        return result;
    }

    /**
     * Remaps Slang-generated vertex attribute names to jME conventions.
     */
    public String remapAttributes(String glsl) {
        String result = glsl;
        for (var entry : ATTRIBUTE_MAP.entrySet()) {
            result = renameIdentifier(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Adjusts GLSL version from 450 to 330 for broader compatibility.
     * Removes layout(column_major) directives.
     */
    String adjustVersion(String glsl) {
        String result = glsl;
        // Remove #version — jME manages the version directive itself
        result = result.replaceAll("#version \\d+\\s*\\n", "");
        result = result.replace("layout(column_major) uniform;\n", "");
        result = result.replace("layout(column_major) buffer;\n", "");
        // Remove layout(binding = N) from remaining declarations
        result = result.replaceAll("layout\\(binding = \\d+\\)\\s*\\n", "");
        // Remove layout(location = N) ONLY from vertex attribute inputs (jME names).
        // jME binds vertex attributes by name via glBindAttribLocation, so explicit
        // locations cause mismatches. Keep locations on varyings (out and fragment in)
        // since Slang generates different names between vertex out and fragment in.
        for (String attrName : ATTRIBUTE_MAP.values()) {
            result = result.replaceAll(
                "layout\\(location = \\d+\\)\\s*\\n(?=in \\S+ " + attrName + ";)", "");
        }
        // Remove #line directives — Slang emits #line N M format which can
        // cause issues with some GLSL compilers in 330 mode
        result = result.replaceAll("#line \\d+( \\d+)?\\s*\\n", "");
        // Slang emits mat4x4/mat3x3 which are valid but some drivers prefer mat4/mat3
        result = result.replace("mat4x4", "mat4");
        result = result.replace("mat3x3", "mat3");
        return result;
    }

    private String renameIdentifier(String source, String oldName, String newName) {
        Pattern pattern = Pattern.compile("(?<![\\w])" + Pattern.quote(oldName) + "(?![\\w])");
        return pattern.matcher(source).replaceAll(newName);
    }
}
