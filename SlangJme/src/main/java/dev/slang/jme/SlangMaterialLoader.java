package dev.slang.jme;

import com.jme3.asset.*;
import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.shader.VarType;
import com.jme3.texture.Texture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads Slang material instance files (.slangmat).
 *
 * <p>Format:
 * <pre>
 * SlangMaterial {
 *     MaterialDef: Shaders/PBR.slang
 *
 *     Float roughness: 0.5
 *     Float metallic: 0.0
 *     Vector3 baseColor: 0.8 0.2 0.2
 *     Vector4 tintColor: 1.0 1.0 1.0 1.0
 *     Boolean useNormalMap: false
 *     Texture2D normalMap: Textures/normal.png
 *     Color ambient: 0.2 0.2 0.2 1.0
 * }
 * </pre>
 *
 * <p>Register with: {@code assetManager.registerLoader(SlangMaterialLoader.class, "slangmat")}
 */
public class SlangMaterialLoader implements AssetLoader {

    private static final Logger log = Logger.getLogger(SlangMaterialLoader.class.getName());

    @Override
    public Object load(AssetInfo assetInfo) throws IOException {
        AssetManager assetManager = assetInfo.getManager();
        AssetKey<?> key = assetInfo.getKey();

        String materialDefPath = null;
        Material material = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(assetInfo.openStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue;
                if (line.equals("SlangMaterial {") || line.equals("}")) continue;

                if (line.startsWith("MaterialDef:") || line.startsWith("MaterialDef :")) {
                    materialDefPath = line.substring(line.indexOf(':') + 1).trim();
                    MaterialDef matDef = (MaterialDef) assetManager.loadAsset(
                        new AssetKey<>(materialDefPath));
                    material = new Material(matDef);
                    continue;
                }

                if (material == null) {
                    throw new IOException("MaterialDef must be specified before parameters in " + key);
                }

                parseParameter(material, line, assetManager, key);
            }
        }

        if (material == null) {
            throw new IOException("No MaterialDef specified in " + key);
        }

        return material;
    }

    private void parseParameter(Material material, String line,
                                 AssetManager assetManager, AssetKey<?> key) throws IOException {
        // Format: Type name: value
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return;

        String typeAndName = line.substring(0, colonIdx).trim();
        String value = line.substring(colonIdx + 1).trim();

        int spaceIdx = typeAndName.indexOf(' ');
        if (spaceIdx < 0) return;

        String typeName = typeAndName.substring(0, spaceIdx).trim();
        String paramName = typeAndName.substring(spaceIdx + 1).trim();

        try {
            switch (typeName) {
                case "Float" -> material.setFloat(paramName, Float.parseFloat(value));
                case "Int" -> material.setInt(paramName, Integer.parseInt(value));
                case "Boolean" -> material.setBoolean(paramName, Boolean.parseBoolean(value));
                case "Vector2" -> {
                    float[] v = parseFloats(value, 2);
                    material.setVector2(paramName, new Vector2f(v[0], v[1]));
                }
                case "Vector3" -> {
                    float[] v = parseFloats(value, 3);
                    material.setVector3(paramName, new Vector3f(v[0], v[1], v[2]));
                }
                case "Vector4" -> {
                    float[] v = parseFloats(value, 4);
                    material.setVector4(paramName, new Vector4f(v[0], v[1], v[2], v[3]));
                }
                case "Color" -> {
                    float[] v = parseFloats(value, 4);
                    material.setColor(paramName, new ColorRGBA(v[0], v[1], v[2], v[3]));
                }
                case "Texture2D" -> {
                    Texture tex = assetManager.loadTexture(value);
                    material.setTexture(paramName, tex);
                }
                default -> log.log(Level.WARNING, "Unknown parameter type ''{0}'' in {1}",
                    new Object[]{typeName, key});
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to set parameter ''{0}'' in {1}: {2}",
                new Object[]{paramName, key, e.getMessage()});
        }
    }

    private float[] parseFloats(String value, int count) {
        String[] parts = value.trim().split("\\s+");
        float[] result = new float[count];
        for (int i = 0; i < count && i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }
}
