# SlangJme Material System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Slang-to-jME material loader that compiles Slang shaders to GLSL, auto-discovers parameters via reflection, and produces standard jME `MaterialDef`/`Material` objects with a generic mode registry for composable techniques.

**Architecture:** `SlangMaterialSystem` is the entry point — it holds a jSlang `GlobalSession`, a registry of named modes (each a Slang module + jME technique config), and a loader that compiles Slang → GLSL, reflects parameters, post-processes uniform/attribute names, and assembles jME `MaterialDef` objects. Runtime define variants are handled by re-compiling Slang per unique `DefineList` (lazy, cached) via a custom `SlangTechniqueDefLogic`.

**Tech Stack:** Java 21+ (preview features), jSlang (`dev.slang.api`), jMonkeyEngine 3.10.0-local (mavenLocal), Gradle Kotlin DSL, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-03-19-slang-jme-material-system-design.md`

---

## File Structure

```
SlangJme/
├── build.gradle.kts
├── src/main/java/dev/slang/jme/
│   ├── ReflectionMapper.java         # Maps Slang reflection → jME MatParam/VarType/defines
│   ├── GlslPostProcessor.java        # Uniform prefix renaming + attribute remapping
│   ├── SlangShaderGenerator.java     # Compiles Slang module → GLSL strings + reflection
│   ├── SlangTechniqueDefLogic.java   # Custom TechniqueDefLogic: re-compile per define set
│   ├── ModeConfig.java               # Generic technique mode configuration
│   ├── SlangTechniqueConfig.java     # Per-material technique configuration
│   └── SlangMaterialSystem.java      # Entry point: mode registry + material loading
└── src/test/java/dev/slang/jme/
    ├── ReflectionMapperTest.java
    ├── GlslPostProcessorTest.java
    ├── SlangShaderGeneratorTest.java
    ├── SlangTechniqueDefLogicTest.java
    ├── ModeConfigTest.java
    ├── SlangMaterialSystemTest.java
    └── IntegrationTest.java
```

---

### Task 1: Project Setup — Gradle Module + Dependencies

**Files:**
- Modify: `settings.gradle.kts`
- Create: `SlangJme/build.gradle.kts`

- [ ] **Step 1: Add SlangJme to settings.gradle.kts**

Add `include("SlangJme")` to `/media/mzuegg/Vault/Projects/SlangBindings/settings.gradle.kts`:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "SlangBindings"

include("bindings")
include("api")
include("SlangJme")
```

- [ ] **Step 2: Create SlangJme/build.gradle.kts**

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation(project(":api"))
    compileOnly("org.jmonkeyengine:jme3-core:3.10.0-local")

    testImplementation("org.jmonkeyengine:jme3-core:3.10.0-local")
    testImplementation("org.jmonkeyengine:jme3-desktop:3.10.0-local")
    testImplementation("org.jmonkeyengine:jme3-lwjgl3:3.10.0-local")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}
```

- [ ] **Step 3: Verify build compiles**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:compileJava`
Expected: BUILD SUCCESSFUL (no source files yet, just verifying dependency resolution)

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts SlangJme/build.gradle.kts
git commit -m "chore: add SlangJme module with jME + jSlang dependencies"
```

---

### Task 2: ReflectionMapper — Slang Types → jME VarType

**Files:**
- Create: `SlangJme/src/main/java/dev/slang/jme/ReflectionMapper.java`
- Create: `SlangJme/src/test/java/dev/slang/jme/ReflectionMapperTest.java`

This is the foundation — it maps Slang reflection data to jME material constructs.

- [ ] **Step 1: Write failing test for type mapping**

Create `SlangJme/src/test/java/dev/slang/jme/ReflectionMapperTest.java`:

```java
package dev.slang.jme;

import com.jme3.shader.VarType;
import dev.slang.api.*;
import dev.slang.api.reflect.*;
import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import dev.slang.bindings.raw.SlangReflection;
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.ReflectionMapperTest"`
Expected: Compilation failure — `ReflectionMapper` class does not exist yet.

- [ ] **Step 3: Implement ReflectionMapper**

Create `SlangJme/src/main/java/dev/slang/jme/ReflectionMapper.java`:

