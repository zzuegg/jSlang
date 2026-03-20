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
 * Demonstrates material inheritance.
 *
 * All materials inherit from PbrDefaults.slangmat (which defines the shader,
 * specializations, and default params). Each child only overrides baseColor.
 */
public class InheritanceDemo extends SimpleApplication {

    public static void main(String[] args) {
        InheritanceDemo app = new InheritanceDemo();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("SlangJme Material Inheritance Demo");
        settings.setResolution(1280, 720);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        SlangMaterialSystem.initialize(assetManager);

        String[] materials = {
            "Materials/PbrDefaults.slangmat",
            "Materials/GreenMetal.slangmat",
            "Materials/RedPlastic.slangmat",
            "Materials/BlueSoft.slangmat",
        };
        String[] labels = {"Default", "GreenMetal", "RedPlastic", "BlueSoft"};

        for (int i = 0; i < materials.length; i++) {
            Material mat = assetManager.loadMaterial(materials[i]);

            Sphere mesh = new Sphere(32, 32, 1.5f);
            Geometry geom = new Geometry(labels[i], mesh);
            geom.setMaterial(mat);
            geom.setLocalTranslation((i - 1.5f) * 4f, 0, 0);
            rootNode.attachChild(geom);
        }

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -1, -1).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(1.5f));
        rootNode.addLight(sun);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.2f));
        rootNode.addLight(ambient);

        cam.setLocation(new Vector3f(0, 0, 14));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        flyCam.setMoveSpeed(10);
    }

    @Override
    public void destroy() {
        super.destroy();
        SlangMaterialSystem.getInstance(assetManager).close();
    }
}
