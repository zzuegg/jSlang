package dev.slang.jme.examples;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.TechniqueDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;
import com.jme3.system.AppSettings;
import dev.slang.jme.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Demonstrates multiple Slang shader materials in the same scene,
 * all sharing the same registered DepthOnly mode.
 *
 * Features shown:
 * - Multiple different Slang shaders (PBR, Unlit, Toon)
 * - All share the same registered DepthOnly mode (composable techniques)
 * - Different parameter types: float, float3, float4, int, bool, Texture2D
 * - Define-based variants (HAS_ALBEDOTEX enabled vs disabled)
 */
public class MultiMaterialDemo extends SimpleApplication {

    private SlangMaterialSystem slangSystem;

    public static void main(String[] args) {
        MultiMaterialDemo app = new MultiMaterialDemo();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("SlangJme Multi-Material Demo");
        settings.setResolution(1280, 720);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        slangSystem = new SlangMaterialSystem(assetManager);

        String depthSource = loadShaderSource("Shaders/DepthOnly.slang");

        // Register the shared depth-only mode ONCE — applied to all materials
        slangSystem.registerModeFromSource("DepthOnly", depthSource,
            ModeConfig.builder()
                .shadowMode(TechniqueDef.ShadowMode.PostPass)
                .build());

        // === PBR Sphere (left) ===
        createPbrSphere();

        // === Unlit Box (center) ===
        createUnlitBox();

        // === Toon Torus (right) ===
        createToonTorus();

        // Lights
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -1, -1).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        rootNode.addLight(sun);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.3f));
        rootNode.addLight(ambient);

        // Camera
        cam.setLocation(new Vector3f(0, 2, 10));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        flyCam.setMoveSpeed(10);
    }

    private void createPbrSphere() {
        String pbrSource = loadShaderSource("Shaders/PBR.slang");
        Sphere mesh = new Sphere(32, 32, 1.5f);
        Geometry geom = new Geometry("PBR Sphere", mesh);

        try {
            // PBR material with the shared DepthOnly mode
            Material mat = slangSystem.loadMaterialFromSource("pbr", pbrSource,
                SlangTechniqueConfig.builder()
                    .mode("DepthOnly")
                    .build());

            mat.setFloat("roughness", 0.3f);
            mat.setFloat("metallic", 0.8f);
            mat.setVector3("baseColor", new Vector3f(0.9f, 0.6f, 0.2f)); // gold
            mat.setBoolean("useNormalMap", false);
            geom.setMaterial(mat);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PBR material", e);
        }

        geom.setLocalTranslation(-4, 0, 0);
        rootNode.attachChild(geom);
    }

    private void createUnlitBox() {
        String unlitSource = loadShaderSource("Shaders/Unlit.slang");
        Box mesh = new Box(1, 1, 1);
        Geometry geom = new Geometry("Unlit Box", mesh);

        try {
            // Unlit material — same DepthOnly mode, totally different shader
            Material mat = slangSystem.loadMaterialFromSource("unlit", unlitSource,
                SlangTechniqueConfig.builder()
                    .mode("DepthOnly")
                    .build());

            // Unlit just needs a color
            mat.setVector4("color", new Vector4f(0.2f, 0.8f, 0.3f, 1.0f));
            geom.setMaterial(mat);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Unlit material", e);
        }

        geom.setLocalTranslation(0, 0, 0);
        rootNode.attachChild(geom);
    }

    private void createToonTorus() {
        String toonSource = loadShaderSource("Shaders/Toon.slang");
        Torus mesh = new Torus(32, 16, 0.5f, 1.5f);
        Geometry geom = new Geometry("Toon Torus", mesh);

        try {
            // Toon material — again with the shared DepthOnly mode
            Material mat = slangSystem.loadMaterialFromSource("toon", toonSource,
                SlangTechniqueConfig.builder()
                    .mode("DepthOnly")
                    .build());

            mat.setVector3("baseColor", new Vector3f(0.3f, 0.3f, 0.9f)); // blue
            mat.setInt("toonLevels", 3);
            mat.setFloat("outlineWidth", 0.02f);
            geom.setMaterial(mat);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Toon material", e);
        }

        geom.setLocalTranslation(4, 0, 0);
        rootNode.attachChild(geom);
    }

    private String loadShaderSource(String resourcePath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) throw new RuntimeException("Shader not found: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + resourcePath, e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (slangSystem != null) slangSystem.close();
    }
}