```java
package dev.slang.jme;

import com.jme3.shader.UniformBinding;
import com.jme3.shader.VarType;
import dev.slang.api.reflect.ParameterReflection;
import dev.slang.api.reflect.ProgramLayout;
import dev.slang.api.reflect.TypeReflection;
import dev.slang.bindings.raw.SlangReflection;

import java.util.*;

public class ReflectionMapper {

    public record MatParamMapping(String name, VarType varType) {}
    public record DefineMapping(String paramName, String defineName, VarType defineType) {}

    private static final Set<String> WORLD_PARAM_NAMES;
    static {
        var names = new HashSet<String>();
        for (UniformBinding b : UniformBinding.values()) {
            names.add(b.name());
        }
        WORLD_PARAM_NAMES = Collections.unmodifiableSet(names);
    }

    public List<MatParamMapping> extractParameters(ProgramLayout layout) {
        var result = new ArrayList<MatParamMapping>();
        for (var param : layout.parameters()) {
            String name = param.name();
            if (name == null || WORLD_PARAM_NAMES.contains(name)) continue;

            var type = param.type();
            int kind = type.kind();

            // Skip sampler states — they're implicit with textures in jME
            if (kind == SlangReflection.TYPE_KIND_SAMPLER_STATE) continue;

            VarType varType = mapType(type);
            if (varType != null) {
                result.add(new MatParamMapping(name, varType));
            }
        }
        return result;
    }

    public VarType mapType(TypeReflection type) {
        int kind = type.kind();
        String name = type.name();

        return switch (kind) {
            case SlangReflection.TYPE_KIND_SCALAR -> mapScalar(name);
            case SlangReflection.TYPE_KIND_VECTOR -> mapVector(name);
            case SlangReflection.TYPE_KIND_MATRIX -> mapMatrix(name);
            case SlangReflection.TYPE_KIND_RESOURCE -> mapResource(type);
            case SlangReflection.TYPE_KIND_CONSTANT_BUFFER -> VarType.UniformBufferObject;
            case SlangReflection.TYPE_KIND_SHADER_STORAGE_BUFFER -> VarType.ShaderStorageBufferObject;
            default -> null;
        };
    }

    public List<UniformBinding> extractWorldBindings(ProgramLayout layout) {
        var result = new ArrayList<UniformBinding>();
        for (var param : layout.parameters()) {
            String name = param.name();
            if (name != null && WORLD_PARAM_NAMES.contains(name)) {
                result.add(UniformBinding.valueOf(name));
            }
        }
        return result;
    }

    public Map<String, DefineMapping> extractDefines(ProgramLayout layout) {
        var result = new LinkedHashMap<String, DefineMapping>();
        for (var param : layout.parameters()) {
            String name = param.name();
            if (name == null || WORLD_PARAM_NAMES.contains(name)) continue;

            var type = param.type();
            int kind = type.kind();

            if (kind == SlangReflection.TYPE_KIND_SCALAR && "bool".equals(type.name())) {
                result.put(name, new DefineMapping(name, "HAS_" + name.toUpperCase(), VarType.Boolean));
            } else if (kind == SlangReflection.TYPE_KIND_RESOURCE) {
                VarType vt = mapResource(type);
                if (vt != null && vt.isTextureType()) {
                    result.put(name, new DefineMapping(name, "HAS_" + name.toUpperCase(), vt));
                }
            }
        }
        return result;
    }

    private VarType mapScalar(String name) {
        return switch (name) {
            case "float", "half" -> VarType.Float;
            case "int", "uint" -> VarType.Int;
            case "bool" -> VarType.Boolean;
            default -> null;
        };
    }

    private VarType mapVector(String name) {
        return switch (name) {
            case "float2", "half2" -> VarType.Vector2;
            case "float3", "half3" -> VarType.Vector3;
            case "float4", "half4" -> VarType.Vector4;
            default -> null;
        };
    }

    private VarType mapMatrix(String name) {
        return switch (name) {
            case "float3x3" -> VarType.Matrix3;
            case "float4x4" -> VarType.Matrix4;
            default -> null;
        };
    }

    private VarType mapResource(TypeReflection type) {
        String name = type.name();
        if (name == null) return null;
        // Order matters: more specific prefixes must come before less specific
        if (name.startsWith("Texture2DArray")) return VarType.TextureArray;
        if (name.startsWith("Texture2D")) return VarType.Texture2D;
        if (name.startsWith("Texture3D")) return VarType.Texture3D;
        if (name.startsWith("TextureCube")) return VarType.TextureCubeMap;
        if (name.startsWith("RWStructuredBuffer")) return VarType.ShaderStorageBufferObject;
        if (name.startsWith("RWTexture2D")) return VarType.Image2D;
        if (name.startsWith("RWTexture3D")) return VarType.Image3D;
        return null;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.ReflectionMapperTest"`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add SlangJme/src/main/java/dev/slang/jme/ReflectionMapper.java \
        SlangJme/src/test/java/dev/slang/jme/ReflectionMapperTest.java
git commit -m "feat(SlangJme): add ReflectionMapper — Slang types to jME VarType mapping"
```

---

### Task 3: GlslPostProcessor — Uniform Prefixes + Attribute Remapping

**Files:**
- Create: `SlangJme/src/main/java/dev/slang/jme/GlslPostProcessor.java`
- Create: `SlangJme/src/test/java/dev/slang/jme/GlslPostProcessorTest.java`

- [ ] **Step 1: Write failing test**

Create `SlangJme/src/test/java/dev/slang/jme/GlslPostProcessorTest.java`:

```java
package dev.slang.jme;

import com.jme3.shader.UniformBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GlslPostProcessorTest {

    @Test
    void addsMaterialUniformPrefix() {
        String glsl = "uniform float roughness;\nvoid main() { float r = roughness; }";
        var processor = new GlslPostProcessor();
        String result = processor.addUniformPrefixes(glsl,
            Set.of("roughness"), Set.of());
        assertTrue(result.contains("m_roughness"));
        assertFalse(result.contains(" roughness"));
    }

    @Test
    void addsWorldUniformPrefix() {
        String glsl = "uniform mat4 WorldViewProjectionMatrix;\nvoid main() { mat4 m = WorldViewProjectionMatrix; }";
        var processor = new GlslPostProcessor();
        String result = processor.addUniformPrefixes(glsl,
            Set.of(), Set.of("WorldViewProjectionMatrix"));
        assertTrue(result.contains("g_WorldViewProjectionMatrix"));
        assertFalse(result.contains(" WorldViewProjectionMatrix"));
    }

    @Test
    void doesNotPrefixAlreadyPrefixed() {
        String glsl = "uniform float m_roughness;";
        var processor = new GlslPostProcessor();
        String result = processor.addUniformPrefixes(glsl,
            Set.of("roughness"), Set.of());
        assertFalse(result.contains("m_m_roughness"));
    }

    @Test
    void remapsPositionAttribute() {
        // Slang typically emits attribute names based on semantics
        // The exact output format depends on Slang's GLSL backend
        String glsl = "in vec3 position_0;\nvoid main() { gl_Position = vec4(position_0, 1.0); }";
        var processor = new GlslPostProcessor();
        String result = processor.remapAttributes(glsl);
        assertTrue(result.contains("inPosition"));
    }

    @Test
    void remapsNormalAttribute() {
        String glsl = "in vec3 normal_0;\nvoid main() { vec3 n = normal_0; }";
        var processor = new GlslPostProcessor();
        String result = processor.remapAttributes(glsl);
        assertTrue(result.contains("inNormal"));
    }

    @Test
    void remapsTexCoordAttribute() {
        String glsl = "in vec2 uv_0;\nvoid main() { vec2 tc = uv_0; }";
        var processor = new GlslPostProcessor();
        String result = processor.remapAttributes(glsl);
        assertTrue(result.contains("inTexCoord"));
    }
}
```

**Important note:** The exact attribute names Slang emits in GLSL depend on its backend. The test may need adjustment after seeing actual Slang GLSL output. During implementation, first compile a test shader and inspect the GLSL output to learn what Slang emits for vertex attributes. Then adjust the remapping logic accordingly.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.GlslPostProcessorTest"`
Expected: Compilation failure — `GlslPostProcessor` class does not exist yet.

