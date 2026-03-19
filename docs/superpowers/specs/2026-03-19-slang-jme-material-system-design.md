# SlangJme Material System Design

## Overview

A loader/bridge layer that compiles Slang shader modules to GLSL and produces standard jMonkeyEngine `MaterialDef`/`Material`/`Shader` objects. Key features:

- **Auto-generated parameters**: Slang reflection discovers uniforms, textures, samplers → `MatParam` declarations
- **Auto-generated defines**: Reflection discovers conditional features → define mappings
- **Runtime define variants**: Re-compiles Slang per define combination (lazy, cached) since Slang resolves all code at compile time
- **Generic mode registry**: Register reusable technique modes (light, shadow, depth, glow, etc.) by name — each mode is a Slang module that gets composed as a separate jME TechniqueDef onto every loaded material
- **Slang-level composition**: Modules, imports, interfaces for shader code reuse within a technique

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   User Code                          │
│  system.registerMode("Shadow", "PostShadow.slang")   │
│  system.registerMode("Light", "SPLight.slang")       │
│  Material mat = system.loadMaterial("PBR.slang")     │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              SlangMaterialSystem                     │
│  - Generic mode registry (name → Slang module)       │
│  - Compiles Slang → GLSL via jSlang                  │
│  - Reflects parameters → MatParam auto-generation    │
│  - Assembles TechniqueDefs from registered modes     │
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

New Gradle module `SlangJme` in the SlangBindings project. Add `include("SlangJme")` to `settings.gradle.kts`.

```
SlangJme/
├── build.gradle.kts              # depends on :api + jme3-core from mavenLocal
├── src/main/java/dev/slang/jme/
│   ├── SlangMaterialSystem.java  # Main entry point, mode registry, loader
│   ├── ModeConfig.java           # Generic technique mode configuration
│   ├── SlangTechniqueConfig.java # Per-material technique configuration
│   ├── ReflectionMapper.java     # Maps Slang reflection → jME MatParams/defines
│   ├── SlangShaderGenerator.java # Compiles Slang module → GLSL source strings
│   ├── GlslPostProcessor.java   # Uniform prefix renaming, attribute mapping
│   └── SlangTechniqueDefLogic.java # Custom TechniqueDefLogic for inline GLSL
└── src/test/java/dev/slang/jme/
    └── ...                       # Tests with full jME runtime
```

## Core Components

### 1. SlangMaterialSystem

The central entry point. Holds the jSlang `GlobalSession`, the generic mode registry, and material loading logic.

```java
public class SlangMaterialSystem implements AutoCloseable {
    private final AssetManager assetManager;
    private final GlobalSession globalSession;
    private final Map<String, ModeConfig> modes;  // generic registry
    private final List<String> searchPaths;        // Slang module search paths

    public SlangMaterialSystem(AssetManager assetManager);

    // Add search paths for Slang module resolution (enables `import`)
    public void addSearchPath(String path);

    // Generic mode registry
    public void registerMode(String name, String slangModulePath, ModeConfig config);

    // Loading API
    public MaterialDef loadMaterialDef(String slangModulePath, SlangTechniqueConfig config);
    public Material loadMaterial(String slangModulePath, SlangTechniqueConfig config);

    public void close(); // releases GlobalSession
}
```

### 2. ModeConfig

A generic technique mode configuration. Covers light modes, shadow modes, depth passes, glow — anything that adds a technique to a material.

```java
public class ModeConfig {
    private String vertexEntryPoint;              // default: "vertexMain"
    private String fragmentEntryPoint;            // default: "fragmentMain"
    private TechniqueDef.LightMode lightMode;     // optional (Disable, SinglePass, etc.)
    private TechniqueDef.ShadowMode shadowMode;   // optional (Disable, InPass, PostPass)
    private TechniqueDefLogic logic;              // optional (rendering logic impl)
    private RenderState renderState;              // optional
    private List<String> requiredWorldParams;     // e.g., LightPosition, LightColor
    private Map<String, VarType> implicitDefines; // e.g., NB_LIGHTS → Int

    public static Builder builder() { ... }
}
```

Usage:
```java
system.registerMode("Light", "Shaders/SinglePassLight.slang", ModeConfig.builder()
    .lightMode(TechniqueDef.LightMode.SinglePass)
    .logic(new SinglePassLightingLogic())
    .worldParam("LightPosition")
    .worldParam("LightColor")
    .implicitDefine("NB_LIGHTS", VarType.Int)
    .build());

system.registerMode("Shadow", "Shaders/PostShadow.slang", ModeConfig.builder()
    .shadowMode(TechniqueDef.ShadowMode.PostPass)
    .build());

system.registerMode("Depth", "Shaders/DepthPrePass.slang", ModeConfig.builder()
    .build());
```

### 3. SlangTechniqueConfig

Per-material configuration. Specifies entry points for the main technique and which registered modes to apply.

