package dev.slang.bindings;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class COMObject implements AutoCloseable {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final int MAX_VTABLE_SLOTS = 64;

    protected final MemorySegment self;
    private final MethodHandle[] handleCache = new MethodHandle[MAX_VTABLE_SLOTS];
    private boolean closed = false;

    protected static final int SLOT_QUERY_INTERFACE = 0;
    protected static final int SLOT_ADD_REF = 1;
    protected static final int SLOT_RELEASE = 2;

    protected static final FunctionDescriptor DESC_ADD_REF =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
    protected static final FunctionDescriptor DESC_RELEASE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    public COMObject(MemorySegment self) {
        this.self = self.reinterpret(ValueLayout.ADDRESS.byteSize());
    }

    protected MemorySegment vtable() {
        return self.get(ValueLayout.ADDRESS, 0)
                   .reinterpret(256 * ValueLayout.ADDRESS.byteSize());
    }

    protected MemorySegment getVtableSlot(int index) {
        return vtable().get(ValueLayout.ADDRESS, (long) index * ValueLayout.ADDRESS.byteSize());
    }

    protected MethodHandle getHandle(int slot, FunctionDescriptor descriptor) {
        MethodHandle cached = handleCache[slot];
        if (cached != null) return cached;
        MemorySegment fnPtr = getVtableSlot(slot);
        MethodHandle handle = LINKER.downcallHandle(fnPtr, descriptor);
        handleCache[slot] = handle;
        return handle;
    }

    public int addRef() {
        try {
            return (int) getHandle(SLOT_ADD_REF, DESC_ADD_REF).invokeExact(self);
        } catch (Throwable t) {
            throw new RuntimeException("addRef failed", t);
        }
    }

    public int release() {
        try {
            return (int) getHandle(SLOT_RELEASE, DESC_RELEASE).invokeExact(self);
        } catch (Throwable t) {
            throw new RuntimeException("release failed", t);
        }
    }

    public MemorySegment ptr() {
        return self;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            release();
        }
    }
}
