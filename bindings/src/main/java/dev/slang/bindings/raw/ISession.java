package dev.slang.bindings.raw;

import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;

public class ISession extends ISlangUnknown {

    private static final int SLOT_GET_GLOBAL_SESSION = 3;
    private static final int SLOT_LOAD_MODULE = 4;
    private static final int SLOT_LOAD_MODULE_FROM_SOURCE = 5;
    private static final int SLOT_CREATE_COMPOSITE_COMPONENT_TYPE = 6;
    private static final int SLOT_SPECIALIZE_TYPE = 7;
    private static final int SLOT_GET_TYPE_LAYOUT = 8;
    private static final int SLOT_GET_CONTAINER_TYPE = 9;
    private static final int SLOT_GET_DYNAMIC_TYPE = 10;
    private static final int SLOT_GET_TYPE_RTTI_MANGLED_NAME = 11;
    private static final int SLOT_GET_TYPE_CONFORMANCE_WITNESS_MANGLED_NAME = 12;
    private static final int SLOT_GET_TYPE_CONFORMANCE_WITNESS_SEQUENTIAL_ID = 13;
    private static final int SLOT_CREATE_COMPILE_REQUEST = 14;
    private static final int SLOT_CREATE_TYPE_CONFORMANCE_COMPONENT_TYPE = 15;
    private static final int SLOT_LOAD_MODULE_FROM_IR_BLOB = 16;
    private static final int SLOT_GET_LOADED_MODULE_COUNT = 17;
    private static final int SLOT_GET_LOADED_MODULE = 18;
    private static final int SLOT_IS_BINARY_MODULE_UP_TO_DATE = 19;
    private static final int SLOT_LOAD_MODULE_FROM_SOURCE_STRING = 20;
    private static final int SLOT_GET_DYNAMIC_OBJECT_RTTI_BYTES = 21;
    private static final int SLOT_LOAD_MODULE_INFO_FROM_IR_BLOB = 22;
    private static final int SLOT_GET_DECL_SOURCE_LOCATION = 23;

    private static final FunctionDescriptor DESC_GET_GLOBAL_SESSION =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_LOAD_MODULE =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_LOAD_MODULE_FROM_SOURCE =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_CREATE_COMPOSITE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_LOADED_MODULE_COUNT =
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_LOADED_MODULE =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

    private static final FunctionDescriptor DESC_IS_BINARY_MODULE_UP_TO_DATE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_LOAD_MODULE_FROM_SOURCE_STRING =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_SPECIALIZE_TYPE =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_TYPE_LAYOUT =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_CONTAINER_TYPE =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_DYNAMIC_TYPE =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_TYPE_RTTI_MANGLED_NAME =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_TYPE_CONFORMANCE_WITNESS_MANGLED_NAME =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_TYPE_CONFORMANCE_WITNESS_SEQUENTIAL_ID =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_CREATE_COMPILE_REQUEST =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_CREATE_TYPE_CONFORMANCE_COMPONENT_TYPE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_LOAD_MODULE_FROM_IR_BLOB =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_DYNAMIC_OBJECT_RTTI_BYTES =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_LOAD_MODULE_INFO_FROM_IR_BLOB =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_DECL_SOURCE_LOCATION =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    public ISession(MemorySegment self) {
        super(self);
    }

