package dev.slang.bindings.enums;

import java.util.Optional;

public enum SourceLanguage {
    UNKNOWN(0), SLANG(1), HLSL(2), GLSL(3), C(4), CPP(5), CUDA(6), SPIRV(7), METAL(8), WGSL(9);

    private final int value;
    SourceLanguage(int value) { this.value = value; }
    public int value() { return value; }

    public static Optional<SourceLanguage> fromValue(int value) {
        for (SourceLanguage l : values()) {
            if (l.value == value) return Optional.of(l);
        }
        return Optional.empty();
    }
}
