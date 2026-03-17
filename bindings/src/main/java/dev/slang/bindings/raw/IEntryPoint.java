package dev.slang.bindings.raw;

import java.lang.foreign.*;

public class IEntryPoint extends IComponentType {
    private static final int SLOT_GET_FUNCTION_REFLECTION = 17;

    private static final FunctionDescriptor DESC_GET_FUNCTION_REFLECTION =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    public IEntryPoint(MemorySegment self) {
        super(self);
    }

    public MemorySegment getFunctionReflection() {
        try {
            return (MemorySegment) getHandle(SLOT_GET_FUNCTION_REFLECTION, DESC_GET_FUNCTION_REFLECTION)
                .invokeExact(self);
        } catch (Throwable t) { throw new RuntimeException("getFunctionReflection failed", t); }
    }
}
