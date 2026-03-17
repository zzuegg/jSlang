package dev.slang.bindings.raw;

import dev.slang.bindings.NativeLoader;
import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class IGlobalSession extends ISlangUnknown {

    private static final int SLOT_CREATE_SESSION = 3;
    private static final int SLOT_FIND_PROFILE = 4;
    private static final int SLOT_SET_DOWNSTREAM_COMPILER_PATH = 5;
    private static final int SLOT_SET_DOWNSTREAM_COMPILER_PRELUDE = 6;
    private static final int SLOT_GET_DOWNSTREAM_COMPILER_PRELUDE = 7;
    private static final int SLOT_GET_BUILD_TAG_STRING = 8;
    private static final int SLOT_SET_DEFAULT_DOWNSTREAM_COMPILER = 9;
    private static final int SLOT_GET_DEFAULT_DOWNSTREAM_COMPILER = 10;
    private static final int SLOT_SET_LANGUAGE_PRELUDE = 11;
    private static final int SLOT_GET_LANGUAGE_PRELUDE = 12;
    private static final int SLOT_ADD_BUILTINS = 14;
    private static final int SLOT_CHECK_COMPILE_TARGET_SUPPORT = 17;
    private static final int SLOT_CHECK_PASS_THROUGH_SUPPORT = 18;
    private static final int SLOT_FIND_CAPABILITY = 22;
    private static final int SLOT_GET_COMPILER_ELAPSED_TIME = 25;

    private static final FunctionDescriptor DESC_CREATE_SESSION =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_FIND_PROFILE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_BUILD_TAG_STRING =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_SET_DOWNSTREAM_COMPILER_PATH =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_SET_DOWNSTREAM_COMPILER_PRELUDE =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_DOWNSTREAM_COMPILER_PRELUDE =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_SET_DEFAULT_DOWNSTREAM_COMPILER =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_GET_DEFAULT_DOWNSTREAM_COMPILER =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_SET_LANGUAGE_PRELUDE =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_LANGUAGE_PRELUDE =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_ADD_BUILTINS =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_CHECK_COMPILE_TARGET_SUPPORT =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_CHECK_PASS_THROUGH_SUPPORT =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_FIND_CAPABILITY =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_COMPILER_ELAPSED_TIME =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

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

    public void setDownstreamCompilerPath(Arena arena, int passThrough, String path) {
        try {
            MemorySegment pathStr = arena.allocateUtf8String(path);
            getHandle(SLOT_SET_DOWNSTREAM_COMPILER_PATH, DESC_SET_DOWNSTREAM_COMPILER_PATH)
                .invokeExact(self, passThrough, pathStr);
        } catch (Throwable t) {
            throw new RuntimeException("setDownstreamCompilerPath failed", t);
        }
    }

    public void setDownstreamCompilerPrelude(Arena arena, int passThrough, String prelude) {
        try {
            MemorySegment preludeStr = arena.allocateUtf8String(prelude);
            getHandle(SLOT_SET_DOWNSTREAM_COMPILER_PRELUDE, DESC_SET_DOWNSTREAM_COMPILER_PRELUDE)
                .invokeExact(self, passThrough, preludeStr);
        } catch (Throwable t) {
            throw new RuntimeException("setDownstreamCompilerPrelude failed", t);
        }
    }

    public ISlangBlob getDownstreamCompilerPrelude(Arena arena, int passThrough) {
        try {
            MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
            getHandle(SLOT_GET_DOWNSTREAM_COMPILER_PRELUDE, DESC_GET_DOWNSTREAM_COMPILER_PRELUDE)
                .invokeExact(self, passThrough, outBlob);
            MemorySegment blobPtr = outBlob.get(ValueLayout.ADDRESS, 0);
            if (blobPtr.equals(MemorySegment.NULL)) return null;
            return new ISlangBlob(blobPtr);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("getDownstreamCompilerPrelude failed", t);
        }
    }

    public int setDefaultDownstreamCompiler(int sourceLanguage, int compiler) {
        try {
            return (int) getHandle(SLOT_SET_DEFAULT_DOWNSTREAM_COMPILER, DESC_SET_DEFAULT_DOWNSTREAM_COMPILER)
                .invokeExact(self, sourceLanguage, compiler);
        } catch (Throwable t) {
            throw new RuntimeException("setDefaultDownstreamCompiler failed", t);
        }
    }

    public int getDefaultDownstreamCompiler(int sourceLanguage) {
        try {
            return (int) getHandle(SLOT_GET_DEFAULT_DOWNSTREAM_COMPILER, DESC_GET_DEFAULT_DOWNSTREAM_COMPILER)
                .invokeExact(self, sourceLanguage);
        } catch (Throwable t) {
            throw new RuntimeException("getDefaultDownstreamCompiler failed", t);
        }
    }

    public void setLanguagePrelude(Arena arena, int sourceLanguage, String prelude) {
        try {
            MemorySegment preludeStr = arena.allocateUtf8String(prelude);
            getHandle(SLOT_SET_LANGUAGE_PRELUDE, DESC_SET_LANGUAGE_PRELUDE)
                .invokeExact(self, sourceLanguage, preludeStr);
        } catch (Throwable t) {
            throw new RuntimeException("setLanguagePrelude failed", t);
        }
    }

    public ISlangBlob getLanguagePrelude(Arena arena, int sourceLanguage) {
        try {
            MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
            getHandle(SLOT_GET_LANGUAGE_PRELUDE, DESC_GET_LANGUAGE_PRELUDE)
                .invokeExact(self, sourceLanguage, outBlob);
            MemorySegment blobPtr = outBlob.get(ValueLayout.ADDRESS, 0);
            if (blobPtr.equals(MemorySegment.NULL)) return null;
            return new ISlangBlob(blobPtr);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("getLanguagePrelude failed", t);
        }
    }

    public void addBuiltins(Arena arena, String path, String source) {
        try {
            MemorySegment pathStr = arena.allocateUtf8String(path);
            MemorySegment sourceStr = arena.allocateUtf8String(source);
            getHandle(SLOT_ADD_BUILTINS, DESC_ADD_BUILTINS)
                .invokeExact(self, pathStr, sourceStr);
        } catch (Throwable t) {
            throw new RuntimeException("addBuiltins failed", t);
        }
    }

    public boolean checkCompileTargetSupport(int target) {
        try {
            int result = (int) getHandle(SLOT_CHECK_COMPILE_TARGET_SUPPORT, DESC_CHECK_COMPILE_TARGET_SUPPORT)
                .invokeExact(self, target);
            return result >= 0;
        } catch (Throwable t) {
            throw new RuntimeException("checkCompileTargetSupport failed", t);
        }
    }

    public boolean checkPassThroughSupport(int passThrough) {
        try {
            int result = (int) getHandle(SLOT_CHECK_PASS_THROUGH_SUPPORT, DESC_CHECK_PASS_THROUGH_SUPPORT)
                .invokeExact(self, passThrough);
            return result >= 0;
        } catch (Throwable t) {
            throw new RuntimeException("checkPassThroughSupport failed", t);
        }
    }

    public int findCapability(Arena arena, String name) {
        try {
            MemorySegment nameStr = arena.allocateUtf8String(name);
            return (int) getHandle(SLOT_FIND_CAPABILITY, DESC_FIND_CAPABILITY)
                .invokeExact(self, nameStr);
        } catch (Throwable t) {
            throw new RuntimeException("findCapability failed", t);
        }
    }

    public double[] getCompilerElapsedTime(Arena arena) {
        try {
            MemorySegment outTotal = arena.allocate(ValueLayout.JAVA_DOUBLE);
            MemorySegment outDownstream = arena.allocate(ValueLayout.JAVA_DOUBLE);
            getHandle(SLOT_GET_COMPILER_ELAPSED_TIME, DESC_GET_COMPILER_ELAPSED_TIME)
                .invokeExact(self, outTotal, outDownstream);
            return new double[] {
                outTotal.get(ValueLayout.JAVA_DOUBLE, 0),
                outDownstream.get(ValueLayout.JAVA_DOUBLE, 0)
            };
        } catch (Throwable t) {
            throw new RuntimeException("getCompilerElapsedTime failed", t);
        }
    }
}
