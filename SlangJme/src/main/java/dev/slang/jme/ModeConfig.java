package dev.slang.jme;

import com.jme3.material.RenderState;
import com.jme3.material.TechniqueDef;
import com.jme3.material.logic.TechniqueDefLogic;
import com.jme3.shader.VarType;

import java.util.*;

public class ModeConfig {
    private final String vertexEntryPoint;
    private final String fragmentEntryPoint;
    private final TechniqueDef.LightMode lightMode;
    private final TechniqueDef.ShadowMode shadowMode;
    private final TechniqueDefLogic logic;
    private final RenderState renderState;
    private final List<String> requiredWorldParams;
    private final Map<String, VarType> implicitDefines;

    private ModeConfig(Builder builder) {
        this.vertexEntryPoint = builder.vertexEntryPoint;
        this.fragmentEntryPoint = builder.fragmentEntryPoint;
        this.lightMode = builder.lightMode;
        this.shadowMode = builder.shadowMode;
        this.logic = builder.logic;
        this.renderState = builder.renderState;
        this.requiredWorldParams = List.copyOf(builder.requiredWorldParams);
        this.implicitDefines = Map.copyOf(builder.implicitDefines);
    }

    public String vertexEntryPoint() { return vertexEntryPoint; }
    public String fragmentEntryPoint() { return fragmentEntryPoint; }
    public TechniqueDef.LightMode lightMode() { return lightMode; }
    public TechniqueDef.ShadowMode shadowMode() { return shadowMode; }
    public TechniqueDefLogic logic() { return logic; }
    public RenderState renderState() { return renderState; }
    public List<String> requiredWorldParams() { return requiredWorldParams; }
    public Map<String, VarType> implicitDefines() { return implicitDefines; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String vertexEntryPoint = "vertexMain";
        private String fragmentEntryPoint = "fragmentMain";
        private TechniqueDef.LightMode lightMode;
        private TechniqueDef.ShadowMode shadowMode;
        private TechniqueDefLogic logic;
        private RenderState renderState;
        private final List<String> requiredWorldParams = new ArrayList<>();
        private final Map<String, VarType> implicitDefines = new LinkedHashMap<>();

        public Builder vertexEntry(String entry) { this.vertexEntryPoint = entry; return this; }
        public Builder fragmentEntry(String entry) { this.fragmentEntryPoint = entry; return this; }
        public Builder lightMode(TechniqueDef.LightMode mode) { this.lightMode = mode; return this; }
        public Builder shadowMode(TechniqueDef.ShadowMode mode) { this.shadowMode = mode; return this; }
        public Builder logic(TechniqueDefLogic logic) { this.logic = logic; return this; }
        public Builder renderState(RenderState state) { this.renderState = state; return this; }
        public Builder worldParam(String name) { this.requiredWorldParams.add(name); return this; }
        public Builder implicitDefine(String name, VarType type) { this.implicitDefines.put(name, type); return this; }

        public ModeConfig build() { return new ModeConfig(this); }
    }
}
