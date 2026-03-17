package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;

public class VariableReflection {
    private final MemorySegment raw;

    public VariableReflection(MemorySegment raw) { this.raw = raw; }

    public String name() { return SlangReflection.getVariableName(raw); }
    public TypeReflection type() { return new TypeReflection(SlangReflection.getVariableType(raw)); }

    public MemorySegment raw() { return raw; }
}
