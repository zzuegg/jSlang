package dev.slang.jme.examples;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.TechniqueDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Sphere;
import com.jme3.system.AppSettings;
import dev.slang.jme.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Demonstrates the SlangJme material system with a PBR shader.
 *
 * Features shown:
 * - Loading a Slang shader and auto-discovering material parameters
 * - Setting material parameters (roughness, metallic, baseColor)
 * - Registering and applying a reusable "DepthOnly" mode
 * - Multiple spheres with varying material properties
 */
public class PbrDemo extends SimpleApplication {

    private SlangMaterialSystem slangSystem;

    public static void main(String[] args) {
        PbrDemo app = new PbrDemo();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("SlangJme PBR Demo");
        settings.setResolution(1280, 720);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        slangSystem = new SlangMaterialSystem(assetManager);

        String pbrSource = loadShaderSource("Shaders/PBR.slang");
        String depthSource = loadShaderSource("Shaders/DepthOnly.slang");

        // Register a reusable depth-only mode (like a shadow pass)
        slangSystem.registerModeFromSource("DepthOnly", depthSource,
            ModeConfig.builder()
                .shadowMode(TechniqueDef.ShadowMode.PostPass)
                .build());

        // Create a grid of spheres with varying roughness and metallic
        int cols = 5;
        int rows = 5;
        float spacing = 2.5f;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float roughness = (float) c / (cols - 1);
                float metallic = (float) r / (rows - 1);

                Geometry sphere = createSphere(
                    "sphere_" + r + "_" + c,
                    roughness, metallic, pbrSource);

                float x = (c - cols / 2f + 0.5f) * spacing;
                float y = (r - rows / 2f + 0.5f) * spacing;
                sphere.setLocalTranslation(x, y, 0);

                rootNode.attachChild(sphere);
            }
        }

        // Lights
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -1, -1).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(1.5f));
        rootNode.addLight(sun);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.2f));
        rootNode.addLight(ambient);

        // Camera position
        cam.setLocation(new Vector3f(0, 0, 15));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        flyCam.setMoveSpeed(10);
    }

    private Geometry createSphere(String name, float roughness, float metallic,
                                    String shaderSource) {
        Sphere mesh = new Sphere(32, 32, 1f);
        Geometry geom = new Geometry(name, mesh);

        try {
            Material mat = slangSystem.loadMaterialFromSource(
                name, shaderSource,
                SlangTechniqueConfig.builder()
                    .mode("DepthOnly")
                    .build());

            mat.setFloat("roughness", roughness);
            mat.setFloat("metallic", metallic);
            mat.setVector3("baseColor", new Vector3f(0.8f, 0.2f, 0.2f));
            mat.setBoolean("useNormalMap", false);

            geom.setMaterial(mat);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create material for " + name, e);
        }

        return geom;
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
