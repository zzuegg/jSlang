package dev.slang.bindings.enums;

import java.util.Optional;

public enum CompileTarget {
    UNKNOWN(0), NONE(1), GLSL(2), HLSL(5), SPIRV(6), SPIRV_ASM(7),
    DXBC(8), DXBC_ASM(9), DXIL(10), DXIL_ASM(11),
    C_SOURCE(12), CPP_SOURCE(13), HOST_EXECUTABLE(14),
    SHADER_SHARED_LIBRARY(15), SHADER_HOST_CALLABLE(16),
    CUDA_SOURCE(17), PTX(18), CUDA_OBJECT_CODE(19), OBJECT_CODE(20),
    HOST_CPP_SOURCE(21), HOST_HOST_CALLABLE(22), CPP_PYTORCH_BINDING(23),
    METAL(24), METAL_LIB(25), METAL_LIB_ASM(26),
    HOST_SHARED_LIBRARY(27), WGSL(28), WGSL_SPIRV_ASM(29), WGSL_SPIRV(30),
    HOST_VM(31);

    private final int value;
    CompileTarget(int value) { this.value = value; }
    public int value() { return value; }

    public static Optional<CompileTarget> fromValue(int value) {
        for (CompileTarget t : values()) {
            if (t.value == value) return Optional.of(t);
        }
        return Optional.empty();
    }
}
