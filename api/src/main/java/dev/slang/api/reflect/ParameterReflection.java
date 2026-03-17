package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;

public class ParameterReflection {
    private final MemorySegment raw;

    public ParameterReflection(MemorySegment raw) { this.raw = raw; }

    public String name() { return SlangReflection.getVariableName(SlangReflection.getVariable(raw)); }
    public TypeReflection type() { return new TypeReflection(SlangReflection.getVariableType(SlangReflection.getVariable(raw))); }
    public TypeLayoutReflection typeLayout() { return new TypeLayoutReflection(SlangReflection.getTypeLayout(raw)); }

    public long bindingOffset(int category) { return SlangReflection.getOffset(raw, category); }
    public long bindingSpace(int category) { return SlangReflection.getBindingSpace(raw, category); }
    public String semanticName() { return SlangReflection.getSemanticName(raw); }
    public int semanticIndex() { return SlangReflection.getSemanticIndex(raw); }

    public MemorySegment raw() { return raw; }
}
