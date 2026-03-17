package dev.slang.bindings.raw;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class ISlangBlob extends ISlangUnknown {

    private static final int SLOT_GET_BUFFER_POINTER = 3;
    private static final int SLOT_GET_BUFFER_SIZE = 4;

    private static final FunctionDescriptor DESC_GET_BUFFER_POINTER =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    private static final FunctionDescriptor DESC_GET_BUFFER_SIZE =
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

    public ISlangBlob(MemorySegment self) {
        super(self);
    }

    public MemorySegment getBufferPointer() {
        try {
            return (MemorySegment) getHandle(SLOT_GET_BUFFER_POINTER, DESC_GET_BUFFER_POINTER)
                .invokeExact(self);
        } catch (Throwable t) {
            throw new RuntimeException("getBufferPointer failed", t);
        }
    }

    public long getBufferSize() {
        try {
            return (long) getHandle(SLOT_GET_BUFFER_SIZE, DESC_GET_BUFFER_SIZE)
                .invokeExact(self);
        } catch (Throwable t) {
            throw new RuntimeException("getBufferSize failed", t);
        }
    }

    public byte[] toByteArray() {
        long size = getBufferSize();
        MemorySegment ptr = getBufferPointer().reinterpret(size);
        return ptr.toArray(ValueLayout.JAVA_BYTE);
    }
}
