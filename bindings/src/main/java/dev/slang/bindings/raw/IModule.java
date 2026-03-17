package dev.slang.bindings.raw;

import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;

public class IModule extends IComponentType {

    private static final int SLOT_FIND_ENTRY_POINT_BY_NAME = 17;
    private static final int SLOT_GET_NAME = 22;
    private static final int SLOT_FIND_AND_CHECK_ENTRY_POINT = 25;

    private static final FunctionDescriptor DESC_FIND_ENTRY_POINT_BY_NAME =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_NAME =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_FIND_AND_CHECK_ENTRY_POINT =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    public IModule(MemorySegment self) {
        super(self);
    }

    public IEntryPoint findEntryPointByName(Arena arena, String name) {
        MemorySegment nameStr = arena.allocateUtf8String(name);
        MemorySegment outEntryPoint = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_FIND_ENTRY_POINT_BY_NAME, DESC_FIND_ENTRY_POINT_BY_NAME)
                .invokeExact(self, nameStr, outEntryPoint);
            SlangResult.check(result, "IModule::findEntryPointByName(" + name + ")");
            return new IEntryPoint(outEntryPoint.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("findEntryPointByName failed", t); }
    }

    public IEntryPoint findAndCheckEntryPoint(Arena arena, String name, int stage) {
        MemorySegment nameStr = arena.allocateUtf8String(name);
        MemorySegment outEntryPoint = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_FIND_AND_CHECK_ENTRY_POINT, DESC_FIND_AND_CHECK_ENTRY_POINT)
                .invokeExact(self, nameStr, stage, outEntryPoint, outDiag);
            SlangResult.check(result, "IModule::findAndCheckEntryPoint(" + name + ")");
            return new IEntryPoint(outEntryPoint.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("findAndCheckEntryPoint failed", t); }
    }

    public String getName() {
        try {
            MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_NAME, DESC_GET_NAME)
                .invokeExact(self);
            return ptr.reinterpret(256).getUtf8String(0);
        } catch (Throwable t) { throw new RuntimeException("getName failed", t); }
    }
}