- [ ] **Step 3: Implement GlslPostProcessor**

Create `SlangJme/src/main/java/dev/slang/jme/GlslPostProcessor.java`:

```java
package dev.slang.jme;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class GlslPostProcessor {

    // Slang GLSL backend attribute name → jME attribute name
    // NOTE: These mappings may need adjustment based on actual Slang GLSL output.
    // Compile a test shader and inspect output to verify exact attribute names.
    private static final Map<String, String> ATTRIBUTE_MAP = Map.of(
        "position_0", "inPosition",
        "normal_0", "inNormal",
        "tangent_0", "inTangent",
        "uv_0", "inTexCoord",
        "uv_1", "inTexCoord2",
        "color_0", "inColor"
    );

    /**
     * Renames uniforms to add jME prefixes:
     * - Material parameters: name → m_name
     * - World parameters: name → g_name
     */
    public String addUniformPrefixes(String glsl, Set<String> materialParamNames,
                                      Set<String> worldParamNames) {
        String result = glsl;
        for (String name : worldParamNames) {
            result = renameIdentifier(result, name, "g_" + name);
        }
        for (String name : materialParamNames) {
            result = renameIdentifier(result, name, "m_" + name);
        }
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
     * Applies all post-processing to a GLSL source string.
     */
    public String process(String glsl, Set<String> materialParamNames,
                          Set<String> worldParamNames) {
        String result = addUniformPrefixes(glsl, materialParamNames, worldParamNames);
        result = remapAttributes(result);
        return result;
    }

    private String renameIdentifier(String source, String oldName, String newName) {
        // Word-boundary replacement to avoid partial matches
        // Negative lookbehind for alphanumeric/underscore, negative lookahead for same
        Pattern pattern = Pattern.compile("(?<![\\w])" + Pattern.quote(oldName) + "(?![\\w])");
        return pattern.matcher(source).replaceAll(newName);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.GlslPostProcessorTest"`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add SlangJme/src/main/java/dev/slang/jme/GlslPostProcessor.java \
        SlangJme/src/test/java/dev/slang/jme/GlslPostProcessorTest.java
