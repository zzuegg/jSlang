package dev.slang.bindings.raw;

import dev.slang.bindings.NativeLoader;
import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class IGlobalSession extends ISlangUnknown {

    private static final int SLOT_CREATE_SESSION = 3;
    private static final int SLOT_FIND_PROFILE = 4;
    private static final int SLOT_GET_BUILD_TAG_STRING = 8;

    private static final FunctionDescriptor DESC_CREATE_SESSION =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_FIND_PROFILE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_BUILD_TAG_STRING =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    public IGlobalSession(MemorySegment self) {
        super(self);
    }

    public static IGlobalSession create() {
        SymbolLookup lookup = NativeLoader.load();
        MemorySegment createFn = lookup.find("slang_createGlobalSession2").orElseThrow(
            () -> new RuntimeException("slang_createGlobalSession2 not found"));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment desc = arena.allocate(64);
            MemorySegment outSession = arena.allocate(ValueLayout.ADDRESS);

            MethodHandle create = Linker.nativeLinker().downcallHandle(
                createFn,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            int result = (int) create.invokeExact(desc, outSession);
            SlangResult.check(result, "slang_createGlobalSession2");

            MemorySegment ptr = outSession.get(ValueLayout.ADDRESS, 0);
            return new IGlobalSession(ptr);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create global session", t);
        }
    }

    public MemorySegment createSessionRaw(MemorySegment sessionDesc, MemorySegment outSession) {
        try {
            int result = (int) getHandle(SLOT_CREATE_SESSION, DESC_CREATE_SESSION)
                .invokeExact(self, sessionDesc, outSession);
            SlangResult.check(result, "IGlobalSession::createSession");
            return outSession.get(ValueLayout.ADDRESS, 0);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("createSession failed", t);
        }
    }

    public int findProfile(Arena arena, String name) {
        try {
            MemorySegment nameStr = arena.allocateUtf8String(name);
            return (int) getHandle(SLOT_FIND_PROFILE, DESC_FIND_PROFILE)
                .invokeExact(self, nameStr);
        } catch (Throwable t) {
            throw new RuntimeException("findProfile failed", t);
        }
    }

    public String getBuildTagString() {
        try {
            MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_BUILD_TAG_STRING, DESC_GET_BUILD_TAG_STRING)
                .invokeExact(self);
            return ptr.reinterpret(256).getUtf8String(0);
        } catch (Throwable t) {
            throw new RuntimeException("getBuildTagString failed", t);
        }
    }
}
