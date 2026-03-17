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
    private static final int SLOT_CREATE_COMPILE_REQUEST = 13;
    private static final int SLOT_ADD_BUILTINS = 14;
    private static final int SLOT_SET_SHARED_LIBRARY_LOADER = 15;
    private static final int SLOT_GET_SHARED_LIBRARY_LOADER = 16;
    private static final int SLOT_CHECK_COMPILE_TARGET_SUPPORT = 17;
    private static final int SLOT_CHECK_PASS_THROUGH_SUPPORT = 18;
    private static final int SLOT_COMPILE_CORE_MODULE = 19;
    private static final int SLOT_LOAD_CORE_MODULE = 20;
    private static final int SLOT_SAVE_CORE_MODULE = 21;
    private static final int SLOT_FIND_CAPABILITY = 22;
    private static final int SLOT_SET_DOWNSTREAM_COMPILER_FOR_TRANSITION = 23;
    private static final int SLOT_GET_DOWNSTREAM_COMPILER_FOR_TRANSITION = 24;
    private static final int SLOT_GET_COMPILER_ELAPSED_TIME = 25;
    private static final int SLOT_SET_SPIRV_CORE_GRAMMAR = 26;
    private static final int SLOT_PARSE_COMMAND_LINE_ARGUMENTS = 27;
    private static final int SLOT_GET_SESSION_DESC_DIGEST = 28;
    private static final int SLOT_COMPILE_BUILTIN_MODULE = 29;
    private static final int SLOT_LOAD_BUILTIN_MODULE = 30;
    private static final int SLOT_SAVE_BUILTIN_MODULE = 31;

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

    private static final FunctionDescriptor DESC_CREATE_COMPILE_REQUEST =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_SET_SHARED_LIBRARY_LOADER =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_SHARED_LIBRARY_LOADER =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_COMPILE_CORE_MODULE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_LOAD_CORE_MODULE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

    private static final FunctionDescriptor DESC_SAVE_CORE_MODULE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_SET_DOWNSTREAM_COMPILER_FOR_TRANSITION =
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_GET_DOWNSTREAM_COMPILER_FOR_TRANSITION =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_SET_SPIRV_CORE_GRAMMAR =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_PARSE_COMMAND_LINE_ARGUMENTS =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_GET_SESSION_DESC_DIGEST =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_COMPILE_BUILTIN_MODULE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);

    private static final FunctionDescriptor DESC_LOAD_BUILTIN_MODULE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

    private static final FunctionDescriptor DESC_SAVE_BUILTIN_MODULE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

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

    public MemorySegment createCompileRequest(Arena arena) {
        try {
            MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
            int result = (int) getHandle(SLOT_CREATE_COMPILE_REQUEST, DESC_CREATE_COMPILE_REQUEST)
                .invokeExact(self, out);
            SlangResult.check(result, "IGlobalSession::createCompileRequest");
            return out.get(ValueLayout.ADDRESS, 0);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("createCompileRequest failed", t);
        }
    }

    public void setSharedLibraryLoader(MemorySegment loader) {
        try {
            getHandle(SLOT_SET_SHARED_LIBRARY_LOADER, DESC_SET_SHARED_LIBRARY_LOADER)
                .invokeExact(self, loader);
        } catch (Throwable t) {
            throw new RuntimeException("setSharedLibraryLoader failed", t);
        }
    }

    public MemorySegment getSharedLibraryLoader() {
        try {
            return (MemorySegment) getHandle(SLOT_GET_SHARED_LIBRARY_LOADER, DESC_GET_SHARED_LIBRARY_LOADER)
                .invokeExact(self);
        } catch (Throwable t) {
            throw new RuntimeException("getSharedLibraryLoader failed", t);
        }
    }

    public void compileCoreModule(int flags) {
        try {
            int result = (int) getHandle(SLOT_COMPILE_CORE_MODULE, DESC_COMPILE_CORE_MODULE)
                .invokeExact(self, flags);
            SlangResult.check(result, "IGlobalSession::compileCoreModule");
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("compileCoreModule failed", t);
        }
    }

    public void loadCoreModule(MemorySegment data, long size) {
        try {
            int result = (int) getHandle(SLOT_LOAD_CORE_MODULE, DESC_LOAD_CORE_MODULE)
                .invokeExact(self, data, size);
            SlangResult.check(result, "IGlobalSession::loadCoreModule");
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("loadCoreModule failed", t);
        }
    }

    public ISlangBlob saveCoreModule(Arena arena, int archiveType) {
        try {
            MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
            int result = (int) getHandle(SLOT_SAVE_CORE_MODULE, DESC_SAVE_CORE_MODULE)
                .invokeExact(self, archiveType, outBlob);
            SlangResult.check(result, "IGlobalSession::saveCoreModule");
            MemorySegment blobPtr = outBlob.get(ValueLayout.ADDRESS, 0);
            if (blobPtr.equals(MemorySegment.NULL)) return null;
            return new ISlangBlob(blobPtr);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("saveCoreModule failed", t);
        }
    }

    public void setDownstreamCompilerForTransition(int source, int target, int compiler) {
        try {
            getHandle(SLOT_SET_DOWNSTREAM_COMPILER_FOR_TRANSITION, DESC_SET_DOWNSTREAM_COMPILER_FOR_TRANSITION)
                .invokeExact(self, source, target, compiler);
        } catch (Throwable t) {
            throw new RuntimeException("setDownstreamCompilerForTransition failed", t);
        }
    }

    public int getDownstreamCompilerForTransition(int source, int target) {
        try {
            return (int) getHandle(SLOT_GET_DOWNSTREAM_COMPILER_FOR_TRANSITION, DESC_GET_DOWNSTREAM_COMPILER_FOR_TRANSITION)
                .invokeExact(self, source, target);
        } catch (Throwable t) {
            throw new RuntimeException("getDownstreamCompilerForTransition failed", t);
        }
    }

    public void setSPIRVCoreGrammar(Arena arena, String path) {
        try {
            MemorySegment pathStr = arena.allocateUtf8String(path);
            int result = (int) getHandle(SLOT_SET_SPIRV_CORE_GRAMMAR, DESC_SET_SPIRV_CORE_GRAMMAR)
                .invokeExact(self, pathStr);
            SlangResult.check(result, "IGlobalSession::setSPIRVCoreGrammar");
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("setSPIRVCoreGrammar failed", t);
        }
    }

    public MemorySegment parseCommandLineArguments(Arena arena, int argc, MemorySegment argv,
                                                    MemorySegment outSessionDesc, MemorySegment outAuxAllocation) {
        try {
            int result = (int) getHandle(SLOT_PARSE_COMMAND_LINE_ARGUMENTS, DESC_PARSE_COMMAND_LINE_ARGUMENTS)
                .invokeExact(self, argc, argv, outSessionDesc, outAuxAllocation);
            SlangResult.check(result, "IGlobalSession::parseCommandLineArguments");
            return outSessionDesc;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("parseCommandLineArguments failed", t);
        }
    }

    public ISlangBlob getSessionDescDigest(Arena arena, MemorySegment sessionDesc) {
        try {
            MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
            int result = (int) getHandle(SLOT_GET_SESSION_DESC_DIGEST, DESC_GET_SESSION_DESC_DIGEST)
                .invokeExact(self, sessionDesc, outBlob);
            SlangResult.check(result, "IGlobalSession::getSessionDescDigest");
            MemorySegment blobPtr = outBlob.get(ValueLayout.ADDRESS, 0);
            if (blobPtr.equals(MemorySegment.NULL)) return null;
            return new ISlangBlob(blobPtr);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("getSessionDescDigest failed", t);
        }
    }

    public void compileBuiltinModule(int module, int flags) {
        try {
            int result = (int) getHandle(SLOT_COMPILE_BUILTIN_MODULE, DESC_COMPILE_BUILTIN_MODULE)
                .invokeExact(self, module, flags);
            SlangResult.check(result, "IGlobalSession::compileBuiltinModule");
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("compileBuiltinModule failed", t);
        }
    }

    public void loadBuiltinModule(int module, MemorySegment data, long size) {
        try {
            int result = (int) getHandle(SLOT_LOAD_BUILTIN_MODULE, DESC_LOAD_BUILTIN_MODULE)
                .invokeExact(self, module, data, size);
            SlangResult.check(result, "IGlobalSession::loadBuiltinModule");
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("loadBuiltinModule failed", t);
        }
    }

    public ISlangBlob saveBuiltinModule(Arena arena, int module, int archiveType) {
        try {
            MemorySegment outBlob = arena.allocate(ValueLayout.ADDRESS);
            int result = (int) getHandle(SLOT_SAVE_BUILTIN_MODULE, DESC_SAVE_BUILTIN_MODULE)
                .invokeExact(self, module, archiveType, outBlob);
            SlangResult.check(result, "IGlobalSession::saveBuiltinModule");
            MemorySegment blobPtr = outBlob.get(ValueLayout.ADDRESS, 0);
            if (blobPtr.equals(MemorySegment.NULL)) return null;
            return new ISlangBlob(blobPtr);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("saveBuiltinModule failed", t);
        }
    }
}
