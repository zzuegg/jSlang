package dev.slang.api;

import dev.slang.bindings.raw.IComponentType;
import dev.slang.bindings.raw.ISlangBlob;
import java.lang.foreign.Arena;

public class ComponentType implements AutoCloseable {
    protected final IComponentType raw;

    public ComponentType(IComponentType raw) {
        this.raw = raw;
    }

    public ComponentType link() {
        try (Arena arena = Arena.ofConfined()) {
            return new ComponentType(raw.link(arena));
        }
    }

    public Blob getEntryPointCode(int entryPointIndex, int targetIndex) {
        try (Arena arena = Arena.ofConfined()) {
            ISlangBlob blob = raw.getEntryPointCode(arena, entryPointIndex, targetIndex);
            return new Blob(blob);
        }
    }

    public Blob getTargetCode(int targetIndex) {
        try (Arena arena = Arena.ofConfined()) {
            ISlangBlob blob = raw.getTargetCode(arena, targetIndex);
            return new Blob(blob);
        }
    }

    public IComponentType raw() { return raw; }

    @Override
    public void close() {
        raw.release();
    }
}