git commit -m "feat(SlangJme): add GlslPostProcessor — uniform prefix + attribute remapping"
```

---

### Task 4: SlangShaderGenerator — Compile Slang → GLSL

**Files:**
- Create: `SlangJme/src/main/java/dev/slang/jme/SlangShaderGenerator.java`
- Create: `SlangJme/src/test/java/dev/slang/jme/SlangShaderGeneratorTest.java`

- [ ] **Step 1: Write failing test**

Create `SlangJme/src/test/java/dev/slang/jme/SlangShaderGeneratorTest.java`:

```java
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
            #ifdef HAS_ALBEDOTEX
            Texture2D albedoTex;
            SamplerState linearSampler;
            #endif

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
                #ifdef HAS_ALBEDOTEX
                return float4(1,1,1,1);
                #else
                return float4(roughness, roughness, roughness, 1.0);
                #endif
            }
            """;

        var generator = new SlangShaderGenerator(global);
        var withDefine = generator.compile("test", shaderWithDefine,
            "vertexMain", "fragmentMain", Map.of("HAS_ALBEDOTEX", "1"));
        var withoutDefine = generator.compile("test2", shaderWithDefine,
            "vertexMain", "fragmentMain", Map.of());

        // The two variants should produce different fragment shaders
        assertNotEquals(withDefine.fragmentGlsl(), withoutDefine.fragmentGlsl());
    }

    @Test
    void returnsReflectionData() throws Exception {
        var generator = new SlangShaderGenerator(global);
        var result = generator.compileWithReflection("test", SHADER,
            "vertexMain", "fragmentMain", Map.of());
        assertNotNull(result.layout());
        assertTrue(result.layout().parameterCount() > 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.SlangShaderGeneratorTest"`
Expected: Compilation failure — `SlangShaderGenerator` class does not exist yet.

- [ ] **Step 3: Implement SlangShaderGenerator**

Create `SlangJme/src/main/java/dev/slang/jme/SlangShaderGenerator.java`:

```java
package dev.slang.jme;

import dev.slang.api.*;
import dev.slang.api.reflect.ProgramLayout;
import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SlangShaderGenerator {

    public record ShaderSources(String vertexGlsl, String fragmentGlsl) {}
    public record CompilationResult(ShaderSources sources, ProgramLayout layout) {}

    private final GlobalSession globalSession;
    private final int glslProfile;

    public SlangShaderGenerator(GlobalSession globalSession) {
        this.globalSession = globalSession;
        this.glslProfile = globalSession.findProfile("glsl_330");
    }

    public ShaderSources compile(String moduleName, String sourceCode,
                                  String vertexEntry, String fragmentEntry,
                                  Map<String, String> defines) throws SlangException {
        return compileWithReflection(moduleName, sourceCode, vertexEntry, fragmentEntry,
            defines, List.of()).sources();
    }

    public CompilationResult compileWithReflection(String moduleName, String sourceCode,
                                                     String vertexEntry, String fragmentEntry,
                                                     Map<String, String> defines,
                                                     List<String> searchPaths) throws SlangException {
        var builder = new SessionDescBuilder()
            .addTarget(new TargetDescBuilder()
                .format(CompileTarget.GLSL)
                .profile(glslProfile));

        for (var entry : defines.entrySet()) {
            builder.addMacro(entry.getKey(), entry.getValue());
        }
        for (String path : searchPaths) {
            builder.addSearchPath(path);
        }

        try (var session = globalSession.createSession(builder)) {
            var module = session.loadModuleFromSourceString(
                moduleName, moduleName + ".slang", sourceCode);

            var vsEp = module.findAndCheckEntryPoint(vertexEntry, Stage.VERTEX);
            var fsEp = module.findAndCheckEntryPoint(fragmentEntry, Stage.FRAGMENT);
            var composite = session.createCompositeComponentType(module, vsEp, fsEp);
            var linked = composite.link();

            String vertexGlsl;
            try (var blob = linked.getEntryPointCode(0, 0)) {
                vertexGlsl = new String(blob.toByteArray(), StandardCharsets.UTF_8);
            }

            String fragmentGlsl;
            try (var blob = linked.getEntryPointCode(1, 0)) {
                fragmentGlsl = new String(blob.toByteArray(), StandardCharsets.UTF_8);
            }

            ProgramLayout layout = linked.getLayout(0);
            return new CompilationResult(
                new ShaderSources(vertexGlsl, fragmentGlsl), layout);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.SlangShaderGeneratorTest"`
Expected: All tests PASS.

**Important:** If tests fail, inspect the actual Slang GLSL output (print it) to understand Slang's naming conventions. Adjust attribute mapping in `GlslPostProcessor` based on actual output. Also verify entry point indices — Slang may order them differently.

- [ ] **Step 5: Commit**

```bash
git add SlangJme/src/main/java/dev/slang/jme/SlangShaderGenerator.java \
        SlangJme/src/test/java/dev/slang/jme/SlangShaderGeneratorTest.java
git commit -m "feat(SlangJme): add SlangShaderGenerator — Slang to GLSL compilation"
```

---

### Task 5: ModeConfig — Generic Technique Mode Configuration

**Files:**
- Create: `SlangJme/src/main/java/dev/slang/jme/ModeConfig.java`
- Create: `SlangJme/src/test/java/dev/slang/jme/ModeConfigTest.java`

- [ ] **Step 1: Write failing test**

Create `SlangJme/src/test/java/dev/slang/jme/ModeConfigTest.java`:

```java
package dev.slang.jme;

import com.jme3.material.TechniqueDef;
import com.jme3.shader.VarType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModeConfigTest {

    @Test
    void buildsWithDefaults() {
        var config = ModeConfig.builder().build();
        assertEquals("vertexMain", config.vertexEntryPoint());
        assertEquals("fragmentMain", config.fragmentEntryPoint());
        assertNull(config.lightMode());
        assertNull(config.shadowMode());
        assertTrue(config.requiredWorldParams().isEmpty());
        assertTrue(config.implicitDefines().isEmpty());
    }

    @Test
    void buildsWithLightMode() {
        var config = ModeConfig.builder()
            .lightMode(TechniqueDef.LightMode.SinglePass)
            .worldParam("LightPosition")
            .worldParam("LightColor")
            .implicitDefine("NB_LIGHTS", VarType.Int)
            .build();
        assertEquals(TechniqueDef.LightMode.SinglePass, config.lightMode());
        assertEquals(2, config.requiredWorldParams().size());
        assertTrue(config.implicitDefines().containsKey("NB_LIGHTS"));
    }

    @Test
    void buildsWithShadowMode() {
        var config = ModeConfig.builder()
            .shadowMode(TechniqueDef.ShadowMode.PostPass)
            .vertexEntry("shadowVs")
            .fragmentEntry("shadowFs")
            .build();
        assertEquals(TechniqueDef.ShadowMode.PostPass, config.shadowMode());
        assertEquals("shadowVs", config.vertexEntryPoint());
        assertEquals("shadowFs", config.fragmentEntryPoint());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.ModeConfigTest"`
Expected: Compilation failure.

- [ ] **Step 3: Implement ModeConfig**

Create `SlangJme/src/main/java/dev/slang/jme/ModeConfig.java`:

```java
package dev.slang.jme;

import com.jme3.material.RenderState;
import com.jme3.material.TechniqueDef;
import com.jme3.material.logic.TechniqueDefLogic;
import com.jme3.shader.VarType;

import java.util.*;

public class ModeConfig {
    private final String vertexEntryPoint;
    private final String fragmentEntryPoint;
    private final TechniqueDef.LightMode lightMode;
    private final TechniqueDef.ShadowMode shadowMode;
    private final TechniqueDefLogic logic;
    private final RenderState renderState;
    private final List<String> requiredWorldParams;
    private final Map<String, VarType> implicitDefines;

    private ModeConfig(Builder builder) {
        this.vertexEntryPoint = builder.vertexEntryPoint;
        this.fragmentEntryPoint = builder.fragmentEntryPoint;
        this.lightMode = builder.lightMode;
        this.shadowMode = builder.shadowMode;
        this.logic = builder.logic;
        this.renderState = builder.renderState;
        this.requiredWorldParams = List.copyOf(builder.requiredWorldParams);
        this.implicitDefines = Map.copyOf(builder.implicitDefines);
    }

    public String vertexEntryPoint() { return vertexEntryPoint; }
    public String fragmentEntryPoint() { return fragmentEntryPoint; }
    public TechniqueDef.LightMode lightMode() { return lightMode; }
    public TechniqueDef.ShadowMode shadowMode() { return shadowMode; }
    public TechniqueDefLogic logic() { return logic; }
    public RenderState renderState() { return renderState; }
    public List<String> requiredWorldParams() { return requiredWorldParams; }
    public Map<String, VarType> implicitDefines() { return implicitDefines; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String vertexEntryPoint = "vertexMain";
        private String fragmentEntryPoint = "fragmentMain";
        private TechniqueDef.LightMode lightMode;
        private TechniqueDef.ShadowMode shadowMode;
        private TechniqueDefLogic logic;
        private RenderState renderState;
        private final List<String> requiredWorldParams = new ArrayList<>();
        private final Map<String, VarType> implicitDefines = new LinkedHashMap<>();

        public Builder vertexEntry(String entry) { this.vertexEntryPoint = entry; return this; }
        public Builder fragmentEntry(String entry) { this.fragmentEntryPoint = entry; return this; }
        public Builder lightMode(TechniqueDef.LightMode mode) { this.lightMode = mode; return this; }
        public Builder shadowMode(TechniqueDef.ShadowMode mode) { this.shadowMode = mode; return this; }
        public Builder logic(TechniqueDefLogic logic) { this.logic = logic; return this; }
        public Builder renderState(RenderState state) { this.renderState = state; return this; }
        public Builder worldParam(String name) { this.requiredWorldParams.add(name); return this; }
        public Builder implicitDefine(String name, VarType type) { this.implicitDefines.put(name, type); return this; }

        public ModeConfig build() { return new ModeConfig(this); }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.ModeConfigTest"`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add SlangJme/src/main/java/dev/slang/jme/ModeConfig.java \
        SlangJme/src/test/java/dev/slang/jme/ModeConfigTest.java
git commit -m "feat(SlangJme): add ModeConfig — generic technique mode configuration"
```

---

### Task 6: SlangTechniqueDefLogic — Custom TechniqueDefLogic with Define-Based Recompilation

**Files:**
- Create: `SlangJme/src/main/java/dev/slang/jme/SlangTechniqueDefLogic.java`
- Create: `SlangJme/src/test/java/dev/slang/jme/SlangTechniqueDefLogicTest.java`

This is the core integration piece. It implements `TechniqueDefLogic`, re-compiling Slang per unique `DefineList` and caching the result.

- [ ] **Step 1: Write failing test**

Create `SlangJme/src/test/java/dev/slang/jme/SlangTechniqueDefLogicTest.java`:

```java
package dev.slang.jme;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.TechniqueDef;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
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
        #ifdef HAS_ALBEDOTEX
        Texture2D albedoTex;
        SamplerState linearSampler;
        #endif

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
            #ifdef HAS_ALBEDOTEX
            return albedoTex.Sample(linearSampler, input.uv);
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
        int defineId = techniqueDef.addShaderUnmappedDefine("HAS_ALBEDOTEX", VarType.Boolean);

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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.SlangTechniqueDefLogicTest"`
Expected: Compilation failure.

- [ ] **Step 3: Implement SlangTechniqueDefLogic**

Create `SlangJme/src/main/java/dev/slang/jme/SlangTechniqueDefLogic.java`:

```java
package dev.slang.jme;

import com.jme3.asset.AssetManager;
import com.jme3.light.LightList;
import com.jme3.material.Material;
import com.jme3.material.TechniqueDef;
import com.jme3.material.logic.DefaultTechniqueDefLogic;
import com.jme3.material.logic.TechniqueDefLogic;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Geometry;
import com.jme3.shader.DefineList;
import com.jme3.shader.Shader;
import com.jme3.shader.UniformBinding;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlangTechniqueDefLogic implements TechniqueDefLogic {

    private static final Logger log = Logger.getLogger(SlangTechniqueDefLogic.class.getName());

    private final TechniqueDef techniqueDef;
    private final TechniqueDefLogic delegate;
    private final SlangShaderGenerator generator;
    private final GlslPostProcessor postProcessor;
    private final String moduleName;
    private final String sourceCode;
    private final String vertexEntry;
    private final String fragmentEntry;
    private final Map<DefineList, Shader> shaderCache = new ConcurrentHashMap<>();

    // Cached reflection data from first compilation
    private Set<String> materialParamNames;
    private Set<String> worldParamNames;

    public SlangTechniqueDefLogic(TechniqueDef techniqueDef,
                                   TechniqueDefLogic delegate,
                                   SlangShaderGenerator generator,
                                   GlslPostProcessor postProcessor,
                                   String moduleName, String sourceCode,
                                   String vertexEntry, String fragmentEntry) {
        this.techniqueDef = techniqueDef;
        this.delegate = delegate;
        this.generator = generator;
        this.postProcessor = postProcessor;
        this.moduleName = moduleName;
        this.sourceCode = sourceCode;
        this.vertexEntry = vertexEntry;
        this.fragmentEntry = fragmentEntry;
    }

    public void setParamNames(Set<String> materialParamNames, Set<String> worldParamNames) {
        this.materialParamNames = materialParamNames;
        this.worldParamNames = worldParamNames;
    }

    @Override
    public Shader makeCurrent(AssetManager assetManager, RenderManager renderManager,
                               EnumSet<Caps> rendererCaps, LightList lights,
                               DefineList defines) {
        // Let delegate update dynamic defines (e.g., light count) if present
        if (delegate != null) {
            delegate.makeCurrent(assetManager, renderManager, rendererCaps, lights, defines);
        }

        // Check cache
        DefineList key = defines.deepClone();
        Shader cached = shaderCache.get(key);
        if (cached != null) return cached;

        // Convert DefineList → Slang preprocessor macros
        Map<String, String> macros = defineListToMacros(defines);

        try {
            // Re-compile Slang with these defines
            var result = generator.compile(
                moduleName + "_" + shaderCache.size(),
                sourceCode, vertexEntry, fragmentEntry, macros);

            // Post-process GLSL
            String vertexGlsl = result.vertexGlsl();
            String fragmentGlsl = result.fragmentGlsl();
            if (materialParamNames != null) {
                vertexGlsl = postProcessor.process(vertexGlsl, materialParamNames, worldParamNames);
                fragmentGlsl = postProcessor.process(fragmentGlsl, materialParamNames, worldParamNames);
            }

            // Build jME Shader
            Shader shader = new Shader();
            shader.addSource(Shader.ShaderType.Vertex, moduleName + ".vert",
                vertexGlsl, null, "GLSL330");
            shader.addSource(Shader.ShaderType.Fragment, moduleName + ".frag",
                fragmentGlsl, null, "GLSL330");

            // Add world parameter bindings
            for (UniformBinding binding : techniqueDef.getWorldBindings()) {
                shader.addUniformBinding(binding);
            }

            shaderCache.put(key, shader);
            return shader;

        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to compile Slang shader: " + moduleName, e);
            throw new RuntimeException("Slang compilation failed for " + moduleName, e);
        }
    }

    @Override
    public void render(RenderManager renderManager, Shader shader,
                        Geometry geometry, LightList lights,
                        Material.BindUnits lastBindUnits) {
        if (delegate != null) {
            delegate.render(renderManager, shader, geometry, lights, lastBindUnits);
        } else {
            DefaultTechniqueDefLogic.renderMeshFromGeometry(renderManager.getRenderer(), geometry);
        }
    }

    private Map<String, String> defineListToMacros(DefineList defines) {
        var macros = new LinkedHashMap<String, String>();
        String[] names = techniqueDef.getDefineNames();
        if (names == null) return macros;

        var types = techniqueDef.getDefineTypes();
        for (int i = 0; i < names.length; i++) {
            if (!defines.isSet(i)) continue;
            String name = names[i];
            var type = types[i];
            String value = switch (type) {
                case Boolean -> "1";
                case Int -> String.valueOf(defines.getInt(i));
                case Float -> String.valueOf(defines.getFloat(i));
                default -> "1";
            };
            macros.put(name, value);
        }
        return macros;
    }
}
```

**Note:** The `render()` method uses `Material.BindUnits` as verified from jME 3.10.0-local sources. `DefaultTechniqueDefLogic.renderMeshFromGeometry()` takes `Renderer`, not `RenderManager`, so we call `renderManager.getRenderer()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.SlangTechniqueDefLogicTest"`
Expected: All tests PASS.

**Troubleshooting:** If tests fail due to `DesktopAssetManager` constructor differences, adjust the test to use the correct jME 3.10.0-local API. If `render()` signature doesn't match, check the `TechniqueDefLogic` interface in your jME fork and adjust.

- [ ] **Step 5: Commit**

```bash
git add SlangJme/src/main/java/dev/slang/jme/SlangTechniqueDefLogic.java \
        SlangJme/src/test/java/dev/slang/jme/SlangTechniqueDefLogicTest.java
git commit -m "feat(SlangJme): add SlangTechniqueDefLogic — define-based Slang recompilation"
```

---

### Task 7: SlangMaterialSystem — Entry Point, Mode Registry, Material Loading

**Files:**
- Create: `SlangJme/src/main/java/dev/slang/jme/SlangMaterialSystem.java`
- Create: `SlangJme/src/test/java/dev/slang/jme/SlangMaterialSystemTest.java`

This is the top-level orchestrator that ties everything together.

- [ ] **Step 1: Write failing test**

Create `SlangJme/src/test/java/dev/slang/jme/SlangMaterialSystemTest.java`:

```java
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlangMaterialSystemTest {

    private static final String PBR_SHADER = """
        uniform float4x4 WorldViewProjectionMatrix;
        uniform float4x4 NormalMatrix;
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
            assertNull(matDef.getMaterialParam("NormalMatrix"));
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

            // Should have both Default and Shadow techniques
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
            // Can set parameters discovered from reflection
            mat.setFloat("roughness", 0.5f);
            mat.setFloat("metallic", 0.0f);
            assertEquals(0.5f, (float) mat.getParamValue("roughness"));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.SlangMaterialSystemTest"`
