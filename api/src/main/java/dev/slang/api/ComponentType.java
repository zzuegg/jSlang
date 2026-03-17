package dev.slang.api;

import dev.slang.bindings.raw.IComponentType;
import dev.slang.bindings.raw.ISlangBlob;
import java.lang.foreign.Arena;

public class ComponentType implements AutoCloseable {
    protected final IComponentType raw;

    public ComponentType(IComponentType raw) {
        this.raw = raw;
    }

    public ComponentType link() throws SlangException {
        try (Arena arena = Arena.ofConfined()) {
            return new ComponentType(raw.link(arena));
        } catch (RuntimeException e) {
            throw new SlangException(e.getMessage(), e);
        }
    }

    public Blob getEntryPointCode(int entryPointIndex, int targetIndex) throws SlangException {
        try (Arena arena = Arena.ofConfined()) {
            ISlangBlob blob = raw.getEntryPointCode(arena, entryPointIndex, targetIndex);
            return new Blob(blob);
        } catch (RuntimeException e) {
            throw new SlangException(e.getMessage(), e);
        }
    }

    public Blob getTargetCode(int targetIndex) throws SlangException {
        try (Arena arena = Arena.ofConfined()) {
            ISlangBlob blob = raw.getTargetCode(arena, targetIndex);
            return new Blob(blob);
        } catch (RuntimeException e) {
            throw new SlangException(e.getMessage(), e);
        }
    }

    public long getSpecializationParamCount() {
        return raw.getSpecializationParamCount();
    }

    public ComponentType renameEntryPoint(String newName) throws SlangException {
        try (Arena arena = Arena.ofConfined()) {
            return new ComponentType(raw.renameEntryPoint(arena, newName));
        } catch (RuntimeException e) {
            throw new SlangException(e.getMessage(), e);
        }
    }

    public IComponentType raw() { return raw; }

    @Override
    public void close() {
        raw.release();
    }
}
