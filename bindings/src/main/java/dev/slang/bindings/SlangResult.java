package dev.slang.bindings;

public final class SlangResult {

    private SlangResult() {}

    public static final int SLANG_OK = 0;
    public static final int SLANG_FAIL = Integer.MIN_VALUE;

    public static boolean isOk(int result) {
        return result >= 0;
    }

    public static boolean isFail(int result) {
        return result < 0;
    }

    public static void check(int result) {
        if (isFail(result)) {
            throw new RuntimeException("Slang call failed with result: 0x" + Integer.toHexString(result));
        }
    }

    public static void check(int result, String context) {
        if (isFail(result)) {
            throw new RuntimeException(context + " — Slang result: 0x" + Integer.toHexString(result));
        }
    }
}
