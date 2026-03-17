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

    private static final MethodHandle FIND_TYPE_BY_NAME = downcall(
        "spReflection_FindTypeByName",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    public static MemorySegment findTypeByName(MemorySegment programLayout, Arena arena, String name) {
        MemorySegment nameStr = arena.allocateUtf8String(name);
        try {
            return (MemorySegment) FIND_TYPE_BY_NAME.invokeExact(programLayout, nameStr);
        } catch (Throwable t) { throw new RuntimeException("findTypeByName failed", t); }
    }

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

    // --- VariableLayout binding info (spReflectionVariableLayout_*) ---

    private static final MethodHandle VAR_LAYOUT_GET_OFFSET = downcall(
        "spReflectionVariableLayout_GetOffset",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, // varLayout
            ValueLayout.JAVA_INT // category (SlangParameterCategory)
        ));

    private static final MethodHandle VAR_LAYOUT_GET_SPACE = downcall(
        "spReflectionVariableLayout_GetSpace",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, // varLayout
            ValueLayout.JAVA_INT // category
        ));

    private static final MethodHandle VAR_LAYOUT_GET_SEMANTIC_NAME = downcall(
        "spReflectionVariableLayout_GetSemanticName",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle VAR_LAYOUT_GET_SEMANTIC_INDEX = downcall(
        "spReflectionVariableLayout_GetSemanticIndex",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    public static long getOffset(MemorySegment varLayout, int category) {
        try {
            return (long) VAR_LAYOUT_GET_OFFSET.invokeExact(varLayout, category);
        } catch (Throwable t) { throw new RuntimeException("getOffset failed", t); }
    }

    public static long getBindingSpace(MemorySegment varLayout, int category) {
        try {
            return (long) VAR_LAYOUT_GET_SPACE.invokeExact(varLayout, category);
        } catch (Throwable t) { throw new RuntimeException("getBindingSpace failed", t); }
    }

    public static String getSemanticName(MemorySegment varLayout) {
        try {
            MemorySegment ptr = (MemorySegment) VAR_LAYOUT_GET_SEMANTIC_NAME.invokeExact(varLayout);
            if (ptr.equals(MemorySegment.NULL)) return null;
            return ptr.reinterpret(256).getUtf8String(0);
        } catch (Throwable t) { throw new RuntimeException("getSemanticName failed", t); }
    }

    public static int getSemanticIndex(MemorySegment varLayout) {
        try {
            return (int) VAR_LAYOUT_GET_SEMANTIC_INDEX.invokeExact(varLayout);
        } catch (Throwable t) { throw new RuntimeException("getSemanticIndex failed", t); }
    }

    // --- TypeLayout (spReflectionTypeLayout_*) ---

    private static final MethodHandle TYPE_LAYOUT_GET_SIZE = downcall(
        "spReflectionTypeLayout_GetSize",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, // typeLayout
            ValueLayout.JAVA_INT // category
        ));

    private static final MethodHandle TYPE_LAYOUT_GET_STRIDE = downcall(
        "spReflectionTypeLayout_GetStride",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, // typeLayout
            ValueLayout.JAVA_INT // category
        ));

    private static final MethodHandle TYPE_LAYOUT_GET_ALIGNMENT = downcall(
        "spReflectionTypeLayout_getAlignment",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // typeLayout
            ValueLayout.JAVA_INT // category
        ));

    private static final MethodHandle TYPE_LAYOUT_GET_FIELD_COUNT = downcall(
        "spReflectionTypeLayout_GetFieldCount",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_LAYOUT_GET_FIELD_BY_INDEX = downcall(
        "spReflectionTypeLayout_GetFieldByIndex",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle TYPE_LAYOUT_GET_TYPE = downcall(
        "spReflectionTypeLayout_GetType",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_LAYOUT_GET_KIND = downcall(
        "spReflectionTypeLayout_getKind",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_LAYOUT_GET_ELEMENT_TYPE_LAYOUT = downcall(
        "spReflectionTypeLayout_GetElementTypeLayout",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_LAYOUT_GET_BINDING_RANGE_COUNT = downcall(
        "spReflectionTypeLayout_getBindingRangeCount",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_LAYOUT_GET_BINDING_RANGE_TYPE = downcall(
        "spReflectionTypeLayout_getBindingRangeType",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle TYPE_LAYOUT_GET_BINDING_RANGE_BINDING_COUNT = downcall(
        "spReflectionTypeLayout_getBindingRangeBindingCount",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle TYPE_LAYOUT_GET_DESCRIPTOR_SET_COUNT = downcall(
        "spReflectionTypeLayout_getDescriptorSetCount",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static final MethodHandle TYPE_LAYOUT_GET_DESCRIPTOR_SET_SPACE_OFFSET = downcall(
        "spReflectionTypeLayout_getDescriptorSetSpaceOffset",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle TYPE_LAYOUT_GET_DESCRIPTOR_SET_RANGE_COUNT = downcall(
        "spReflectionTypeLayout_getDescriptorSetDescriptorRangeCount",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private static final MethodHandle TYPE_LAYOUT_GET_DESCRIPTOR_SET_RANGE_TYPE = downcall(
        "spReflectionTypeLayout_getDescriptorSetDescriptorRangeType",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    private static final MethodHandle TYPE_LAYOUT_GET_DESCRIPTOR_SET_RANGE_INDEX_OFFSET = downcall(
        "spReflectionTypeLayout_getDescriptorSetDescriptorRangeIndexOffset",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    public static long getTypeLayoutSize(MemorySegment typeLayout, int category) {
        try {
            return (long) TYPE_LAYOUT_GET_SIZE.invokeExact(typeLayout, category);
        } catch (Throwable t) { throw new RuntimeException("getTypeLayoutSize failed", t); }
    }

    public static long getTypeLayoutStride(MemorySegment typeLayout, int category) {
        try {
            return (long) TYPE_LAYOUT_GET_STRIDE.invokeExact(typeLayout, category);
        } catch (Throwable t) { throw new RuntimeException("getTypeLayoutStride failed", t); }
    }

    public static int getTypeLayoutAlignment(MemorySegment typeLayout, int category) {
        try {
            return (int) TYPE_LAYOUT_GET_ALIGNMENT.invokeExact(typeLayout, category);
        } catch (Throwable t) { throw new RuntimeException("getTypeLayoutAlignment failed", t); }
    }

    public static int getTypeLayoutFieldCount(MemorySegment typeLayout) {
        try {
            return (int) TYPE_LAYOUT_GET_FIELD_COUNT.invokeExact(typeLayout);
        } catch (Throwable t) { throw new RuntimeException("getTypeLayoutFieldCount failed", t); }
    }

    public static MemorySegment getTypeLayoutFieldByIndex(MemorySegment typeLayout, int index) {
        try {
            return (MemorySegment) TYPE_LAYOUT_GET_FIELD_BY_INDEX.invokeExact(typeLayout, index);
        } catch (Throwable t) { throw new RuntimeException("getTypeLayoutFieldByIndex failed", t); }
    }

    public static MemorySegment getTypeLayoutType(MemorySegment typeLayout) {
        try {
            return (MemorySegment) TYPE_LAYOUT_GET_TYPE.invokeExact(typeLayout);
        } catch (Throwable t) { throw new RuntimeException("getTypeLayoutType failed", t); }
    }

    public static int getTypeLayoutKind(MemorySegment typeLayout) {
        try {
            return (int) TYPE_LAYOUT_GET_KIND.invokeExact(typeLayout);
        } catch (Throwable t) { throw new RuntimeException("getTypeLayoutKind failed", t); }
    }

    public static MemorySegment getElementTypeLayout(MemorySegment typeLayout) {
        try {
            return (MemorySegment) TYPE_LAYOUT_GET_ELEMENT_TYPE_LAYOUT.invokeExact(typeLayout);
        } catch (Throwable t) { throw new RuntimeException("getElementTypeLayout failed", t); }
    }

    public static int getBindingRangeCount(MemorySegment typeLayout) {
        try {
            return (int) TYPE_LAYOUT_GET_BINDING_RANGE_COUNT.invokeExact(typeLayout);
        } catch (Throwable t) { throw new RuntimeException("getBindingRangeCount failed", t); }
    }

    public static int getBindingRangeType(MemorySegment typeLayout, int index) {
        try {
            return (int) TYPE_LAYOUT_GET_BINDING_RANGE_TYPE.invokeExact(typeLayout, index);
        } catch (Throwable t) { throw new RuntimeException("getBindingRangeType failed", t); }
    }

    public static int getBindingRangeBindingCount(MemorySegment typeLayout, int index) {
        try {
            return (int) TYPE_LAYOUT_GET_BINDING_RANGE_BINDING_COUNT.invokeExact(typeLayout, index);
        } catch (Throwable t) { throw new RuntimeException("getBindingRangeBindingCount failed", t); }
    }

    public static int getDescriptorSetCount(MemorySegment typeLayout) {
        try {
            return (int) TYPE_LAYOUT_GET_DESCRIPTOR_SET_COUNT.invokeExact(typeLayout);
        } catch (Throwable t) { throw new RuntimeException("getDescriptorSetCount failed", t); }
    }

    public static int getDescriptorSetSpaceOffset(MemorySegment typeLayout, int setIndex) {
        try {
            return (int) TYPE_LAYOUT_GET_DESCRIPTOR_SET_SPACE_OFFSET.invokeExact(typeLayout, setIndex);
        } catch (Throwable t) { throw new RuntimeException("getDescriptorSetSpaceOffset failed", t); }
    }

    public static int getDescriptorSetDescriptorRangeCount(MemorySegment typeLayout, int setIndex) {
        try {
            return (int) TYPE_LAYOUT_GET_DESCRIPTOR_SET_RANGE_COUNT.invokeExact(typeLayout, setIndex);
        } catch (Throwable t) { throw new RuntimeException("getDescriptorSetDescriptorRangeCount failed", t); }
    }

    public static int getDescriptorSetDescriptorRangeType(MemorySegment typeLayout, int setIndex, int rangeIndex) {
        try {
            return (int) TYPE_LAYOUT_GET_DESCRIPTOR_SET_RANGE_TYPE.invokeExact(typeLayout, setIndex, rangeIndex);
        } catch (Throwable t) { throw new RuntimeException("getDescriptorSetDescriptorRangeType failed", t); }
    }

    public static int getDescriptorSetDescriptorRangeIndexOffset(MemorySegment typeLayout, int setIndex, int rangeIndex) {
        try {
            return (int) TYPE_LAYOUT_GET_DESCRIPTOR_SET_RANGE_INDEX_OFFSET.invokeExact(typeLayout, setIndex, rangeIndex);
        } catch (Throwable t) { throw new RuntimeException("getDescriptorSetDescriptorRangeIndexOffset failed", t); }
    }

    // --- Parameter category constants (for offset/space/size queries) ---
    public static final int PARAMETER_CATEGORY_NONE = 0;
    public static final int PARAMETER_CATEGORY_MIXED = 1;
    public static final int PARAMETER_CATEGORY_CONSTANT_BUFFER = 2;
    public static final int PARAMETER_CATEGORY_SHADER_RESOURCE = 3;
    public static final int PARAMETER_CATEGORY_UNORDERED_ACCESS = 4;
    public static final int PARAMETER_CATEGORY_VARYING_INPUT = 5;
    public static final int PARAMETER_CATEGORY_VARYING_OUTPUT = 6;
    public static final int PARAMETER_CATEGORY_SAMPLER_STATE = 7;
    public static final int PARAMETER_CATEGORY_UNIFORM = 8;
    public static final int PARAMETER_CATEGORY_DESCRIPTOR_TABLE_SLOT = 9;
    public static final int PARAMETER_CATEGORY_SPECIALIZATION_CONSTANT = 10;
    public static final int PARAMETER_CATEGORY_PUSH_CONSTANT_BUFFER = 11;
    public static final int PARAMETER_CATEGORY_REGISTER_SPACE = 12;
    public static final int PARAMETER_CATEGORY_GENERIC = 13;
    public static final int PARAMETER_CATEGORY_SUB_ELEMENT_REGISTER_SPACE = 14;

    // --- Binding range type constants ---
    public static final int BINDING_TYPE_UNKNOWN = 0;
    public static final int BINDING_TYPE_SAMPLER = 1;
    public static final int BINDING_TYPE_TEXTURE = 2;
    public static final int BINDING_TYPE_CONSTANT_BUFFER = 3;
    public static final int BINDING_TYPE_PARAMETER_BLOCK = 4;
    public static final int BINDING_TYPE_TYPED_BUFFER = 5;
    public static final int BINDING_TYPE_RAW_BUFFER = 6;
    public static final int BINDING_TYPE_COMBINED_TEXTURE_SAMPLER = 7;
    public static final int BINDING_TYPE_INPUT_RENDER_TARGET = 8;
    public static final int BINDING_TYPE_INLINE_UNIFORM_DATA = 9;
    public static final int BINDING_TYPE_RAY_TRACING_ACCELERATION_STRUCTURE = 10;
    public static final int BINDING_TYPE_VARYING_INPUT = 11;
    public static final int BINDING_TYPE_VARYING_OUTPUT = 12;
    public static final int BINDING_TYPE_EXISTING_TEXTURE_UAV = 13;
    public static final int BINDING_TYPE_EXISTING_TEXTURE_SRV = 14;
    public static final int BINDING_TYPE_MUTABLE_FLAG = 0x100;
    public static final int BINDING_TYPE_MUTABLE_TYPED_BUFFER = BINDING_TYPE_TYPED_BUFFER | BINDING_TYPE_MUTABLE_FLAG;
    public static final int BINDING_TYPE_MUTABLE_RAW_BUFFER = BINDING_TYPE_RAW_BUFFER | BINDING_TYPE_MUTABLE_FLAG;
    public static final int BINDING_TYPE_MUTABLE_TEXTURE = BINDING_TYPE_TEXTURE | BINDING_TYPE_MUTABLE_FLAG;

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
