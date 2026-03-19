package dev.slang.jme;

import com.jme3.shader.UniformBinding;
import dev.slang.api.*;
import dev.slang.api.reflect.ProgramLayout;
import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;

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

            // Extract all reflection data NOW while the session/layout is still alive.
            // ProgramLayout holds a raw native pointer that becomes dangling after session close.
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
