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

    private static volatile SlangMaterialSystem instance;

    private final AssetManager assetManager;
    private final GlobalSession globalSession;
    private final SlangShaderGenerator generator;
    private final GlslPostProcessor postProcessor;
    private final List<String> searchPaths = new ArrayList<>();

    private final Map<String, RegisteredMode> modes = new LinkedHashMap<>();

    private record RegisteredMode(String sourceCode, ModeConfig config) {}

    /**
     * Returns the singleton instance, creating it if necessary.
     */
    public static SlangMaterialSystem getInstance(AssetManager assetManager) {
        if (instance == null) {
            synchronized (SlangMaterialSystem.class) {
                if (instance == null) {
                    instance = new SlangMaterialSystem(assetManager);
                }
            }
        }
        return instance;
    }

    /**
     * Registers the Slang asset loaders (.slang and .slangmat) with the given AssetManager
     * and initializes the singleton instance.
     */
    public static SlangMaterialSystem initialize(AssetManager assetManager) {
        assetManager.registerLoader(SlangMaterialDefLoader.class, "slang");
        assetManager.registerLoader(SlangMaterialLoader.class, "slangmat");
        return getInstance(assetManager);
    }

    public SlangMaterialSystem(AssetManager assetManager) {
        this.assetManager = assetManager;
        this.globalSession = GlobalSession.create();
        this.generator = new SlangShaderGenerator(globalSession);
        this.postProcessor = new GlslPostProcessor();
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
            config.staticDefines(), searchPaths);

        // 2. Use pre-extracted reflection data (extracted while session was alive)
        var materialParams = result.reflection().materialParams();
        var worldBindings = result.reflection().worldBindings();
        var defines = result.reflection().defines();

        // 3. Build MaterialDef
        MaterialDef matDef = new MaterialDef(assetManager, name);

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

        // Collect ALL material param names for GLSL post-processing
        Set<String> matParamNames = new HashSet<>();
        for (var mp : materialParams) {
            matParamNames.add(mp.name());
        }

        Set<String> worldParamNames = new HashSet<>();
        for (UniformBinding b : worldBindings) {
            worldParamNames.add(b.name());
        }

        // Set dummy shader paths (required by TechniqueDef internals)
        techniqueDef.setShaderFile(moduleName + ".vert", moduleName + ".frag", "GLSL450", "GLSL450");

        // Create custom logic that handles Slang compilation
        var logic = new SlangTechniqueDefLogic(
            techniqueDef,
            modeConfig != null ? modeConfig.logic() : null,
            generator, postProcessor,
            moduleName, sourceCode, vertexEntry, fragmentEntry);
        logic.setParamNames(matParamNames, worldParamNames);
        techniqueDef.setLogic(logic);

        return techniqueDef;
    }

    private TechniqueDef buildModeTechniqueDef(String modeName,
                                                RegisteredMode registered) throws SlangException {
        var modeConfig = registered.config();

        var result = generator.compileWithReflection(
            modeName, registered.sourceCode(),
            modeConfig.vertexEntryPoint(), modeConfig.fragmentEntryPoint(),
            Map.of(), searchPaths);

        var modeMatParams = result.reflection().materialParams();
        var worldBindings = result.reflection().worldBindings();
        var defines = result.reflection().defines();

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
