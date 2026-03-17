package dev.slang.bindings.enums;

import java.util.Optional;

public enum Stage {
    NONE(0), VERTEX(1), HULL(2), DOMAIN(3), GEOMETRY(4), FRAGMENT(5), COMPUTE(6),
    RAY_GENERATION(7), INTERSECTION(8), ANY_HIT(9), CLOSEST_HIT(10), MISS(11),
    CALLABLE(12), MESH(13), AMPLIFICATION(14);

    private final int value;
    Stage(int value) { this.value = value; }
    public int value() { return value; }

    public static Optional<Stage> fromValue(int value) {
        for (Stage s : values()) {
            if (s.value == value) return Optional.of(s);
        }
        return Optional.empty();
    }
}
