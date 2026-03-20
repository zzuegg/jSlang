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
    private final List<String> specializationTypes;
    private final List<String> searchPaths;
    private final Map<String, Shader> shaderCache = new ConcurrentHashMap<>();

    private Set<String> materialParamNames;
    private Set<String> worldParamNames;

    public SlangTechniqueDefLogic(TechniqueDef techniqueDef,
                                   TechniqueDefLogic delegate,
                                   SlangShaderGenerator generator,
                                   GlslPostProcessor postProcessor,
                                   String moduleName, String sourceCode,
                                   String vertexEntry, String fragmentEntry) {
        this(techniqueDef, delegate, generator, postProcessor,
             moduleName, sourceCode, vertexEntry, fragmentEntry, List.of(), List.of());
    }

    public SlangTechniqueDefLogic(TechniqueDef techniqueDef,
                                   TechniqueDefLogic delegate,
                                   SlangShaderGenerator generator,
                                   GlslPostProcessor postProcessor,
                                   String moduleName, String sourceCode,
                                   String vertexEntry, String fragmentEntry,
                                   List<String> specializationTypes,
                                   List<String> searchPaths) {
        this.techniqueDef = techniqueDef;
        this.delegate = delegate;
        this.generator = generator;
        this.postProcessor = postProcessor;
        this.moduleName = moduleName;
        this.sourceCode = sourceCode;
        this.vertexEntry = vertexEntry;
        this.fragmentEntry = fragmentEntry;
        this.specializationTypes = specializationTypes;
        this.searchPaths = searchPaths;
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

        // Cache key includes defines + specialization types
        String cacheKey = defines.deepClone().toString() + "|" + String.join(",", specializationTypes);
        Shader cached = shaderCache.get(cacheKey);
        if (cached != null) return cached;

        // Convert DefineList → Slang preprocessor macros
        Map<String, String> macros = defineListToMacros(defines);

        try {
            // Use the original module name for module-based loading (sourceCode == null),
            // append variant suffix only for source-string loading to avoid name collisions
            String compileModuleName = sourceCode == null
                ? moduleName
                : moduleName + "_v" + shaderCache.size();
            var result = generator.compileSpecialized(
                compileModuleName,
                sourceCode, vertexEntry, fragmentEntry, macros,
                searchPaths, specializationTypes);

            // Post-process GLSL
            String vertexGlsl = result.vertexGlsl();
            String fragmentGlsl = result.fragmentGlsl();
            if (materialParamNames != null) {
                vertexGlsl = postProcessor.process(vertexGlsl, materialParamNames, worldParamNames);
                fragmentGlsl = postProcessor.process(fragmentGlsl, materialParamNames, worldParamNames);
            }

            // Build jME Shader (empty string for defines — null would append "null" literal)
            Shader shader = new Shader();
            shader.addSource(Shader.ShaderType.Vertex, moduleName + ".vert",
                vertexGlsl, "", "GLSL450");
            shader.addSource(Shader.ShaderType.Fragment, moduleName + ".frag",
                fragmentGlsl, "", "GLSL450");

            for (UniformBinding binding : techniqueDef.getWorldBindings()) {
                shader.addUniformBinding(binding);
            }

            shaderCache.put(cacheKey, shader);
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
