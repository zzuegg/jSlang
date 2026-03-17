package dev.slang.bindings.raw;

import java.lang.foreign.*;

public class IEntryPoint extends IComponentType {
    private static final int SLOT_GET_FUNCTION_REFLECTION = 17;

    public IEntryPoint(MemorySegment self) {
        super(self);
    }
}
