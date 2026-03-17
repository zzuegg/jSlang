package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

public class ProgramLayout {
    private final MemorySegment raw;

    public ProgramLayout(MemorySegment raw) { this.raw = raw; }

    public int parameterCount() { return SlangReflection.getParameterCount(raw); }

    public ParameterReflection parameter(int index) {
        return new ParameterReflection(SlangReflection.getParameterByIndex(raw, index));
    }

    public List<ParameterReflection> parameters() {
        int n = parameterCount();
        var list = new ArrayList<ParameterReflection>(n);
        for (int i = 0; i < n; i++) list.add(parameter(i));
        return list;
    }

    public int entryPointCount() { return SlangReflection.getEntryPointCount(raw); }

    public EntryPointReflection entryPoint(int index) {
        return new EntryPointReflection(SlangReflection.getEntryPointByIndex(raw, index));
    }

    public List<EntryPointReflection> entryPoints() {
        int n = entryPointCount();
        var list = new ArrayList<EntryPointReflection>(n);
        for (int i = 0; i < n; i++) list.add(entryPoint(i));
        return list;
    }

    public MemorySegment raw() { return raw; }
}