Expected: Compilation failure.

- [ ] **Step 3: Create SlangTechniqueConfig**

First create the simple config class. Create `SlangJme/src/main/java/dev/slang/jme/SlangTechniqueConfig.java`:

```java
package dev.slang.jme;

import com.jme3.material.RenderState;

import java.util.*;

public class SlangTechniqueConfig {
    private final String vertexEntryPoint;
    private final String fragmentEntryPoint;
    private final List<String> modes;
    private final RenderState renderState;
    private final List<String> worldParams;
    private final Map<String, String> staticDefines;

    private SlangTechniqueConfig(Builder builder) {
        this.vertexEntryPoint = builder.vertexEntryPoint;
        this.fragmentEntryPoint = builder.fragmentEntryPoint;
        this.modes = List.copyOf(builder.modes);
        this.renderState = builder.renderState;
        this.worldParams = List.copyOf(builder.worldParams);
        this.staticDefines = Map.copyOf(builder.staticDefines);
    }

    public String vertexEntryPoint() { return vertexEntryPoint; }
    public String fragmentEntryPoint() { return fragmentEntryPoint; }
    public List<String> modes() { return modes; }
    public RenderState renderState() { return renderState; }
    public List<String> worldParams() { return worldParams; }
    public Map<String, String> staticDefines() { return staticDefines; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String vertexEntryPoint = "vertexMain";
        private String fragmentEntryPoint = "fragmentMain";
        private final List<String> modes = new ArrayList<>();
        private RenderState renderState;
        private final List<String> worldParams = new ArrayList<>();
        private final Map<String, String> staticDefines = new LinkedHashMap<>();

        public Builder vertexEntry(String entry) { this.vertexEntryPoint = entry; return this; }
        public Builder fragmentEntry(String entry) { this.fragmentEntryPoint = entry; return this; }
        public Builder mode(String modeName) { this.modes.add(modeName); return this; }
        public Builder renderState(RenderState state) { this.renderState = state; return this; }
        public Builder worldParam(String name) { this.worldParams.add(name); return this; }
        public Builder staticDefine(String name, String value) { this.staticDefines.put(name, value); return this; }

        public SlangTechniqueConfig build() { return new SlangTechniqueConfig(this); }
    }
}
```

