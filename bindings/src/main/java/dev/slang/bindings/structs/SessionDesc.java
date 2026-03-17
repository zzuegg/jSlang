package dev.slang.bindings.structs;

import java.lang.foreign.*;

public final class SessionDesc {

    private SessionDesc() {}

    public static final long SIZE = 128;

    public static MemorySegment allocate(Arena arena, MemorySegment targets, long targetCount) {
        MemorySegment seg = arena.allocate(SIZE);
        seg.set(ValueLayout.JAVA_LONG, 0, SIZE);
        seg.set(ValueLayout.ADDRESS, 8, targets);
        seg.set(ValueLayout.JAVA_LONG, 16, targetCount);
        return seg;
    }
}
