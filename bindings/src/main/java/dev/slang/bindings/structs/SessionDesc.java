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

    public static MemorySegment allocate(Arena arena, MemorySegment targets, long targetCount,
            MemorySegment searchPaths, long searchPathCount,
            MemorySegment macros, long macroCount, int flags) {
        MemorySegment seg = arena.allocate(SIZE);
        seg.set(ValueLayout.JAVA_LONG, 0, SIZE);        // structureSize
        seg.set(ValueLayout.ADDRESS, 8, targets);        // targets
        seg.set(ValueLayout.JAVA_LONG, 16, targetCount); // targetCount
        seg.set(ValueLayout.JAVA_INT, 24, flags);        // flags
        seg.set(ValueLayout.ADDRESS, 32, searchPaths);   // searchPaths
        seg.set(ValueLayout.JAVA_LONG, 40, searchPathCount); // searchPathCount
        seg.set(ValueLayout.ADDRESS, 48, macros);        // macros
        seg.set(ValueLayout.JAVA_LONG, 56, macroCount);  // macroCount
        return seg;
    }
}