    public IModule loadModule(Arena arena, String moduleName) {
        MemorySegment nameStr = arena.allocateUtf8String(moduleName);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment modulePtr = (MemorySegment) getHandle(SLOT_LOAD_MODULE, DESC_LOAD_MODULE)
                .invokeExact(self, nameStr, outDiag);
            if (modulePtr.equals(MemorySegment.NULL)) {
                throw new RuntimeException("loadModule returned null for: " + moduleName);
            }
            return new IModule(modulePtr);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("loadModule failed", t); }
    }

    public IModule loadModuleFromSourceString(Arena arena, String moduleName, String path, String source) {
        MemorySegment nameStr = arena.allocateUtf8String(moduleName);
        MemorySegment pathStr = arena.allocateUtf8String(path);
        MemorySegment sourceStr = arena.allocateUtf8String(source);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment modulePtr = (MemorySegment)
                getHandle(SLOT_LOAD_MODULE_FROM_SOURCE_STRING, DESC_LOAD_MODULE_FROM_SOURCE_STRING)
                    .invokeExact(self, nameStr, pathStr, sourceStr, outDiag);
            if (modulePtr.equals(MemorySegment.NULL)) {
                MemorySegment diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
                String diagMsg = "";
                if (!diagPtr.equals(MemorySegment.NULL)) {
                    ISlangBlob diagBlob = new ISlangBlob(diagPtr);
                    diagMsg = new String(diagBlob.toByteArray());
                    diagBlob.release();
                }
                throw new RuntimeException("loadModuleFromSourceString failed: " + diagMsg);
            }
            return new IModule(modulePtr);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("loadModuleFromSourceString failed", t); }
    }

    public MemorySegment getGlobalSession() {
        try {
            return (MemorySegment) getHandle(SLOT_GET_GLOBAL_SESSION, DESC_GET_GLOBAL_SESSION)
                .invokeExact(self);
        } catch (Throwable t) { throw new RuntimeException("getGlobalSession failed", t); }
    }

    public IModule loadModuleFromSource(Arena arena, String moduleName, String path, ISlangBlob sourceBlob) {
        MemorySegment nameStr = arena.allocateUtf8String(moduleName);
        MemorySegment pathStr = arena.allocateUtf8String(path);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment modulePtr = (MemorySegment)
                getHandle(SLOT_LOAD_MODULE_FROM_SOURCE, DESC_LOAD_MODULE_FROM_SOURCE)
                    .invokeExact(self, nameStr, pathStr, sourceBlob.ptr(), MemorySegment.NULL, outDiag);
            if (modulePtr.equals(MemorySegment.NULL)) {
                MemorySegment diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
                String diagMsg = "";
                if (!diagPtr.equals(MemorySegment.NULL)) {
                    ISlangBlob diagBlob = new ISlangBlob(diagPtr);
                    diagMsg = new String(diagBlob.toByteArray());
                    diagBlob.release();
                }
                throw new RuntimeException("loadModuleFromSource failed: " + diagMsg);
            }
            return new IModule(modulePtr);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("loadModuleFromSource failed", t); }
    }

    public long getLoadedModuleCount() {
        try {
            return (long) getHandle(SLOT_GET_LOADED_MODULE_COUNT, DESC_GET_LOADED_MODULE_COUNT)
                .invokeExact(self);
        } catch (Throwable t) { throw new RuntimeException("getLoadedModuleCount failed", t); }
    }

    public IModule getLoadedModule(long index) {
        try {
            MemorySegment modulePtr = (MemorySegment)
                getHandle(SLOT_GET_LOADED_MODULE, DESC_GET_LOADED_MODULE)
                    .invokeExact(self, index);
            return new IModule(modulePtr);
        } catch (Throwable t) { throw new RuntimeException("getLoadedModule failed", t); }
    }

    public boolean isBinaryModuleUpToDate(Arena arena, String path, ISlangBlob blob) {
        MemorySegment pathStr = arena.allocateUtf8String(path);
        try {
            int result = (int) getHandle(SLOT_IS_BINARY_MODULE_UP_TO_DATE, DESC_IS_BINARY_MODULE_UP_TO_DATE)
                .invokeExact(self, pathStr, blob.ptr());
            return result >= 0;
        } catch (Throwable t) { throw new RuntimeException("isBinaryModuleUpToDate failed", t); }
    }

    public IComponentType createCompositeComponentType(Arena arena, IComponentType... components) {
        MemorySegment array = arena.allocate(ValueLayout.ADDRESS.byteSize() * components.length);
        for (int i = 0; i < components.length; i++) {
            array.set(ValueLayout.ADDRESS, (long) i * ValueLayout.ADDRESS.byteSize(), components[i].ptr());
        }
        MemorySegment outComposite = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_CREATE_COMPOSITE_COMPONENT_TYPE, DESC_CREATE_COMPOSITE)
                .invokeExact(self, array, (long) components.length, outComposite, outDiag);
            SlangResult.check(result, "createCompositeComponentType");
            return new IComponentType(outComposite.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("createCompositeComponentType failed", t); }
    }

    public MemorySegment specializeType(Arena arena, MemorySegment type, MemorySegment specializationArgs, long argCount) {
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment result = (MemorySegment)
                getHandle(SLOT_SPECIALIZE_TYPE, DESC_SPECIALIZE_TYPE)
                    .invokeExact(self, type, specializationArgs, argCount, outDiag);
            return result;
        } catch (Throwable t) { throw new RuntimeException("specializeType failed", t); }
    }

    public MemorySegment getTypeLayout(Arena arena, MemorySegment type, long targetIndex, int rules) {
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment result = (MemorySegment)
                getHandle(SLOT_GET_TYPE_LAYOUT, DESC_GET_TYPE_LAYOUT)
                    .invokeExact(self, type, targetIndex, rules, outDiag);
            return result;
        } catch (Throwable t) { throw new RuntimeException("getTypeLayout failed", t); }
    }

    public MemorySegment getContainerType(Arena arena, MemorySegment type, int containerType) {
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment result = (MemorySegment)
                getHandle(SLOT_GET_CONTAINER_TYPE, DESC_GET_CONTAINER_TYPE)
                    .invokeExact(self, type, containerType, outDiag);
            return result;
        } catch (Throwable t) { throw new RuntimeException("getContainerType failed", t); }
    }

    public MemorySegment getDynamicType() {
        try {
            return (MemorySegment) getHandle(SLOT_GET_DYNAMIC_TYPE, DESC_GET_DYNAMIC_TYPE)
                .invokeExact(self);
        } catch (Throwable t) { throw new RuntimeException("getDynamicType failed", t); }
    }

    public String getTypeRTTIMangledName(Arena arena, MemorySegment type) {
        MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_GET_TYPE_RTTI_MANGLED_NAME, DESC_GET_TYPE_RTTI_MANGLED_NAME)
                .invokeExact(self, type, outBlob);
            SlangResult.check(result, "getTypeRTTIMangledName");
            MemorySegment blobPtr = outBlob.get(ValueLayout.ADDRESS, 0);
            if (blobPtr.equals(MemorySegment.NULL)) return null;
            ISlangBlob blob = new ISlangBlob(blobPtr);
            try {
                return new String(blob.toByteArray());
            } finally { blob.release(); }
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getTypeRTTIMangledName failed", t); }
    }

    public String getTypeConformanceWitnessMangledName(Arena arena, MemorySegment type, MemorySegment interfaceType) {
        MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int)
                getHandle(SLOT_GET_TYPE_CONFORMANCE_WITNESS_MANGLED_NAME, DESC_GET_TYPE_CONFORMANCE_WITNESS_MANGLED_NAME)
                    .invokeExact(self, type, interfaceType, outBlob);
            SlangResult.check(result, "getTypeConformanceWitnessMangledName");
            MemorySegment blobPtr = outBlob.get(ValueLayout.ADDRESS, 0);
            if (blobPtr.equals(MemorySegment.NULL)) return null;
            ISlangBlob blob = new ISlangBlob(blobPtr);
            try {
                return new String(blob.toByteArray());
            } finally { blob.release(); }
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getTypeConformanceWitnessMangledName failed", t); }
    }

    public int getTypeConformanceWitnessSequentialID(Arena arena, MemorySegment type, MemorySegment interfaceType) {
        MemorySegment outId = arena.allocate(ValueLayout.JAVA_INT);
        try {
            int result = (int)
                getHandle(SLOT_GET_TYPE_CONFORMANCE_WITNESS_SEQUENTIAL_ID, DESC_GET_TYPE_CONFORMANCE_WITNESS_SEQUENTIAL_ID)
                    .invokeExact(self, type, interfaceType, outId);
            SlangResult.check(result, "getTypeConformanceWitnessSequentialID");
            return outId.get(ValueLayout.JAVA_INT, 0);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getTypeConformanceWitnessSequentialID failed", t); }
    }

    public MemorySegment createCompileRequest(Arena arena) {
        MemorySegment outRequest = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_CREATE_COMPILE_REQUEST, DESC_CREATE_COMPILE_REQUEST)
                .invokeExact(self, outRequest);
            SlangResult.check(result, "createCompileRequest");
            return outRequest.get(ValueLayout.ADDRESS, 0);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("createCompileRequest failed", t); }
    }

    public IComponentType createTypeConformanceComponentType(Arena arena, MemorySegment type,
            MemorySegment interfaceType, long conformanceIdOverride) {
        MemorySegment outComponent = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int)
                getHandle(SLOT_CREATE_TYPE_CONFORMANCE_COMPONENT_TYPE, DESC_CREATE_TYPE_CONFORMANCE_COMPONENT_TYPE)
                    .invokeExact(self, type, interfaceType, outComponent, conformanceIdOverride, outDiag);
            SlangResult.check(result, "createTypeConformanceComponentType");
            return new IComponentType(outComponent.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("createTypeConformanceComponentType failed", t); }
    }

    public IModule loadModuleFromIRBlob(Arena arena, String name, String path, ISlangBlob source) {
        MemorySegment nameStr = arena.allocateUtf8String(name);
        MemorySegment pathStr = arena.allocateUtf8String(path);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment modulePtr = (MemorySegment)
                getHandle(SLOT_LOAD_MODULE_FROM_IR_BLOB, DESC_LOAD_MODULE_FROM_IR_BLOB)
                    .invokeExact(self, nameStr, pathStr, source.ptr(), outDiag);
            if (modulePtr.equals(MemorySegment.NULL)) {
                MemorySegment diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
                String diagMsg = "";
                if (!diagPtr.equals(MemorySegment.NULL)) {
                    ISlangBlob diagBlob = new ISlangBlob(diagPtr);
                    diagMsg = new String(diagBlob.toByteArray());
                    diagBlob.release();
                }
                throw new RuntimeException("loadModuleFromIRBlob failed: " + diagMsg);
            }
            return new IModule(modulePtr);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("loadModuleFromIRBlob failed", t); }
    }

    public int getDynamicObjectRTTIBytes(Arena arena, MemorySegment type, MemorySegment interfaceType, int outSize) {
        try {
            return (int) getHandle(SLOT_GET_DYNAMIC_OBJECT_RTTI_BYTES, DESC_GET_DYNAMIC_OBJECT_RTTI_BYTES)
                .invokeExact(self, type, interfaceType, MemorySegment.NULL, outSize);
        } catch (Throwable t) { throw new RuntimeException("getDynamicObjectRTTIBytes failed", t); }
    }

    public void loadModuleInfoFromIRBlob(Arena arena, String moduleName, ISlangBlob source) {
        MemorySegment nameStr = arena.allocateUtf8String(moduleName);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int)
                getHandle(SLOT_LOAD_MODULE_INFO_FROM_IR_BLOB, DESC_LOAD_MODULE_INFO_FROM_IR_BLOB)
                    .invokeExact(self, nameStr, source.ptr(), outDiag);
            SlangResult.check(result, "loadModuleInfoFromIRBlob");
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("loadModuleInfoFromIRBlob failed", t); }
    }

    public void getDeclSourceLocation(Arena arena, MemorySegment decl, MemorySegment outLocation) {
        try {
            int result = (int)
                getHandle(SLOT_GET_DECL_SOURCE_LOCATION, DESC_GET_DECL_SOURCE_LOCATION)
                    .invokeExact(self, decl, outLocation);
            SlangResult.check(result, "getDeclSourceLocation");
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("getDeclSourceLocation failed", t); }
    }
}
