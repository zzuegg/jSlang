package dev.slang.api;

public class SlangException extends Exception {
    private final int resultCode;

    public SlangException(int resultCode, String message) {
        super(message + " (SlangResult: 0x" + Integer.toHexString(resultCode) + ")");
        this.resultCode = resultCode;
    }

    public int getResultCode() { return resultCode; }
}
