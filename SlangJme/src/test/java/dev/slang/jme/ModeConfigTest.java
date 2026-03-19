package dev.slang.jme;

import com.jme3.material.TechniqueDef;
import com.jme3.shader.VarType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModeConfigTest {

    @Test
    void buildsWithDefaults() {
        var config = ModeConfig.builder().build();
        assertEquals("vertexMain", config.vertexEntryPoint());
        assertEquals("fragmentMain", config.fragmentEntryPoint());
        assertNull(config.lightMode());
        assertNull(config.shadowMode());
        assertTrue(config.requiredWorldParams().isEmpty());
        assertTrue(config.implicitDefines().isEmpty());
    }

    @Test
    void buildsWithLightMode() {
        var config = ModeConfig.builder()
            .lightMode(TechniqueDef.LightMode.SinglePass)
            .worldParam("LightPosition")
            .worldParam("LightColor")
            .implicitDefine("NB_LIGHTS", VarType.Int)
            .build();
        assertEquals(TechniqueDef.LightMode.SinglePass, config.lightMode());
        assertEquals(2, config.requiredWorldParams().size());
        assertTrue(config.implicitDefines().containsKey("NB_LIGHTS"));
    }

    @Test
    void buildsWithShadowMode() {
        var config = ModeConfig.builder()
            .shadowMode(TechniqueDef.ShadowMode.PostPass)
            .vertexEntry("shadowVs")
            .fragmentEntry("shadowFs")
            .build();
        assertEquals(TechniqueDef.ShadowMode.PostPass, config.shadowMode());
        assertEquals("shadowVs", config.vertexEntryPoint());
        assertEquals("shadowFs", config.fragmentEntryPoint());
    }
}