- [ ] **Step 4: Implement SlangMaterialSystem**

Create `SlangJme/src/main/java/dev/slang/jme/SlangMaterialSystem.java`:

```java
package dev.slang.jme;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.material.TechniqueDef;
import com.jme3.shader.UniformBinding;
import com.jme3.shader.VarType;
import dev.slang.api.GlobalSession;
import dev.slang.api.SlangException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class SlangMaterialSystem implements AutoCloseable {

    private static final Logger log = Logger.getLogger(SlangMaterialSystem.class.getName());
    private static final AtomicInteger TECHNIQUE_SORT_ID = new AtomicInteger(0);

    private final AssetManager assetManager;
    private final GlobalSession globalSession;
    private final SlangShaderGenerator generator;
    private final GlslPostProcessor postProcessor;
    private final ReflectionMapper reflectionMapper;
    private final List<String> searchPaths = new ArrayList<>();

    // Generic mode registry: name → (sourceCode, config)
    private final Map<String, RegisteredMode> modes = new LinkedHashMap<>();

    private record RegisteredMode(String sourceCode, ModeConfig config) {}

    public SlangMaterialSystem(AssetManager assetManager) {
        this.assetManager = assetManager;
        this.globalSession = GlobalSession.create();
        this.generator = new SlangShaderGenerator(globalSession);
        this.postProcessor = new GlslPostProcessor();
        this.reflectionMapper = new ReflectionMapper();
    }

    public void addSearchPath(String path) {
        searchPaths.add(path);
    }

    public void registerModeFromSource(String name, String sourceCode, ModeConfig config) {
        modes.put(name, new RegisteredMode(sourceCode, config));
    }

    public MaterialDef loadMaterialDefFromSource(String name, String sourceCode,
                                                   SlangTechniqueConfig config) throws SlangException {
        // 1. Compile with static defines to get reflection data
        var result = generator.compileWithReflection(
            name, sourceCode,
            config.vertexEntryPoint(), config.fragmentEntryPoint(),
            config.staticDefines());

        // 2. Extract parameters and world bindings from reflection
        var materialParams = reflectionMapper.extractParameters(result.layout());
        var worldBindings = reflectionMapper.extractWorldBindings(result.layout());
        var defines = reflectionMapper.extractDefines(result.layout());

        // 3. Build MaterialDef
        MaterialDef matDef = new MaterialDef(assetManager, name);

        // Add material parameters
        for (var param : materialParams) {
            if (param.varType().isTextureType()) {
                matDef.addMaterialParamTexture(param.varType(), param.name(), null, null);
            } else {
                matDef.addMaterialParam(param.varType(), param.name(), null);
            }
        }

        // 4. Build main technique ("Default")
        var mainTechnique = buildTechniqueDef("Default", name, sourceCode,
            config.vertexEntryPoint(), config.fragmentEntryPoint(),
            materialParams, worldBindings, defines, config, null);
        matDef.addTechniqueDef(mainTechnique);

        // 5. Build techniques for each registered mode
        for (String modeName : config.modes()) {
            var registeredMode = modes.get(modeName);
            if (registeredMode == null) {
                throw new IllegalArgumentException("Unknown mode: " + modeName);
            }
            var modeTechnique = buildModeTechniqueDef(modeName, registeredMode);
            matDef.addTechniqueDef(modeTechnique);
        }

        return matDef;
    }

    public Material loadMaterialFromSource(String name, String sourceCode,
                                            SlangTechniqueConfig config) throws SlangException {
        MaterialDef matDef = loadMaterialDefFromSource(name, sourceCode, config);
        return new Material(matDef);
    }

    @Override
    public void close() {
        globalSession.close();
    }

    private TechniqueDef buildTechniqueDef(String techniqueName, String moduleName,
                                            String sourceCode, String vertexEntry,
                                            String fragmentEntry,
                                            List<ReflectionMapper.MatParamMapping> materialParams,
                                            List<UniformBinding> worldBindings,
                                            Map<String, ReflectionMapper.DefineMapping> defines,
                                            SlangTechniqueConfig config,
                                            ModeConfig modeConfig) {
        var techniqueDef = new TechniqueDef(techniqueName, TECHNIQUE_SORT_ID.getAndIncrement());

        // Add world parameters
        for (UniformBinding binding : worldBindings) {
            techniqueDef.addWorldParam(binding.name());
        }
        for (String wp : config.worldParams()) {
            techniqueDef.addWorldParam(wp);
        }

        // Add define mappings
        for (var entry : defines.entrySet()) {
            var dm = entry.getValue();
            techniqueDef.addShaderParamDefine(dm.paramName(), dm.defineType(), dm.defineName());
        }

        // Apply mode config if present
        if (modeConfig != null) {
            if (modeConfig.lightMode() != null) {
                techniqueDef.setLightMode(modeConfig.lightMode());
            }
            if (modeConfig.shadowMode() != null) {
                techniqueDef.setShadowMode(modeConfig.shadowMode());
            }
            if (modeConfig.renderState() != null) {
                techniqueDef.setRenderState(modeConfig.renderState());
            }
            for (String wp : modeConfig.requiredWorldParams()) {
                techniqueDef.addWorldParam(wp);
            }
            for (var entry : modeConfig.implicitDefines().entrySet()) {
                techniqueDef.addShaderUnmappedDefine(entry.getKey(), entry.getValue());
            }
        }

        // Collect ALL material param names for GLSL post-processing (uniform prefix renaming)
        Set<String> materialParamNames = new HashSet<>();
        if (materialParams != null) {
            for (var mp : materialParams) {
                materialParamNames.add(mp.name());
            }
        }
        for (var entry : defines.entrySet()) {
            materialParamNames.add(entry.getKey());
        }

        Set<String> worldParamNames = new HashSet<>();
        for (UniformBinding b : worldBindings) {
            worldParamNames.add(b.name());
        }

        // Set shader file to dummy path (required by TechniqueDef)
        techniqueDef.setShaderFile(moduleName + ".vert", moduleName + ".frag", "GLSL330", "GLSL330");

        // Create custom logic that handles Slang compilation
        var logic = new SlangTechniqueDefLogic(
            techniqueDef,
            modeConfig != null ? modeConfig.logic() : null,
            generator, postProcessor,
            moduleName, sourceCode, vertexEntry, fragmentEntry);
        logic.setParamNames(materialParamNames, worldParamNames);
        techniqueDef.setLogic(logic);

        return techniqueDef;
    }

    private TechniqueDef buildModeTechniqueDef(String modeName,
                                                RegisteredMode registered) throws SlangException {
        var modeConfig = registered.config();

        // Compile mode shader to get reflection
        var result = generator.compileWithReflection(
            modeName, registered.sourceCode(),
            modeConfig.vertexEntryPoint(), modeConfig.fragmentEntryPoint(),
            Map.of());

        var modeMatParams = reflectionMapper.extractParameters(result.layout());
        var worldBindings = reflectionMapper.extractWorldBindings(result.layout());
        var defines = reflectionMapper.extractDefines(result.layout());

        return buildTechniqueDef(modeName, modeName, registered.sourceCode(),
            modeConfig.vertexEntryPoint(), modeConfig.fragmentEntryPoint(),
            modeMatParams, worldBindings, defines,
            SlangTechniqueConfig.builder()
                .vertexEntry(modeConfig.vertexEntryPoint())
                .fragmentEntry(modeConfig.fragmentEntryPoint())
                .build(),
            modeConfig);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.SlangMaterialSystemTest"`
