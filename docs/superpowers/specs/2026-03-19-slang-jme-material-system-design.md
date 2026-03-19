# SlangJme Material System Design

## Overview

A loader/bridge layer that compiles Slang shader modules to GLSL and produces standard jMonkeyEngine `MaterialDef`/`Material`/`Shader` objects. Key features:

- **Auto-generated parameters**: Slang reflection discovers uniforms, textures, samplers → `MatParam` declarations
- **Auto-generated defines**: Reflection discovers conditional features → define mappings
- **Runtime define variants**: jME's existing `DefineList` + shader cache handles variant switching
- **Composable techniques**: Registry of reusable light modes and shadow modes applied to all materials
- **Slang-level composition**: Modules, imports, interfaces for shader code reuse

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   User Code                          │
│  system.registerLightMode("SinglePass", config)      │
│  system.registerShadowMode("PCF", config)            │
│  Material mat = system.loadMaterial("PBR.slang")     │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              SlangMaterialSystem                     │
│  - Registry of light modes and shadow modes          │
│  - Compiles Slang → GLSL via jSlang                  │
│  - Reflects parameters → MatParam auto-generation    │
│  - Assembles TechniqueDefs with registered modes     │
│  - Produces standard jME MaterialDef                 │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│           Standard jME Material Pipeline             │
│  MaterialDef → Material → Technique → Shader         │
│  DefineList → shader variant caching                 │
│  UniformBinding → world parameter binding            │
└─────────────────────────────────────────────────────┘
```

## Module Structure

New Gradle module `SlangJme` in the SlangBindings project:

```
SlangJme/
├── build.gradle.kts              # depends on :api + jme3-core from mavenLocal
├── src/main/java/dev/slang/jme/
│   ├── SlangMaterialSystem.java  # Main entry point, registry, loader
│   ├── SlangMaterialDef.java     # Builds MaterialDef from compiled Slang
│   ├── SlangTechniqueConfig.java # Technique configuration (entry points, render state)
│   ├── LightModeConfig.java      # Registered light mode definition
│   ├── ShadowModeConfig.java     # Registered shadow mode definition
│   ├── ReflectionMapper.java     # Maps Slang reflection → jME MatParams/defines
│   └── SlangShaderGenerator.java # Compiles Slang module → GLSL source strings
└── src/test/java/dev/slang/jme/
    └── ...                       # Tests with full jME runtime
```

## Core Components

### 1. SlangMaterialSystem

The central entry point. Holds the jSlang `GlobalSession`, technique registries, and material loading logic.

```java
public class SlangMaterialSystem implements AutoCloseable {
    private final AssetManager assetManager;
    private final GlobalSession globalSession;
    private final Map<String, LightModeConfig> lightModes;
    private final Map<String, ShadowModeConfig> shadowModes;

    public SlangMaterialSystem(AssetManager assetManager);

    // Registry API
    public void registerLightMode(String name, LightModeConfig config);
    public void registerShadowMode(String name, ShadowModeConfig config);

    // Loading API
    public MaterialDef loadMaterialDef(String slangModulePath, SlangTechniqueConfig config);
    public Material loadMaterial(String slangModulePath, SlangTechniqueConfig config);

    public void close(); // releases GlobalSession
}
```

### 2. SlangTechniqueConfig

Describes what the loader should produce for a given material. This is the thin descriptor that bridges Slang shader code with jME-specific concepts.

```java
public class SlangTechniqueConfig {
    private String vertexEntryPoint;    // default: "vertexMain"
    private String fragmentEntryPoint;  // default: "fragmentMain"
    private String lightMode;           // references registered light mode
    private List<String> shadowModes;   // references registered shadow modes
    private RenderState renderState;    // optional override
    private List<String> worldParams;   // e.g., "WorldViewProjectionMatrix"
    private Map<String, String> additionalDefines; // extra static defines

    // Builder pattern
    public static Builder builder() { ... }
}
```

Can be created programmatically or loaded from a descriptor file (future extension).

### 3. LightModeConfig

Registered once, applied to every material that requests this light mode.

```java
public class LightModeConfig {
    private TechniqueDef.LightMode jmeLightMode; // SinglePass, MultiPass, etc.
    private TechniqueDefLogic logic;              // rendering logic impl
    private List<String> requiredWorldParams;     // e.g., LightPosition, LightColor
    private Map<String, VarType> implicitDefines; // e.g., NB_LIGHTS → Int
    private String shaderPrologue;                // prepended GLSL (light uniforms, etc.)
}
```

### 4. ShadowModeConfig

Registered once, adds a shadow technique to every material.

```java
public class ShadowModeConfig {
    private TechniqueDef.ShadowMode jmeShadowMode; // InPass, PostPass
    private String slangModulePath;                 // Slang module for shadow pass
    private String vertexEntryPoint;
    private String fragmentEntryPoint;
    private RenderState renderState;
    private List<String> requiredWorldParams;
}
```

### 5. ReflectionMapper

Maps Slang reflection metadata to jME material constructs.

```java
public class ReflectionMapper {
    // Discovers all material parameters from Slang reflection
    public List<MatParamMapping> extractParameters(ProgramLayout layout);

