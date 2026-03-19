package dev.slang.jme;

import com.jme3.material.RenderState;

import java.util.*;

public class SlangTechniqueConfig {
    private final String vertexEntryPoint;
    private final String fragmentEntryPoint;
    private final List<String> modes;
    private final RenderState renderState;
    private final List<String> worldParams;
    private final Map<String, String> staticDefines;
    private final List<String> specializationTypes;

    private SlangTechniqueConfig(Builder builder) {
        this.vertexEntryPoint = builder.vertexEntryPoint;
        this.fragmentEntryPoint = builder.fragmentEntryPoint;
        this.modes = List.copyOf(builder.modes);
        this.renderState = builder.renderState;
        this.worldParams = List.copyOf(builder.worldParams);
        this.staticDefines = Map.copyOf(builder.staticDefines);
        this.specializationTypes = List.copyOf(builder.specializationTypes);
    }

    public String vertexEntryPoint() { return vertexEntryPoint; }
    public String fragmentEntryPoint() { return fragmentEntryPoint; }
    public List<String> modes() { return modes; }
    public RenderState renderState() { return renderState; }
    public List<String> worldParams() { return worldParams; }
    public Map<String, String> staticDefines() { return staticDefines; }
    public List<String> specializationTypes() { return specializationTypes; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String vertexEntryPoint = "vertexMain";
        private String fragmentEntryPoint = "fragmentMain";
        private final List<String> modes = new ArrayList<>();
        private RenderState renderState;
        private final List<String> worldParams = new ArrayList<>();
        private final Map<String, String> staticDefines = new LinkedHashMap<>();
        private final List<String> specializationTypes = new ArrayList<>();

        public Builder vertexEntry(String entry) { this.vertexEntryPoint = entry; return this; }
        public Builder fragmentEntry(String entry) { this.fragmentEntryPoint = entry; return this; }
        public Builder mode(String modeName) { this.modes.add(modeName); return this; }
        public Builder renderState(RenderState state) { this.renderState = state; return this; }
        public Builder worldParam(String name) { this.worldParams.add(name); return this; }
        public Builder staticDefine(String name, String value) { this.staticDefines.put(name, value); return this; }
        public Builder specialize(String typeName) { this.specializationTypes.add(typeName); return this; }

        public SlangTechniqueConfig build() { return new SlangTechniqueConfig(this); }
    }
}
