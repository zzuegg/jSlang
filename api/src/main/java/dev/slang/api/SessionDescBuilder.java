package dev.slang.api;

import dev.slang.bindings.structs.SessionDesc;
import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

public class SessionDescBuilder {
    private final List<TargetDescBuilder> targets = new ArrayList<>();

    public SessionDescBuilder addTarget(TargetDescBuilder target) {
        targets.add(target);
        return this;
    }

    public MemorySegment build(Arena arena) {
        long targetSize = dev.slang.bindings.structs.TargetDesc.LAYOUT.byteSize();
        MemorySegment targetArray = arena.allocate(targetSize * targets.size());
        for (int i = 0; i < targets.size(); i++) {
            MemorySegment target = targets.get(i).build(arena);
            MemorySegment.copy(target, 0, targetArray, i * targetSize, targetSize);
        }
        return SessionDesc.allocate(arena, targetArray, targets.size());
    }
}
