package dev.slang.jme;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads Slang shader files (.slang) as jME MaterialDef instances.
 *
 * <p>The Slang source is compiled and reflected upon to auto-discover material
 * parameters, world bindings, and define mappings.
 *
 * <p>Register with: {@code assetManager.registerLoader(SlangMaterialDefLoader.class, "slang")}
 */
public class SlangMaterialDefLoader implements AssetLoader {

    @Override
    public Object load(AssetInfo assetInfo) throws IOException {
        String sourceCode;
        try (InputStream in = assetInfo.openStream()) {
            sourceCode = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        String name = assetInfo.getKey().getName();
        var system = SlangMaterialSystem.getInstance(assetInfo.getManager());

        try {
            return system.loadMaterialDefFromSource(name, sourceCode,
                SlangTechniqueConfig.builder().build());
        } catch (Exception e) {
            throw new IOException("Failed to compile Slang shader: " + name, e);
        }
    }
}
