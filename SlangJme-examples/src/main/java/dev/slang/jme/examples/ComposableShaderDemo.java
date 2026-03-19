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
 * Demonstrates shader feature composition using Slang interfaces.
 *
 * Three spheres, each using the same ComposablePBR.slang shader but
 * specialized with different BRDF and normal source implementations:
 *   - Lambert + FlatNormal (red)
 *   - BlinnPhong + FlatNormal (blue)
 *   - CookTorrance + HemisphereNormal (white)
 *
 * Each combination produces a different compiled shader variant at load time,
 * with no runtime overhead from the abstraction.
 */
public class ComposableShaderDemo extends SimpleApplication {

    public static void main(String[] args) {
        ComposableShaderDemo app = new ComposableShaderDemo();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("SlangJme Composable Shader Demo");
        settings.setResolution(1280, 720);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        SlangMaterialSystem.initialize(assetManager);

        // Three spheres with different shader specializations
        String[] materials = {
            "Materials/Lambert.slangmat",
            "Materials/BlinnPhong.slangmat",
            "Materials/CookTorrance.slangmat"
        };
        String[] labels = {"Lambert", "BlinnPhong", "CookTorrance"};

        for (int i = 0; i < materials.length; i++) {
            Material mat = assetManager.loadMaterial(materials[i]);

            Sphere mesh = new Sphere(32, 32, 1.5f);
            Geometry geom = new Geometry(labels[i], mesh);
            geom.setMaterial(mat);
            geom.setLocalTranslation((i - 1) * 4f, 0, 0);
            rootNode.attachChild(geom);
        }

        // Lights
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -1, -1).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(1.5f));
        rootNode.addLight(sun);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.2f));
        rootNode.addLight(ambient);

        cam.setLocation(new Vector3f(0, 0, 12));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        flyCam.setMoveSpeed(10);
    }

    @Override
    public void destroy() {
        super.destroy();
        SlangMaterialSystem.getInstance(assetManager).close();
    }
}
