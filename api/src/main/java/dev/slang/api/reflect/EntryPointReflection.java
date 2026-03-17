package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

public class EntryPointReflection {
    private final MemorySegment raw;

    public EntryPointReflection(MemorySegment raw) { this.raw = raw; }

    public String name() { return SlangReflection.getEntryPointName(raw); }
    public int stage() { return SlangReflection.getEntryPointStage(raw); }
    public int parameterCount() { return SlangReflection.getEntryPointParameterCount(raw); }

    public ParameterReflection parameter(int index) {
        return new ParameterReflection(SlangReflection.getEntryPointParameterByIndex(raw, index));
    }

    public List<ParameterReflection> parameters() {
        int n = parameterCount();
        var list = new ArrayList<ParameterReflection>(n);
        for (int i = 0; i < n; i++) list.add(parameter(i));
        return list;
    }

    public long[] threadGroupSize() {
        try (Arena arena = Arena.ofConfined()) {
            return SlangReflection.getComputeThreadGroupSize(arena, raw);
        }
    }

    public MemorySegment raw() { return raw; }
}
