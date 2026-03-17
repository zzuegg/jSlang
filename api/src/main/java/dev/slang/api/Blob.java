package dev.slang.api;

import dev.slang.bindings.raw.ISlangBlob;

public class Blob implements AutoCloseable {
    private final ISlangBlob raw;

    public Blob(ISlangBlob raw) {
        this.raw = raw;
    }

    public byte[] toByteArray() {
        return raw.toByteArray();
    }

    public long size() {
        return raw.getBufferSize();
    }

    @Override
    public void close() {
        raw.close();
    }
}
