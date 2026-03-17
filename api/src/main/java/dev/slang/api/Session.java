package dev.slang.api;

import dev.slang.bindings.raw.IComponentType;
import dev.slang.bindings.raw.ISession;
import java.lang.foreign.Arena;

public class Session implements AutoCloseable {
    private final ISession raw;

    public Session(ISession raw) {
        this.raw = raw;
    }

    public Module loadModule(String moduleName) {
        try (Arena arena = Arena.ofConfined()) {
            return new Module(raw.loadModule(arena, moduleName));
        }
    }

    public Module loadModuleFromSourceString(String moduleName, String path, String source) {
        try (Arena arena = Arena.ofConfined()) {
            return new Module(raw.loadModuleFromSourceString(arena, moduleName, path, source));
        }
    }

    public ComponentType createCompositeComponentType(ComponentType... components) {
        try (Arena arena = Arena.ofConfined()) {
            IComponentType[] rawComponents = new IComponentType[components.length];
            for (int i = 0; i < components.length; i++) {
                rawComponents[i] = components[i].raw();
            }
            return new ComponentType(raw.createCompositeComponentType(arena, rawComponents));
        }
    }

    @Override
    public void close() {
        raw.release();
    }
}
