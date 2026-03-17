package dev.slang.bindings.raw;

import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;

public class IModule extends IComponentType {

    private static final int SLOT_FIND_ENTRY_POINT_BY_NAME = 17;
    private static final int SLOT_GET_DEFINED_ENTRY_POINT_COUNT = 18;
    private static final int SLOT_GET_DEFINED_ENTRY_POINT = 19;
    private static final int SLOT_SERIALIZE = 20;
    private static final int SLOT_WRITE_TO_FILE = 21;
    private static final int SLOT_GET_NAME = 22;
    private static final int SLOT_GET_FILE_PATH = 23;
    private static final int SLOT_GET_UNIQUE_IDENTITY = 24;
    private static final int SLOT_FIND_AND_CHECK_ENTRY_POINT = 25;
    private static final int SLOT_GET_DEPENDENCY_FILE_COUNT = 26;
    private static final int SLOT_GET_DEPENDENCY_FILE_PATH = 27;
    private static final int SLOT_GET_MODULE_REFLECTION = 28;
    private static final int SLOT_DISASSEMBLE = 29;

    private static final FunctionDescriptor DESC_FIND_ENTRY_POINT_BY_NAME =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_DEFINED_ENTRY_POINT_COUNT =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_DEFINED_ENTRY_POINT =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_SERIALIZE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_WRITE_TO_FILE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_NAME =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_FILE_PATH =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_UNIQUE_IDENTITY =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_FIND_AND_CHECK_ENTRY_POINT =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_DEPENDENCY_FILE_COUNT =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_DEPENDENCY_FILE_PATH =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_GET_MODULE_REFLECTION =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_DISASSEMBLE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
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

    public int getDefinedEntryPointCount() {
        try {
            return (int) getHandle(SLOT_GET_DEFINED_ENTRY_POINT_COUNT, DESC_GET_DEFINED_ENTRY_POINT_COUNT)
                .invokeExact(self);
        } catch (Throwable t) { throw new RuntimeException("getDefinedEntryPointCount failed", t); }
    }

    public IEntryPoint getDefinedEntryPoint(Arena arena, int index) {
        MemorySegment outEntryPoint = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_GET_DEFINED_ENTRY_POINT, DESC_GET_DEFINED_ENTRY_POINT)
                .invokeExact(self, index, outEntryPoint);
            SlangResult.check(result, "IModule::getDefinedEntryPoint(" + index + ")");
            return new IEntryPoint(outEntryPoint.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getDefinedEntryPoint failed", t); }
    }

    public ISlangBlob serialize(Arena arena) {
        MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_SERIALIZE, DESC_SERIALIZE)
                .invokeExact(self, outBlob);
            SlangResult.check(result, "IModule::serialize");
            return new ISlangBlob(outBlob.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("serialize failed", t); }
    }

    public void writeToFile(Arena arena, String fileName) {
        MemorySegment fileNameStr = arena.allocateUtf8String(fileName);
        try {
            int result = (int) getHandle(SLOT_WRITE_TO_FILE, DESC_WRITE_TO_FILE)
                .invokeExact(self, fileNameStr);
            SlangResult.check(result, "IModule::writeToFile(" + fileName + ")");
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("writeToFile failed", t); }
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

    public String getFilePath() {
        try {
            MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_FILE_PATH, DESC_GET_FILE_PATH)
                .invokeExact(self);
            if (ptr.equals(MemorySegment.NULL)) return null;
            return ptr.reinterpret(1024).getUtf8String(0);
        } catch (Throwable t) { throw new RuntimeException("getFilePath failed", t); }
    }

    public String getUniqueIdentity() {
        try {
            MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_UNIQUE_IDENTITY, DESC_GET_UNIQUE_IDENTITY)
                .invokeExact(self);
            if (ptr.equals(MemorySegment.NULL)) return null;
            return ptr.reinterpret(1024).getUtf8String(0);
        } catch (Throwable t) { throw new RuntimeException("getUniqueIdentity failed", t); }
    }

    public int getDependencyFileCount() {
        try {
            return (int) getHandle(SLOT_GET_DEPENDENCY_FILE_COUNT, DESC_GET_DEPENDENCY_FILE_COUNT)
                .invokeExact(self);
        } catch (Throwable t) { throw new RuntimeException("getDependencyFileCount failed", t); }
    }

    public String getDependencyFilePath(int index) {
        try {
            MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_DEPENDENCY_FILE_PATH, DESC_GET_DEPENDENCY_FILE_PATH)
                .invokeExact(self, index);
            if (ptr.equals(MemorySegment.NULL)) return null;
            return ptr.reinterpret(1024).getUtf8String(0);
        } catch (Throwable t) { throw new RuntimeException("getDependencyFilePath failed", t); }
    }

    public MemorySegment getModuleReflection() {
        try {
            return (MemorySegment) getHandle(SLOT_GET_MODULE_REFLECTION, DESC_GET_MODULE_REFLECTION)
                .invokeExact(self);
        } catch (Throwable t) { throw new RuntimeException("getModuleReflection failed", t); }
    }

    public ISlangBlob disassemble(Arena arena) {
        MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_DISASSEMBLE, DESC_DISASSEMBLE)
                .invokeExact(self, outBlob);
            SlangResult.check(result, "IModule::disassemble");
            return new ISlangBlob(outBlob.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("disassemble failed", t); }
    }
}