    // Maps Slang type → jME VarType
    public VarType mapType(TypeReflection type);

    // Extracts world parameter bindings (recognized uniform names)
    public List<UniformBinding> extractWorldBindings(ProgramLayout layout);

    // Generates define mappings for boolean/toggle parameters
    public Map<String, DefineMapping> extractDefines(ProgramLayout layout);
}
```

**Type Mapping Table:**

| Slang Type | TypeReflection.kind() | jME VarType |
|---|---|---|
| `float` | SCALAR | `Float` |
| `float2` | VECTOR | `Vector2` |
| `float3` | VECTOR | `Vector3` |
| `float4` | VECTOR | `Vector4` |
| `int` / `uint` | SCALAR | `Int` |
| `bool` | SCALAR | `Boolean` |
| `float3x3` | MATRIX | `Matrix3` |
| `float4x4` | MATRIX | `Matrix4` |
| `Texture2D` | RESOURCE | `Texture2D` |
| `Texture3D` | RESOURCE | `Texture3D` |
| `TextureCube` | RESOURCE | `TextureCubeMap` |
| `Texture2DArray` | RESOURCE | `TextureArray` |
| `SamplerState` | SAMPLER_STATE | (implicit, paired with texture) |
| `ConstantBuffer<T>` | CONSTANT_BUFFER | `UniformBufferObject` (fields flattened) |
| `RWStructuredBuffer<T>` | RESOURCE | `ShaderStorageBufferObject` |

**World Parameter Recognition:**

Uniforms with names matching jME's `UniformBinding` enum are automatically mapped as world parameters rather than material parameters:

- `WorldViewProjectionMatrix` → `UniformBinding.WorldViewProjectionMatrix`
- `ViewMatrix` → `UniformBinding.ViewMatrix`
- `CameraPosition` → `UniformBinding.CameraPosition`
- `Time` → `UniformBinding.Time`
- etc.

Convention: world parameters use jME binding names in the Slang shader. Material parameters use any other name.

### 6. SlangShaderGenerator

Compiles a Slang module and extracts GLSL source for each entry point.

```java
public class SlangShaderGenerator {
    // Compiles Slang → GLSL, returns source strings per stage
    public ShaderSources compile(Session session, String modulePath,
                                  String vertexEntry, String fragmentEntry);

    // Returns compiled GLSL + reflection data
    public CompilationResult compileWithReflection(Session session, String modulePath,
                                                     String vertexEntry, String fragmentEntry);
}

public record ShaderSources(String vertexGlsl, String fragmentGlsl) {}

public record CompilationResult(ShaderSources sources, ProgramLayout layout) {}
```

## Material Loading Flow

```
loadMaterial("PBR.slang", config)
  │
  ├─ 1. Create jSlang Session with GLSL target
  │
  ├─ 2. Load Slang module from asset path
  │     session.loadModuleFromSourceString(...) or loadModule(...)
  │
  ├─ 3. Find entry points (config.vertexEntryPoint, config.fragmentEntryPoint)
  │
  ├─ 4. Create composite component type → link → extract GLSL
  │     SlangShaderGenerator.compileWithReflection(...)
  │
  ├─ 5. Reflect on compiled program
  │     ReflectionMapper.extractParameters(layout)
  │     ReflectionMapper.extractWorldBindings(layout)
  │     ReflectionMapper.extractDefines(layout)
  │
  ├─ 6. Build MaterialDef
  │     ├─ Create MaterialDef(assetManager, name)
  │     ├─ Add auto-discovered MatParams (non-world uniforms)
  │     ├─ Build main TechniqueDef:
  │     │   ├─ Set GLSL source (inline, not file path)
  │     │   ├─ Set light mode from registered LightModeConfig
  │     │   ├─ Set TechniqueDefLogic from LightModeConfig
  │     │   ├─ Add world params (auto-discovered + light mode required)
  │     │   ├─ Add define mappings (param → define)
  │     │   ├─ Set render state
  │     │   └─ Set shader prologue from LightModeConfig
  │     ├─ For each registered shadow mode in config:
  │     │   ├─ Compile shadow Slang module → GLSL
  │     │   ├─ Build shadow TechniqueDef
  │     │   └─ Add to MaterialDef
  │     └─ materialDef.addTechniqueDef(...)
  │
  └─ 7. Return MaterialDef (or wrap in Material)
```

## Inline Shader Sources

jME's `TechniqueDef.setShaderFile()` expects asset paths. Since we compile Slang → GLSL at load time and get source strings (not files), we need one of:

**Option A: Virtual asset registration** — Register compiled GLSL as virtual assets in jME's AssetManager, then reference them by synthetic path.

**Option B: Custom TechniqueDefLogic** — Override `makeCurrent()` to supply pre-compiled shader objects directly, bypassing the asset-based shader loading.

**Option C: Temp file approach** — Write GLSL to temp files, register path. Simple but inelegant.

**Recommendation: Option B** — A custom `SlangTechniqueDefLogic` that wraps the existing logic implementations (SinglePassLightingLogic, etc.) but supplies the GLSL source directly rather than loading from assets. This gives us full control over shader creation while delegating the rendering logic to jME's existing implementations.

```java
public class SlangTechniqueDefLogic implements TechniqueDefLogic {
    private final TechniqueDefLogic delegate;  // e.g., SinglePassLightingLogic
    private final ShaderSources sources;