Expected: All tests PASS.

**Troubleshooting:**
- If `MaterialDef` constructor or API differs in jME 3.10.0-local, check the actual class and adjust.
- If `addMaterialParamTexture` signature differs, check jME source. Some versions use `addMaterialParamTexture(VarType, String, ColorSpace, Texture)`.
- If `TechniqueDef.setShaderFile` is required even with custom logic, keep the dummy path. If not, remove it.

- [ ] **Step 6: Commit**

```bash
git add SlangJme/src/main/java/dev/slang/jme/SlangMaterialSystem.java \
        SlangJme/src/main/java/dev/slang/jme/SlangTechniqueConfig.java \
        SlangJme/src/test/java/dev/slang/jme/SlangMaterialSystemTest.java
git commit -m "feat(SlangJme): add SlangMaterialSystem — mode registry and material loading"
```

---

### Task 8: Integration Test — End-to-End with Actual Slang GLSL Output

**Files:**
- Create: `SlangJme/src/test/java/dev/slang/jme/IntegrationTest.java`

This task verifies the full pipeline works end-to-end and validates assumptions about Slang's GLSL output format (attribute names, uniform names, etc.).

- [ ] **Step 1: Write integration test that inspects actual Slang GLSL output**

Create `SlangJme/src/test/java/dev/slang/jme/IntegrationTest.java`:

