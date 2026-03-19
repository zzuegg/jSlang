package dev.slang.api;

import dev.slang.api.reflect.ProgramLayout;
import dev.slang.bindings.raw.IComponentType;
import dev.slang.bindings.raw.ISlangBlob;
import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

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

    public ProgramLayout getLayout(int targetIndex) {
        try (Arena arena = Arena.ofConfined()) {
            return new ProgramLayout(raw.getLayout(arena, targetIndex));
        } catch (RuntimeException e) {
            throw e;
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

    /**
     * Specializes this generic component with the given type names.
     * Types are looked up by name from the program's reflection layout.
     */
    public ComponentType specialize(Arena arena, String... typeNames) throws SlangException {
        MemorySegment layout = raw.getLayout(arena, 0);

        // Build SpecializationArg array: each arg is { int32 kind=1 (Type), padding, SlangReflectionType* }
        MemorySegment specArgs = arena.allocate(16L * typeNames.length);
        for (int i = 0; i < typeNames.length; i++) {
            MemorySegment type = SlangReflection.findTypeByName(layout, arena, typeNames[i]);
            if (type.equals(MemorySegment.NULL)) {
                throw new SlangException("Specialization type not found: " + typeNames[i],
                    new IllegalArgumentException(typeNames[i]));
            }
            long offset = 16L * i;
            specArgs.set(ValueLayout.JAVA_INT, offset, 1); // kind = 1 (Type)
            specArgs.set(ValueLayout.ADDRESS, offset + 8, type);
        }

        try {
            return new ComponentType(raw.specialize(arena, specArgs, typeNames.length));
        } catch (RuntimeException e) {
            throw new SlangException("Specialization failed: " + e.getMessage(), e);
        }
    }

    public IComponentType raw() { return raw; }

    @Override
    public void close() {
        raw.release();
    }
}