```java
public class SlangTechniqueConfig {
    private String vertexEntryPoint;           // default: "vertexMain"
    private String fragmentEntryPoint;         // default: "fragmentMain"
    private List<String> modes;                // registered mode names to apply
    private RenderState renderState;           // optional override for main technique
    private List<String> worldParams;          // additional world params
    private Map<String, String> staticDefines; // defines baked into compilation

    public static Builder builder() { ... }
}
```

### 4. ReflectionMapper

Maps Slang reflection metadata to jME material constructs.

```java
public class ReflectionMapper {
    // Discovers all material parameters from Slang reflection
    public List<MatParamMapping> extractParameters(ProgramLayout layout);

    // Maps Slang type → jME VarType
    public VarType mapType(TypeReflection type);

    // Identifies world parameter bindings by name convention
    public List<UniformBinding> extractWorldBindings(ProgramLayout layout);

    // Generates define mappings for boolean/texture/int parameters
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

### 5. SlangShaderGenerator

Compiles a Slang module and extracts GLSL source for each entry point.

```java
public class SlangShaderGenerator {
    // Compiles Slang → GLSL with a given set of preprocessor defines
    public ShaderSources compile(Session session, String modulePath,
                                  String vertexEntry, String fragmentEntry,
                                  Map<String, String> defines);

    // Returns compiled GLSL + reflection data
    public CompilationResult compileWithReflection(Session session, String modulePath,
                                                     String vertexEntry, String fragmentEntry,
                                                     Map<String, String> defines);
}

public record ShaderSources(String vertexGlsl, String fragmentGlsl) {}
public record CompilationResult(ShaderSources sources, ProgramLayout layout) {}
```

### 6. GlslPostProcessor

Post-processes the GLSL output from Slang to conform to jME conventions.

```java
public class GlslPostProcessor {
    // Renames uniforms: baseColor → m_baseColor, WorldViewProjectionMatrix → g_WorldViewProjectionMatrix
    public String addUniformPrefixes(String glsl, List<MatParamMapping> materialParams,
                                      List<UniformBinding> worldParams);

    // Maps Slang vertex attribute semantics to jME attribute names
    // POSITION → inPosition, NORMAL → inNormal, TEXCOORD0 → inTexCoord, etc.
    public String remapAttributes(String glsl);
}
```

**Attribute Mapping Table:**

| Slang Semantic | jME Attribute Name |
|---|---|
| `POSITION` / `SV_Position` | `inPosition` |
| `NORMAL` | `inNormal` |
| `TANGENT` | `inTangent` |
| `TEXCOORD0` | `inTexCoord` |
| `TEXCOORD1` | `inTexCoord2` |
| `COLOR0` | `inColor` |

### 7. SlangTechniqueDefLogic

Custom `TechniqueDefLogic` that provides pre-compiled GLSL to jME's shader system and re-compiles Slang on define changes.

```java
public class SlangTechniqueDefLogic implements TechniqueDefLogic {
    private final TechniqueDefLogic delegate;       // optional, e.g. SinglePassLightingLogic
    private final SlangShaderGenerator generator;
    private final GlslPostProcessor postProcessor;
    private final String modulePath;
    private final String vertexEntry;
    private final String fragmentEntry;
    private final Map<DefineList, Shader> shaderCache;  // cached variants

    @Override
    public Shader makeCurrent(AssetManager am, RenderManager rm,
                               EnumSet<Caps> caps, LightList lights,
                               DefineList defines) {
        // 1. Let delegate update dynamic defines (e.g., light count) if present
        // 2. Check shaderCache for this DefineList
        // 3. If cache miss:
        //    a. Convert DefineList → Slang preprocessor macro map
        //    b. Re-compile Slang module with those macros
        //    c. Post-process GLSL (uniform prefixes, attribute mapping)
        //    d. Create jME Shader object from GLSL source strings
        //    e. Cache the Shader
        // 4. Return the Shader
    }

    @Override
    public void render(RenderManager rm, Shader shader,
                        Geometry geometry, LightList lights,
                        BindUnits lastBindUnits) {
        // Delegate to wrapped logic, or default render
        if (delegate != null) {
            delegate.render(rm, shader, geometry, lights, lastBindUnits);
        } else {
            DefaultTechniqueDefLogic.renderMeshFromGeometry(rm, geometry);
        }
    }
}
```

**Why re-compile per variant instead of `#ifdef` injection?**

Slang is a full compiler, not a text preprocessor. It resolves all control flow, optimizes dead code, and produces clean GLSL output. `#ifdef` guards are not preserved in the GLSL output. Therefore, each unique set of defines requires a separate Slang compilation. This is the correct approach because:

- Slang compilation is fast (sub-millisecond for typical shaders, proven by project benchmarks)
- Each variant gets fully optimized dead-code elimination
- The `shaderCache` ensures each variant is compiled only once
- This matches jME's existing `definesToShaderMap` caching model

## Module Loading Strategy

Slang modules are located via Slang's search path system, configured to point at jME asset roots:

```java
SlangMaterialSystem system = new SlangMaterialSystem(assetManager);
system.addSearchPath("Shaders/");      // relative to asset root
system.addSearchPath("Common/Shaders/"); // shared shader library
```

