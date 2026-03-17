package dev.slang.bindings.raw;

import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;

public class IComponentType extends ISlangUnknown {

    private static final int SLOT_GET_ENTRY_POINT_CODE = 6;
    private static final int SLOT_LINK = 10;
    private static final int SLOT_GET_TARGET_CODE = 14;

    private static final FunctionDescriptor DESC_GET_ENTRY_POINT_CODE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_LINK =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_TARGET_CODE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    public IComponentType(MemorySegment self) {
        super(self);
    }

    public IComponentType link(Arena arena) {
        MemorySegment outLinked = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_LINK, DESC_LINK)
                .invokeExact(self, outLinked, outDiag);
            SlangResult.check(result, "IComponentType::link");
            return new IComponentType(outLinked.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("link failed", t); }
    }

    public ISlangBlob getEntryPointCode(Arena arena, long entryPointIndex, long targetIndex) {
        MemorySegment outCode = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_GET_ENTRY_POINT_CODE, DESC_GET_ENTRY_POINT_CODE)
                .invokeExact(self, entryPointIndex, targetIndex, outCode, outDiag);
            SlangResult.check(result, "IComponentType::getEntryPointCode");
            return new ISlangBlob(outCode.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getEntryPointCode failed", t); }
    }

    public ISlangBlob getTargetCode(Arena arena, long targetIndex) {
        MemorySegment outCode = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_GET_TARGET_CODE, DESC_GET_TARGET_CODE)
                .invokeExact(self, targetIndex, outCode, outDiag);
            SlangResult.check(result, "IComponentType::getTargetCode");
            return new ISlangBlob(outCode.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getTargetCode failed", t); }
    }
}