    @Override
    public Shader makeCurrent(AssetManager am, RenderManager rm,
                               EnumSet<Caps> caps, LightList lights,
                               DefineList defines) {
        // Build Shader from pre-compiled GLSL sources + current defines
        // Delegate render() to the wrapped logic
    }
}
```

## Define System Integration

Slang parameters that map to jME defines work as follows:

1. **Boolean parameters** → `addShaderParamDefine(paramName, VarType.Boolean, "HAS_" + paramName.toUpperCase())`
   - e.g., Slang `bool useNormalMap` → define `HAS_USENORMALMAP`

2. **Texture parameters** → `addShaderParamDefine(paramName, VarType.Texture2D, "HAS_" + paramName.toUpperCase())`
   - Presence of texture triggers the define

3. **Int/enum parameters** → `addShaderParamDefine(paramName, VarType.Int, paramName.toUpperCase())`
   - Integer value passed as define value (for multi-mode switches)

The generated GLSL from Slang should use `#ifdef` / `#if` guards that match these define names. This requires a convention in the Slang source — parameters that should act as defines need corresponding `#ifdef` guards in the compiled GLSL output. Slang's preprocessor support handles this.

## Uniform Naming Convention

jME prefixes material uniforms with `m_` and world uniforms with `g_`. Slang shaders should use unprefixed names:

- Slang: `float3 baseColor` → jME uniform: `m_baseColor`
- Slang: `float4x4 WorldViewProjectionMatrix` → jME uniform: `g_WorldViewProjectionMatrix`

The `ReflectionMapper` handles the prefix mapping. The compiled GLSL output may need post-processing to add these prefixes, or the `SlangTechniqueDefLogic` handles the mapping at bind time.

## Example Usage

```java
// Setup (once at app init)
SlangMaterialSystem slang = new SlangMaterialSystem(assetManager);

slang.registerLightMode("SinglePass", LightModeConfig.builder()
    .lightMode(TechniqueDef.LightMode.SinglePass)
    .logic(new SinglePassLightingLogic())
    .worldParam("LightPosition")
    .worldParam("LightColor")
    .worldParam("AmbientLightColor")
    .implicitDefine("NB_LIGHTS", VarType.Int)
    .build());

slang.registerShadowMode("PostShadow", ShadowModeConfig.builder()
    .shadowMode(TechniqueDef.ShadowMode.PostPass)
    .slangModule("Shaders/Shadow/PostShadow.slang")
    .vertexEntry("vsMain")
    .fragmentEntry("fsMain")
    .build());

// Load a material
Material pbr = slang.loadMaterial("Shaders/PBR.slang",
    SlangTechniqueConfig.builder()
        .vertexEntry("vsMain")
        .fragmentEntry("fsMain")
        .lightMode("SinglePass")
        .shadowMode("PostShadow")
        .build());

// Use like any jME material
pbr.setTexture("albedoTex", albedoTexture);
pbr.setFloat("roughness", 0.5f);
pbr.setFloat("metallic", 0.0f);
geometry.setMaterial(pbr);
```

## Dependencies

**Main module (`SlangJme`):**
```kotlin
dependencies {
    implementation(project(":api"))
    compileOnly("org.jmonkeyengine:jme3-core:3.10.0-local")
}
```

**Test configuration:**
```kotlin
dependencies {
    testImplementation("org.jmonkeyengine:jme3-core:3.10.0-local")
    testImplementation("org.jmonkeyengine:jme3-desktop:3.10.0-local")
    testImplementation("org.jmonkeyengine:jme3-lwjgl3:3.10.0-local")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}
```

**Repositories:**
```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
```

## Open Questions / Future Work

1. **GLSL post-processing**: The compiled GLSL from Slang may need uniform name prefixing (`m_`, `g_`), `#ifdef` injection for defines, or GLSL version/extension header adjustments. Extent of post-processing TBD during implementation.

2. **Descriptor file format**: Currently technique config is programmatic. A `.slangmat` JSON/YAML descriptor could be added later for asset-pipeline workflows.

3. **Compute shader support**: The `SlangTechniqueConfig` could support compute entry points, producing compute-only techniques. Your jME fork already has compute support.

4. **Shader caching**: Currently compile-at-load. If load times become an issue, compiled GLSL + reflection data could be cached to disk.

5. **AssetLoader integration**: A jME `AssetLoader` implementation (`SlangLoader`) could be registered for `.slang` files, enabling `assetManager.loadAsset("Materials/PBR.slang")`. Requires a way to pass technique config through the asset system.

6. **Bindless texture support**: Your jME fork has bindless textures. The reflection mapper could detect bindless-compatible parameters and generate appropriate bindings.
