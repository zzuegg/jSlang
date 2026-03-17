package dev.slang.bindings.raw;

import dev.slang.bindings.NativeLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public final class SlangReflection {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = NativeLoader.load();

    private SlangReflection() {}

    private static MethodHandle downcall(String name, FunctionDescriptor desc) {
        MemorySegment fn = LOOKUP.find(name).orElseThrow(
            () -> new RuntimeException(name + " not found in Slang library"));
        return LINKER.downcallHandle(fn, desc);
    }

    // --- ProgramLayout (spReflection_*) ---

    private static final MethodHandle GET_PARAMETER_COUNT = downcall(
        "spReflection_GetParameterCount",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle GET_PARAMETER_BY_INDEX = downcall(
        "spReflection_GetParameterByIndex",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle GET_ENTRY_POINT_COUNT = downcall(
        "spReflection_getEntryPointCount",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle GET_ENTRY_POINT_BY_INDEX = downcall(
        "spReflection_getEntryPointByIndex",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    public static int getParameterCount(MemorySegment programLayout) {
        try {
            return (int) GET_PARAMETER_COUNT.invokeExact(programLayout);
        } catch (Throwable t) { throw new RuntimeException("getParameterCount failed", t); }
    }

    public static MemorySegment getParameterByIndex(MemorySegment programLayout, int index) {
        try {
            return (MemorySegment) GET_PARAMETER_BY_INDEX.invokeExact(programLayout, index);
        } catch (Throwable t) { throw new RuntimeException("getParameterByIndex failed", t); }
    }

    public static int getEntryPointCount(MemorySegment programLayout) {
        try {
            return (int) GET_ENTRY_POINT_COUNT.invokeExact(programLayout);
        } catch (Throwable t) { throw new RuntimeException("getEntryPointCount failed", t); }
    }

    public static MemorySegment getEntryPointByIndex(MemorySegment programLayout, int index) {
        try {
            return (MemorySegment) GET_ENTRY_POINT_BY_INDEX.invokeExact(programLayout, index);
        } catch (Throwable t) { throw new RuntimeException("getEntryPointByIndex failed", t); }
    }

    // --- VariableLayout (spReflectionVariableLayout_*) ---

    private static final MethodHandle GET_VARIABLE = downcall(
        "spReflectionVariableLayout_GetVariable",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle GET_TYPE_LAYOUT = downcall(
        "spReflectionVariableLayout_GetTypeLayout",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle GET_BINDING_SPACE = downcall(
        "spReflectionVariableLayout_GetSpace",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    public static MemorySegment getVariable(MemorySegment varLayout) {
        try {
            return (MemorySegment) GET_VARIABLE.invokeExact(varLayout);
        } catch (Throwable t) { throw new RuntimeException("getVariable failed", t); }
    }

    public static MemorySegment getTypeLayout(MemorySegment varLayout) {
        try {
            return (MemorySegment) GET_TYPE_LAYOUT.invokeExact(varLayout);
        } catch (Throwable t) { throw new RuntimeException("getTypeLayout failed", t); }
    }

    // --- Variable (spReflectionVariable_*) ---

    private static final MethodHandle VARIABLE_GET_NAME = downcall(
        "spReflectionVariable_GetName",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle VARIABLE_GET_TYPE = downcall(
        "spReflectionVariable_GetType",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static String getVariableName(MemorySegment variable) {
        try {
            MemorySegment namePtr = (MemorySegment) VARIABLE_GET_NAME.invokeExact(variable);
            if (namePtr.equals(MemorySegment.NULL)) return null;
            return namePtr.reinterpret(256).getUtf8String(0);
        } catch (Throwable t) { throw new RuntimeException("getVariableName failed", t); }
    }

    public static MemorySegment getVariableType(MemorySegment variable) {
        try {
            return (MemorySegment) VARIABLE_GET_TYPE.invokeExact(variable);
        } catch (Throwable t) { throw new RuntimeException("getVariableType failed", t); }
    }

    // --- Type (spReflectionType_*) ---

    private static final MethodHandle TYPE_GET_KIND = downcall(
        "spReflectionType_GetKind",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_GET_NAME = downcall(
        "spReflectionType_GetName",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_GET_FIELD_COUNT = downcall(
        "spReflectionType_GetFieldCount",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_GET_FIELD_BY_INDEX = downcall(
        "spReflectionType_GetFieldByIndex",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle TYPE_GET_RESOURCE_SHAPE = downcall(
        "spReflectionType_GetResourceShape",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_GET_ELEMENT_TYPE = downcall(
        "spReflectionType_GetElementType",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static int getTypeKind(MemorySegment type) {
        try {
            return (int) TYPE_GET_KIND.invokeExact(type);
        } catch (Throwable t) { throw new RuntimeException("getTypeKind failed", t); }
    }

    public static String getTypeName(MemorySegment type) {
        try {
            MemorySegment namePtr = (MemorySegment) TYPE_GET_NAME.invokeExact(type);
            if (namePtr.equals(MemorySegment.NULL)) return null;
            return namePtr.reinterpret(256).getUtf8String(0);
        } catch (Throwable t) { throw new RuntimeException("getTypeName failed", t); }
    }

    public static int getTypeFieldCount(MemorySegment type) {
        try {
            return (int) TYPE_GET_FIELD_COUNT.invokeExact(type);
        } catch (Throwable t) { throw new RuntimeException("getTypeFieldCount failed", t); }
    }

    public static MemorySegment getTypeFieldByIndex(MemorySegment type, int index) {
        try {
            return (MemorySegment) TYPE_GET_FIELD_BY_INDEX.invokeExact(type, index);
        } catch (Throwable t) { throw new RuntimeException("getTypeFieldByIndex failed", t); }
    }

    public static int getResourceShape(MemorySegment type) {
        try {
            return (int) TYPE_GET_RESOURCE_SHAPE.invokeExact(type);
        } catch (Throwable t) { throw new RuntimeException("getResourceShape failed", t); }
    }

    public static MemorySegment getElementType(MemorySegment type) {
        try {
            return (MemorySegment) TYPE_GET_ELEMENT_TYPE.invokeExact(type);
        } catch (Throwable t) { throw new RuntimeException("getElementType failed", t); }
    }

    // --- EntryPoint reflection (spReflectionEntryPoint_*) ---

    private static final MethodHandle ENTRY_POINT_GET_NAME = downcall(
        "spReflectionEntryPoint_getName",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle ENTRY_POINT_GET_STAGE = downcall(
        "spReflectionEntryPoint_getStage",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle ENTRY_POINT_GET_PARAMETER_COUNT = downcall(
        "spReflectionEntryPoint_getParameterCount",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle ENTRY_POINT_GET_PARAMETER_BY_INDEX = downcall(
        "spReflectionEntryPoint_getParameterByIndex",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    // SlangUInt = size_t (64-bit)
    private static final MethodHandle ENTRY_POINT_GET_THREAD_GROUP_SIZE = downcall(
        "spReflectionEntryPoint_getComputeThreadGroupSize",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS)); // SlangUInt* outSize

    public static String getEntryPointName(MemorySegment entryPoint) {
        try {
            MemorySegment namePtr = (MemorySegment) ENTRY_POINT_GET_NAME.invokeExact(entryPoint);
            if (namePtr.equals(MemorySegment.NULL)) return null;
            return namePtr.reinterpret(256).getUtf8String(0);
        } catch (Throwable t) { throw new RuntimeException("getEntryPointName failed", t); }
    }

    public static int getEntryPointStage(MemorySegment entryPoint) {
        try {
            return (int) ENTRY_POINT_GET_STAGE.invokeExact(entryPoint);
        } catch (Throwable t) { throw new RuntimeException("getEntryPointStage failed", t); }
    }

    public static int getEntryPointParameterCount(MemorySegment entryPoint) {
        try {
            return (int) ENTRY_POINT_GET_PARAMETER_COUNT.invokeExact(entryPoint);
        } catch (Throwable t) { throw new RuntimeException("getEntryPointParameterCount failed", t); }
    }

    public static MemorySegment getEntryPointParameterByIndex(MemorySegment entryPoint, int index) {
        try {
            return (MemorySegment) ENTRY_POINT_GET_PARAMETER_BY_INDEX.invokeExact(entryPoint, index);
        } catch (Throwable t) { throw new RuntimeException("getEntryPointParameterByIndex failed", t); }
    }

    public static long[] getComputeThreadGroupSize(Arena arena, MemorySegment entryPoint) {
        MemorySegment sizeOut = arena.allocate(ValueLayout.JAVA_LONG.byteSize() * 3);
        try {
            ENTRY_POINT_GET_THREAD_GROUP_SIZE.invokeExact(entryPoint, 3L, sizeOut);
            return new long[] {
                sizeOut.get(ValueLayout.JAVA_LONG, 0),
                sizeOut.get(ValueLayout.JAVA_LONG, 8),
                sizeOut.get(ValueLayout.JAVA_LONG, 16)
            };
        } catch (Throwable t) { throw new RuntimeException("getComputeThreadGroupSize failed", t); }
    }

    // --- Type kind constants ---
    public static final int TYPE_KIND_NONE = 0;
    public static final int TYPE_KIND_STRUCT = 1;
    public static final int TYPE_KIND_ARRAY = 2;
    public static final int TYPE_KIND_MATRIX = 3;
    public static final int TYPE_KIND_VECTOR = 4;
    public static final int TYPE_KIND_SCALAR = 5;
    public static final int TYPE_KIND_CONSTANT_BUFFER = 6;
    public static final int TYPE_KIND_RESOURCE = 7;
    public static final int TYPE_KIND_SAMPLER_STATE = 8;
    public static final int TYPE_KIND_TEXTURE_BUFFER = 9;
    public static final int TYPE_KIND_SHADER_STORAGE_BUFFER = 10;
    public static final int TYPE_KIND_PARAMETER_BLOCK = 11;
    public static final int TYPE_KIND_GENERIC_TYPE_PARAMETER = 12;
    public static final int TYPE_KIND_INTERFACE = 13;
}
