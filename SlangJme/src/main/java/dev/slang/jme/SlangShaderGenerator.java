package dev.slang.jme;

import com.jme3.shader.UniformBinding;
import dev.slang.api.*;
import dev.slang.api.reflect.ProgramLayout;
import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;

import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SlangShaderGenerator {

    public record ShaderSources(String vertexGlsl, String fragmentGlsl) {}

    /**
     * Pre-extracted reflection data that is safe to use after the Slang session is closed.
     * ProgramLayout holds a raw native pointer that becomes invalid after session close,
     * so we extract all needed data eagerly.
     */
    public record ReflectionData(
        List<ReflectionMapper.MatParamMapping> materialParams,
        List<UniformBinding> worldBindings,
        Map<String, ReflectionMapper.DefineMapping> defines
    ) {}

    public record CompilationResult(ShaderSources sources, ReflectionData reflection) {}

    private final GlobalSession globalSession;
    private final ReflectionMapper reflectionMapper;
    private final int glslProfile;

    public SlangShaderGenerator(GlobalSession globalSession) {
        this.globalSession = globalSession;
        this.reflectionMapper = new ReflectionMapper();
        this.glslProfile = globalSession.findProfile("glsl_450");
    }

    public ShaderSources compile(String moduleName, String sourceCode,
                                  String vertexEntry, String fragmentEntry,
                                  Map<String, String> defines) throws SlangException {
        return compileWithReflection(moduleName, sourceCode, vertexEntry, fragmentEntry,
            defines, List.of()).sources();
    }

    public ShaderSources compileSpecialized(String moduleName, String sourceCode,
                                             String vertexEntry, String fragmentEntry,
                                             Map<String, String> defines,
                                             List<String> searchPaths,
                                             List<String> specializationTypes) throws SlangException {
        return compileWithReflection(moduleName, sourceCode, vertexEntry, fragmentEntry,
            defines, searchPaths, specializationTypes).sources();
    }

    public CompilationResult compileWithReflection(String moduleName, String sourceCode,
                                                     String vertexEntry, String fragmentEntry,
                                                     Map<String, String> defines,
                                                     List<String> searchPaths) throws SlangException {
        return compileWithReflection(moduleName, sourceCode, vertexEntry, fragmentEntry,
            defines, searchPaths, List.of());
    }

    public CompilationResult compileWithReflection(String moduleName, String sourceCode,
                                                     String vertexEntry, String fragmentEntry,
                                                     Map<String, String> defines,
                                                     List<String> searchPaths,
                                                     List<String> specializationTypes) throws SlangException {
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
            // If search paths are set and no source provided, load by module name
            // so Slang resolves imports from the search paths.
            // If source is provided but search paths exist, still use source string
            // but Slang will resolve imports from search paths.
            dev.slang.api.Module module;
            if (sourceCode == null) {
                module = session.loadModule(moduleName);
            } else {
                module = session.loadModuleFromSourceString(
                    moduleName, moduleName + ".slang", sourceCode);
            }

            var vsEp = module.findAndCheckEntryPoint(vertexEntry, Stage.VERTEX);
            var fsEp = module.findAndCheckEntryPoint(fragmentEntry, Stage.FRAGMENT);
            var composite = session.createCompositeComponentType(module, vsEp, fsEp);

            // Specialize if type names are provided
            ComponentType toLink;
            if (!specializationTypes.isEmpty()) {
                try (Arena arena = Arena.ofConfined()) {
                    toLink = composite.specialize(arena,
                        specializationTypes.toArray(String[]::new));
                }
            } else {
                toLink = composite;
            }

            var linked = toLink.link();

            String vertexGlsl;
            try (var blob = linked.getEntryPointCode(0, 0)) {
                vertexGlsl = new String(blob.toByteArray(), StandardCharsets.UTF_8);
            }

            String fragmentGlsl;
            try (var blob = linked.getEntryPointCode(1, 0)) {
                fragmentGlsl = new String(blob.toByteArray(), StandardCharsets.UTF_8);
            }

            // Extract all reflection data NOW while the session/layout is still alive.
            ProgramLayout layout = linked.getLayout(0);
            var materialParams = reflectionMapper.extractParameters(layout);
            var worldBindings = reflectionMapper.extractWorldBindings(layout);
            var extractedDefines = reflectionMapper.extractDefines(layout);

            return new CompilationResult(
                new ShaderSources(vertexGlsl, fragmentGlsl),
                new ReflectionData(materialParams, worldBindings, extractedDefines));
        }
    }
}
