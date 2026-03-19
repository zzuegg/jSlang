package dev.slang.jme.examples;

import dev.slang.api.GlobalSession;
import dev.slang.jme.GlslPostProcessor;
import dev.slang.jme.SlangShaderGenerator;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

public class DebugShaderOutput {
    public static void main(String[] args) throws Exception {
        String pbrSource;
        try (InputStream in = DebugShaderOutput.class.getClassLoader().getResourceAsStream("Shaders/PBR.slang")) {
            pbrSource = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        var global = GlobalSession.create();
        var generator = new SlangShaderGenerator(global);
        var postProcessor = new GlslPostProcessor();

        var result = generator.compileWithReflection("pbr", pbrSource,
            "vertexMain", "fragmentMain", Map.of(), java.util.List.of());

        Set<String> matNames = new java.util.HashSet<>();
        for (var p : result.reflection().materialParams()) matNames.add(p.name());
        Set<String> worldNames = new java.util.HashSet<>();
        for (var b : result.reflection().worldBindings()) worldNames.add(b.name());

        String vs = postProcessor.process(result.sources().vertexGlsl(), matNames, worldNames);
        String fs = postProcessor.process(result.sources().fragmentGlsl(), matNames, worldNames);

        System.out.println("=== VERTEX ===");
        System.out.println(vs);
        System.out.println("=== FRAGMENT ===");
        System.out.println(fs);

        global.close();
    }
}