This enables Slang `import` statements to resolve across modules:
```slang
// In PBR.slang
import LightingUtils;  // resolves via search paths
import BRDFLibrary;
```

The `SlangMaterialSystem` reads Slang source files from jME's `AssetManager` (using `assetManager.loadAsset()` to get the source text), then passes them to Slang via `session.loadModuleFromSourceString()`. Search paths are also registered with the Slang `Session` so that `import` resolution works for transitive dependencies.

## Material Loading Flow

```
loadMaterial("PBR.slang", config)
  │
  ├─ 1. Create jSlang Session with GLSL target + search paths
  │
  ├─ 2. Read Slang source via AssetManager, load into Slang session
  │
  ├─ 3. Find entry points (config.vertexEntryPoint, config.fragmentEntryPoint)
  │
  ├─ 4. Compile with empty defines (base variant) + extract reflection
  │     SlangShaderGenerator.compileWithReflection(...)
  │
  ├─ 5. Reflect on compiled program
  │     ├─ ReflectionMapper.extractParameters(layout)     → MatParams
  │     ├─ ReflectionMapper.extractWorldBindings(layout)  → UniformBindings
  │     └─ ReflectionMapper.extractDefines(layout)        → define mappings
  │
  ├─ 6. Build MaterialDef
  │     ├─ Create MaterialDef(assetManager, name)
  │     ├─ Add auto-discovered MatParams (non-world uniforms)
  │     ├─ Build main TechniqueDef ("Default"):
  │     │   ├─ Create SlangTechniqueDefLogic (handles re-compilation + caching)
  │     │   ├─ Add world params (auto-discovered)
  │     │   ├─ Add define mappings (param → define)
  │     │   ├─ Set render state
  │     │   └─ Set light mode (Disable by default, or from a referenced mode)
  │     ├─ For each mode in config.modes:
  │     │   ├─ Look up ModeConfig from registry
  │     │   ├─ Compile mode's Slang module → base GLSL
  │     │   ├─ Build TechniqueDef with mode's config (light mode, shadow mode, etc.)
  │     │   ├─ Create SlangTechniqueDefLogic for the mode
  │     │   └─ materialDef.addTechniqueDef(...)
  │     └─ Return MaterialDef
  │
  └─ 7. Wrap in Material, return
```

## Error Handling

- **Slang compilation errors**: `SlangException` is caught and wrapped in a jME-friendly exception (`SlangMaterialException`) with the module path, entry point, and Slang diagnostic message. Logged via jME's `Logger`.
- **Missing modes**: Referencing an unregistered mode name throws `IllegalArgumentException` at load time.
- **Type mapping failures**: Unknown Slang types log a warning and are skipped (the parameter is not added to the MaterialDef).
- **Missing modules**: If the Slang source cannot be found via AssetManager, `AssetNotFoundException` propagates as usual.

## Example Usage

```java
// Setup (once at app init)
SlangMaterialSystem slang = new SlangMaterialSystem(assetManager);
slang.addSearchPath("Shaders/");

// Register reusable modes
slang.registerMode("Light", "Techniques/SinglePassLight.slang", ModeConfig.builder()
    .lightMode(TechniqueDef.LightMode.SinglePass)
    .logic(new SinglePassLightingLogic())
    .worldParam("LightPosition")
    .worldParam("LightColor")
    .worldParam("AmbientLightColor")
    .implicitDefine("NB_LIGHTS", VarType.Int)
    .build());

slang.registerMode("Shadow", "Techniques/PostShadow.slang", ModeConfig.builder()
    .shadowMode(TechniqueDef.ShadowMode.PostPass)
    .build());

slang.registerMode("Depth", "Techniques/DepthPrePass.slang", ModeConfig.builder()
    .build());

// Load a material — gets Light + Shadow + Depth techniques automatically
Material pbr = slang.loadMaterial("Materials/PBR.slang",
    SlangTechniqueConfig.builder()
        .vertexEntry("vsMain")
        .fragmentEntry("fsMain")
        .mode("Light")
        .mode("Shadow")
        .mode("Depth")
        .build());

// Use like any jME material — parameters auto-discovered from reflection
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

repositories {
    mavenLocal()
    mavenCentral()
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

## Open Questions / Future Work

1. **Descriptor file format**: Currently technique config is programmatic. A `.slangmat` JSON descriptor could be added later for asset-pipeline workflows.

2. **Compute shader support**: The `SlangTechniqueConfig` could support compute entry points, producing compute-only techniques via the custom jME fork's compute support.

3. **Disk caching**: If Slang re-compilation per variant becomes a bottleneck, compiled GLSL + reflection data could be serialized to disk for faster subsequent loads.

4. **AssetLoader integration**: A jME `AssetLoader` implementation could be registered for `.slang` files, enabling `assetManager.loadAsset("Materials/PBR.slang")`.

5. **Bindless texture support**: The reflection mapper could detect bindless-compatible parameters and generate appropriate bindings for the custom jME fork.
