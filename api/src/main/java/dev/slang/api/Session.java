package dev.slang.api;

import dev.slang.bindings.raw.IComponentType;
import dev.slang.bindings.raw.ISession;
import java.lang.foreign.Arena;

public class Session implements AutoCloseable {
    private final ISession raw;

    public Session(ISession raw) {
        this.raw = raw;
    }

    public Module loadModule(String moduleName) throws SlangException {
        try (Arena arena = Arena.ofConfined()) {
            return new Module(raw.loadModule(arena, moduleName));
        } catch (RuntimeException e) {
            throw new SlangException(e.getMessage(), e);
        }
    }

    public Module loadModuleFromSourceString(String moduleName, String path, String source) throws SlangException {
        try (Arena arena = Arena.ofConfined()) {
            return new Module(raw.loadModuleFromSourceString(arena, moduleName, path, source));
        } catch (RuntimeException e) {
            throw new SlangException(e.getMessage(), e);
        }
    }

    public long getLoadedModuleCount() {
        return raw.getLoadedModuleCount();
    }

    public Module getLoadedModule(int index) {
        return new Module(raw.getLoadedModule(index));
    }

    public ComponentType createCompositeComponentType(ComponentType... components) throws SlangException {
        try (Arena arena = Arena.ofConfined()) {
            IComponentType[] rawComponents = new IComponentType[components.length];
            for (int i = 0; i < components.length; i++) {
                rawComponents[i] = components[i].raw();
            }
            return new ComponentType(raw.createCompositeComponentType(arena, rawComponents));
        } catch (RuntimeException e) {
            throw new SlangException(e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        raw.release();
    }
}