```java
package dev.slang.jme;

import dev.slang.api.GlobalSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
    void inspectSlangGlslOutput() throws Exception {
        var generator = new SlangShaderGenerator(global);
        var result = generator.compile("inspect", SHADER,
            "vertexMain", "fragmentMain", Map.of());

        System.out.println("=== VERTEX GLSL ===");
        System.out.println(result.vertexGlsl());
        System.out.println("=== FRAGMENT GLSL ===");
        System.out.println(result.fragmentGlsl());

        // Basic sanity — both shaders should have a main()
        assertTrue(result.vertexGlsl().contains("void main("));
        assertTrue(result.fragmentGlsl().contains("void main("));

        // Vertex shader should set gl_Position
        assertTrue(result.vertexGlsl().contains("gl_Position"));
    }

    @Test
    void postProcessedGlslHasJmeConventions() throws Exception {
        var generator = new SlangShaderGenerator(global);
        var reflectionMapper = new ReflectionMapper();
        var postProcessor = new GlslPostProcessor();

        var compiled = generator.compileWithReflection("inspect", SHADER,
            "vertexMain", "fragmentMain", Map.of());

        var materialParams = reflectionMapper.extractParameters(compiled.layout());
        var worldBindings = reflectionMapper.extractWorldBindings(compiled.layout());

        var matNames = new java.util.HashSet<String>();
        for (var p : materialParams) matNames.add(p.name());
        var worldNames = new java.util.HashSet<String>();
        for (var b : worldBindings) worldNames.add(b.name());

        String vertexGlsl = postProcessor.process(
            compiled.sources().vertexGlsl(), matNames, worldNames);
        String fragmentGlsl = postProcessor.process(
            compiled.sources().fragmentGlsl(), matNames, worldNames);

        System.out.println("=== POST-PROCESSED VERTEX GLSL ===");
        System.out.println(vertexGlsl);
        System.out.println("=== POST-PROCESSED FRAGMENT GLSL ===");
        System.out.println(fragmentGlsl);

        // World params should have g_ prefix
        assertTrue(vertexGlsl.contains("g_WorldViewProjectionMatrix"));
        // Material params should have m_ prefix
        assertTrue(fragmentGlsl.contains("m_roughness"), "roughness should be prefixed with m_");
        assertTrue(fragmentGlsl.contains("m_baseColor"), "baseColor should be prefixed with m_");
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test --tests "dev.slang.jme.IntegrationTest" --info`
Expected: Tests PASS. Inspect printed GLSL output to verify assumptions.

**Critical checkpoint:** If the GLSL output shows unexpected attribute names, uniform names, or structure, go back and adjust `GlslPostProcessor.ATTRIBUTE_MAP` and the renaming logic. This is where assumptions get validated.

- [ ] **Step 3: Adjust GlslPostProcessor if needed**

Based on the actual Slang GLSL output, update `ATTRIBUTE_MAP` and `addUniformPrefixes` to match the actual names Slang generates. Update corresponding tests in `GlslPostProcessorTest`.

- [ ] **Step 4: Re-run all tests**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add SlangJme/src/test/java/dev/slang/jme/IntegrationTest.java
git commit -m "test(SlangJme): add integration test validating Slang GLSL output + post-processing"
```

---

### Task 9: Fix Attribute Mapping Based on Actual Output

**Files:**
- Modify: `SlangJme/src/main/java/dev/slang/jme/GlslPostProcessor.java`
- Modify: `SlangJme/src/test/java/dev/slang/jme/GlslPostProcessorTest.java`

This task exists because the attribute names Slang generates in GLSL are not known until Task 8 runs. After inspecting the actual output:

- [ ] **Step 1: Read the GLSL output from Task 8**

Look at the printed vertex shader GLSL. Find what Slang named the vertex inputs. Common patterns:
- `in vec3 position_0;` or `layout(location = 0) in vec3 _S1;` or `in vec3 POSITION;`

- [ ] **Step 2: Update ATTRIBUTE_MAP in GlslPostProcessor**

Replace the placeholder attribute map with the actual mapping based on observed output.

- [ ] **Step 3: Update GlslPostProcessorTest to match actual names**

Fix the test input strings to match what Slang actually generates.

- [ ] **Step 4: Run all tests**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:test`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add SlangJme/src/main/java/dev/slang/jme/GlslPostProcessor.java \
        SlangJme/src/test/java/dev/slang/jme/GlslPostProcessorTest.java
git commit -m "fix(SlangJme): align attribute mapping with actual Slang GLSL output"
```

---

### Task 10: Final Verification — All Tests Green

**Files:** None (verification only)

- [ ] **Step 1: Run full test suite**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew test`
Expected: All tests pass across all modules (bindings, api, SlangJme).

- [ ] **Step 2: Verify no compilation warnings**

Run: `cd /media/mzuegg/Vault/Projects/SlangBindings && ./gradlew :SlangJme:compileJava 2>&1 | grep -i warn`
Expected: No warnings (or only expected preview-feature warnings).
