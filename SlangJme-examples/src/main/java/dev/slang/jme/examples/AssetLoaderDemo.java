package dev.slang.jme.examples;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Sphere;
import com.jme3.system.AppSettings;
import dev.slang.jme.SlangMaterialSystem;

/**
 * Demonstrates loading Slang materials via jME's standard asset system.
 *
 * Usage:
 *   Material mat = assetManager.loadMaterial("Materials/PbrRed.slangmat");
 */
public class AssetLoaderDemo extends SimpleApplication {

    public static void main(String[] args) {
        AssetLoaderDemo app = new AssetLoaderDemo();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("SlangJme Asset Loader Demo");
        settings.setResolution(1280, 720);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        // One-time setup: register .slang and .slangmat loaders
        SlangMaterialSystem.initialize(assetManager);

        // Load material just like any jME material
        Material mat = assetManager.loadMaterial("Materials/PbrRed.slangmat");

        Sphere mesh = new Sphere(32, 32, 2f);
        Geometry geom = new Geometry("sphere", mesh);
        geom.setMaterial(mat);
        rootNode.attachChild(geom);

        // Lights
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -1, -1).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(1.5f));
        rootNode.addLight(sun);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.2f));
        rootNode.addLight(ambient);

        cam.setLocation(new Vector3f(0, 0, 8));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        flyCam.setMoveSpeed(10);
    }

    @Override
    public void destroy() {
        super.destroy();
        SlangMaterialSystem.getInstance(assetManager).close();
    }
}
