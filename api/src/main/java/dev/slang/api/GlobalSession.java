package dev.slang.api;

import dev.slang.bindings.raw.IGlobalSession;
import dev.slang.bindings.raw.ISession;
import java.lang.foreign.*;

public class GlobalSession implements AutoCloseable {
    private final IGlobalSession raw;

    private GlobalSession(IGlobalSession raw) {
        this.raw = raw;
    }

    public static GlobalSession create() {
        return new GlobalSession(IGlobalSession.create());
    }

    public Session createSession(SessionDescBuilder builder) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sessionDesc = builder.build(arena);
            MemorySegment outSession = arena.allocate(ValueLayout.ADDRESS);
            raw.createSessionRaw(sessionDesc, outSession);
            return new Session(new ISession(outSession.get(ValueLayout.ADDRESS, 0)));
        }
    }

    public int findProfile(String name) {
        try (Arena arena = Arena.ofConfined()) {
            return raw.findProfile(arena, name);
        }
    }

    public String getBuildTagString() {
        return raw.getBuildTagString();
    }

    @Override
    public void close() {
        raw.close();
    }
}
