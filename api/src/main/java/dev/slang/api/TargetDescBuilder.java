package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.structs.TargetDesc;
import java.lang.foreign.*;

public class TargetDescBuilder {
    private CompileTarget format = CompileTarget.UNKNOWN;
    private int profileId = 0;

    public TargetDescBuilder format(CompileTarget format) {
        this.format = format;
        return this;
    }

    public TargetDescBuilder profile(int profileId) {
        this.profileId = profileId;
        return this;
    }

    public MemorySegment build(Arena arena) {
        return TargetDesc.allocate(arena, format, profileId);
    }
}
