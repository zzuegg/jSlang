package dev.slang.bindings.raw;

import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;

public class IComponentType extends ISlangUnknown {

    private static final int SLOT_GET_SESSION = 3;
    private static final int SLOT_GET_LAYOUT = 4;
    private static final int SLOT_GET_SPECIALIZATION_PARAM_COUNT = 5;
    private static final int SLOT_GET_ENTRY_POINT_CODE = 6;
    private static final int SLOT_GET_RESULT_AS_FILE_SYSTEM = 7;
    private static final int SLOT_GET_ENTRY_POINT_HASH = 8;
    private static final int SLOT_SPECIALIZE = 9;
    private static final int SLOT_LINK = 10;
    private static final int SLOT_RENAME_ENTRY_POINT = 12;
    private static final int SLOT_LINK_WITH_OPTIONS = 13;
    private static final int SLOT_GET_TARGET_CODE = 14;
    private static final int SLOT_GET_TARGET_METADATA = 15;
    private static final int SLOT_GET_ENTRY_POINT_METADATA = 16;

    private static final FunctionDescriptor DESC_GET_SESSION =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_LAYOUT =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_SPECIALIZATION_PARAM_COUNT =
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_ENTRY_POINT_CODE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_RESULT_AS_FILE_SYSTEM =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_ENTRY_POINT_HASH =
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_SPECIALIZE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_LINK =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_RENAME_ENTRY_POINT =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_LINK_WITH_OPTIONS =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_TARGET_CODE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_TARGET_METADATA =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_ENTRY_POINT_METADATA =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    public IComponentType(MemorySegment self) {
        super(self);
    }

    public MemorySegment getSession() {
        try {
            return (MemorySegment) getHandle(SLOT_GET_SESSION, DESC_GET_SESSION)
                .invokeExact(self);
        } catch (Throwable t) { throw new RuntimeException("getSession failed", t); }
    }

    public MemorySegment getLayout(Arena arena, long targetIndex) {
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment layout = (MemorySegment) getHandle(SLOT_GET_LAYOUT, DESC_GET_LAYOUT)
                .invokeExact(self, targetIndex, outDiag);
            if (layout.equals(MemorySegment.NULL)) {
                throw new RuntimeException("getLayout returned null");
            }
            return layout;
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getLayout failed", t); }
    }

    public long getSpecializationParamCount() {
        try {
            return (long) getHandle(SLOT_GET_SPECIALIZATION_PARAM_COUNT, DESC_GET_SPECIALIZATION_PARAM_COUNT)
                .invokeExact(self);
        } catch (Throwable t) { throw new RuntimeException("getSpecializationParamCount failed", t); }
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

    public MemorySegment getResultAsFileSystem(Arena arena, long entryPointIndex, long targetIndex) {
        MemorySegment outFileSystem = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_GET_RESULT_AS_FILE_SYSTEM, DESC_GET_RESULT_AS_FILE_SYSTEM)
                .invokeExact(self, entryPointIndex, targetIndex, outFileSystem);
            SlangResult.check(result, "IComponentType::getResultAsFileSystem");
            return outFileSystem.get(ValueLayout.ADDRESS, 0);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getResultAsFileSystem failed", t); }
    }

    public ISlangBlob getEntryPointHash(Arena arena, long entryPointIndex, long targetIndex) {
        MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
        try {
            getHandle(SLOT_GET_ENTRY_POINT_HASH, DESC_GET_ENTRY_POINT_HASH)
                .invokeExact(self, entryPointIndex, targetIndex, outBlob);
            MemorySegment blobPtr = outBlob.get(ValueLayout.ADDRESS, 0);
            if (blobPtr.equals(MemorySegment.NULL)) {
                return null;
            }
            return new ISlangBlob(blobPtr);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getEntryPointHash failed", t); }
    }

    public IComponentType specialize(Arena arena, MemorySegment specializationArgs, long argCount) {
        MemorySegment outSpecialized = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_SPECIALIZE, DESC_SPECIALIZE)
                .invokeExact(self, specializationArgs, argCount, outSpecialized, outDiag);
            SlangResult.check(result, "IComponentType::specialize");
            return new IComponentType(outSpecialized.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("specialize failed", t); }
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

    public IComponentType renameEntryPoint(Arena arena, String newName) {
        MemorySegment nameStr = arena.allocateUtf8String(newName);
        MemorySegment outRenamed = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_RENAME_ENTRY_POINT, DESC_RENAME_ENTRY_POINT)
                .invokeExact(self, nameStr, outRenamed);
            SlangResult.check(result, "IComponentType::renameEntryPoint");
            return new IComponentType(outRenamed.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("renameEntryPoint failed", t); }
    }

    public IComponentType linkWithOptions(Arena arena, int optionCount, MemorySegment options) {
        MemorySegment outLinked = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_LINK_WITH_OPTIONS, DESC_LINK_WITH_OPTIONS)
                .invokeExact(self, outLinked, optionCount, options, outDiag);
            SlangResult.check(result, "IComponentType::linkWithOptions");
            return new IComponentType(outLinked.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("linkWithOptions failed", t); }
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

    public MemorySegment getTargetMetadata(Arena arena, long targetIndex) {
        MemorySegment outMetadata = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_GET_TARGET_METADATA, DESC_GET_TARGET_METADATA)
                .invokeExact(self, targetIndex, outMetadata, outDiag);
            SlangResult.check(result, "IComponentType::getTargetMetadata");
            return outMetadata.get(ValueLayout.ADDRESS, 0);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getTargetMetadata failed", t); }
    }

    public MemorySegment getEntryPointMetadata(Arena arena, long entryPointIndex, long targetIndex) {
        MemorySegment outMetadata = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_GET_ENTRY_POINT_METADATA, DESC_GET_ENTRY_POINT_METADATA)
                .invokeExact(self, entryPointIndex, targetIndex, outMetadata, outDiag);
            SlangResult.check(result, "IComponentType::getEntryPointMetadata");
            return outMetadata.get(ValueLayout.ADDRESS, 0);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getEntryPointMetadata failed", t); }
    }
}
