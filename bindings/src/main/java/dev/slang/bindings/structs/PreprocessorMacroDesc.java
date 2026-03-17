package dev.slang.bindings.structs;

import java.lang.foreign.*;

public final class PreprocessorMacroDesc {

    private PreprocessorMacroDesc() {}

    // struct { char* name; char* value; }
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("name"),
        ValueLayout.ADDRESS.withName("value")
    );

    public static MemorySegment allocate(Arena arena, String name, String value) {
        MemorySegment seg = arena.allocate(LAYOUT);
        seg.set(ValueLayout.ADDRESS, 0, arena.allocateUtf8String(name));
        seg.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize(),
            arena.allocateUtf8String(value));
        return seg;
    }
}
