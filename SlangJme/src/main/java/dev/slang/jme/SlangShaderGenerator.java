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

            ProgramLayout layout = linked.getLayout(0);
            return new CompilationResult(
                new ShaderSources(vertexGlsl, fragmentGlsl), layout);
        }
    }
}
