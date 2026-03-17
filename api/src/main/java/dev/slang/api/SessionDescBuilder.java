package dev.slang.api;

import dev.slang.bindings.structs.PreprocessorMacroDesc;
import dev.slang.bindings.structs.SessionDesc;
import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SessionDescBuilder {
    private final List<TargetDescBuilder> targets = new ArrayList<>();
    private final List<String> searchPaths = new ArrayList<>();
    private final Map<String, String> macros = new LinkedHashMap<>();
    private int flags = 0;

    public SessionDescBuilder addTarget(TargetDescBuilder target) {
        targets.add(target);
        return this;
    }

    public SessionDescBuilder addSearchPath(String path) {
        searchPaths.add(path);
        return this;
    }

    public SessionDescBuilder addMacro(String name, String value) {
        macros.put(name, value);
        return this;
    }

    public SessionDescBuilder flags(int flags) {
        this.flags = flags;
        return this;
    }

    public MemorySegment build(Arena arena) {
        long targetSize = dev.slang.bindings.structs.TargetDesc.LAYOUT.byteSize();
        MemorySegment targetArray = arena.allocate(targetSize * targets.size());
        for (int i = 0; i < targets.size(); i++) {
            MemorySegment target = targets.get(i).build(arena);
            MemorySegment.copy(target, 0, targetArray, i * targetSize, targetSize);
        }

        if (searchPaths.isEmpty() && macros.isEmpty() && flags == 0) {
            return SessionDesc.allocate(arena, targetArray, targets.size());
        }

        // Build search paths: char** array
        MemorySegment searchPathArray = MemorySegment.NULL;
        if (!searchPaths.isEmpty()) {
            searchPathArray = arena.allocate(
                ValueLayout.ADDRESS.byteSize() * searchPaths.size());
            for (int i = 0; i < searchPaths.size(); i++) {
                searchPathArray.setAtIndex(ValueLayout.ADDRESS, i,
                    arena.allocateUtf8String(searchPaths.get(i)));
            }
        }

        // Build macros: PreprocessorMacroDesc array
        MemorySegment macroArray = MemorySegment.NULL;
        if (!macros.isEmpty()) {
            long macroSize = PreprocessorMacroDesc.LAYOUT.byteSize();
            macroArray = arena.allocate(macroSize * macros.size());
            int idx = 0;
            for (var entry : macros.entrySet()) {
                MemorySegment macro = PreprocessorMacroDesc.allocate(
                    arena, entry.getKey(), entry.getValue());
                MemorySegment.copy(macro, 0, macroArray, idx * macroSize, macroSize);
                idx++;
            }
        }

        return SessionDesc.allocate(arena, targetArray, targets.size(),
            searchPathArray, searchPaths.size(),
            macroArray, macros.size(), flags);
    }
}
