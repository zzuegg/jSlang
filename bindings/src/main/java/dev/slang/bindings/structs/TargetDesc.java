package dev.slang.bindings.structs;

import dev.slang.bindings.enums.CompileTarget;
import java.lang.foreign.*;

public final class TargetDesc {

    private TargetDesc() {}

    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_LONG.withName("structureSize"),
        ValueLayout.JAVA_INT.withName("format"),
        ValueLayout.JAVA_INT.withName("profile"),
        ValueLayout.JAVA_INT.withName("flags"),
        ValueLayout.JAVA_INT.withName("floatingPointMode"),
        ValueLayout.JAVA_INT.withName("lineDirectiveMode"),
        ValueLayout.JAVA_BYTE.withName("forceGLSLScalarBufferLayout"),
        MemoryLayout.paddingLayout(3),
        ValueLayout.ADDRESS.withName("compilerOptionEntries"),
        ValueLayout.JAVA_INT.withName("compilerOptionEntryCount"),
        MemoryLayout.paddingLayout(4)
    );

    public static MemorySegment allocate(Arena arena, CompileTarget target, int profileId) {
        MemorySegment seg = arena.allocate(LAYOUT);
        seg.set(ValueLayout.JAVA_LONG, 0, LAYOUT.byteSize());
        seg.set(ValueLayout.JAVA_INT, 8, target.value());
        seg.set(ValueLayout.JAVA_INT, 12, profileId);
        return seg;
    }
}
