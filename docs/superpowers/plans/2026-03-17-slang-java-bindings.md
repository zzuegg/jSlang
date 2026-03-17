# SlangBindings Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create Java 26 FFM bindings for the Slang shader compiler with raw COM vtable bindings and idiomatic Java API wrappers.

**Architecture:** Three-layer design — raw FFM downcalls matching Slang's COM vtable layout, a native library loader that bundles platform-specific Slang binaries, and an idiomatic Java API with AutoCloseable wrappers, builders, and checked exceptions.

**Tech Stack:** Java 26 (FFM API), Gradle Kotlin DSL, Slang shared library, JUnit 5

---

## File Structure

```
SlangBindings/
├── settings.gradle.kts
├── build.gradle.kts                          # Root build (shared config)
├── gradle.properties                         # Slang version pin, Java version
├── bindings/
│   ├── build.gradle.kts                      # Bindings module build
│   └── src/
│       ├── main/java/dev/slang/bindings/
│       │   ├── NativeLoader.java             # Platform detection + library loading
│       │   ├── COMObject.java                # Base vtable dispatch + MethodHandle cache
│       │   ├── SlangResult.java              # Result code checking utility
│       │   ├── enums/
│       │   │   ├── CompileTarget.java         # SlangCompileTarget enum
│       │   │   ├── SourceLanguage.java        # SlangSourceLanguage enum
│       │   │   ├── Stage.java                 # SlangStage enum
│       │   │   └── ProfileID.java             # SlangProfileID enum (placeholder, runtime lookup)
│       │   ├── structs/
│       │   │   ├── TargetDesc.java            # TargetDesc StructLayout + accessors
│       │   │   ├── SessionDesc.java           # SessionDesc StructLayout + accessors
│       │   │   └── PreprocessorMacroDesc.java # PreprocessorMacroDesc layout
│       │   └── raw/
│       │       ├── ISlangUnknown.java         # Slots 0-2: queryInterface, addRef, release
│       │       ├── ISlangBlob.java            # Slots 3-4: getBufferPointer, getBufferSize
│       │       ├── IGlobalSession.java        # Slots 3+: createSession, findProfile, etc.
│       │       ├── ISession.java              # Slots 3+: loadModule, createCompositeComponentType, etc.
│       │       ├── IComponentType.java        # Slots 3+: getSession, getLayout, getEntryPointCode, link, etc.
│       │       ├── IModule.java               # IComponentType slots + own: findEntryPointByName, getName, etc.
│       │       └── IEntryPoint.java           # IComponentType slots + own: getFunctionReflection
│       └── test/java/dev/slang/bindings/
│           ├── NativeLoaderTest.java
│           ├── COMObjectTest.java
│           └── raw/
│               └── IGlobalSessionTest.java
├── api/
│   ├── build.gradle.kts                      # API module build (depends on :bindings)
│   └── src/
│       ├── main/java/dev/slang/api/
│       │   ├── GlobalSession.java            # AutoCloseable wrapper for IGlobalSession
│       │   ├── Session.java                  # AutoCloseable wrapper for ISession
│       │   ├── Module.java                   # AutoCloseable wrapper for IModule
│       │   ├── EntryPoint.java               # AutoCloseable wrapper for IEntryPoint
│       │   ├── ComponentType.java            # AutoCloseable wrapper for IComponentType
│       │   ├── Blob.java                     # AutoCloseable wrapper for ISlangBlob
│       │   ├── SlangException.java           # Checked exception from SlangResult errors
│       │   ├── SessionDescBuilder.java       # Builder for SessionDesc struct
│       │   └── TargetDescBuilder.java        # Builder for TargetDesc struct
│       └── test/java/dev/slang/api/
│           ├── GlobalSessionTest.java
│           ├── CompilationTest.java          # End-to-end compilation tests
│           └── resources/
│               └── shaders/
│                   └── simple.slang          # Test shader
```

---

## Vtable Slot Reference

### ISlangUnknown (3 methods)
| Slot | Method | Return | Params |
|------|--------|--------|--------|
| 0 | queryInterface | int (SlangResult) | self, guid, outObject |
| 1 | addRef | int (uint32) | self |
| 2 | release | int (uint32) | self |

### ISlangBlob extends ISlangUnknown (2 own methods, 5 total)
| Slot | Method | Return | Params |
|------|--------|--------|--------|
| 3 | getBufferPointer | ADDRESS (void*) | self |
| 4 | getBufferSize | long (size_t) | self |

### IGlobalSession extends ISlangUnknown (28 own methods, 31 total)
| Slot | Method | Return | Params |
|------|--------|--------|--------|
| 3 | createSession | int | self, SessionDesc*, ISession** |
| 4 | findProfile | int (SlangProfileID) | self, char* name |
| 5 | setDownstreamCompilerPath | void | self, int passThrough, char* path |
| 6 | setDownstreamCompilerPrelude | void | self, int passThrough, char* prelude |
| 7 | getDownstreamCompilerPrelude | void | self, int passThrough, ISlangBlob** |
| 8 | getBuildTagString | ADDRESS (char*) | self |
| 9 | setDefaultDownstreamCompiler | int | self, int srcLang, int compiler |
| 10 | getDefaultDownstreamCompiler | int | self, int srcLang |
| 11 | setLanguagePrelude | void | self, int srcLang, char* prelude |
| 12 | getLanguagePrelude | void | self, int srcLang, ISlangBlob** |
| 13 | createCompileRequest | int | self, ICompileRequest** |
| 14 | addBuiltins | void | self, char* path, char* source |
| 15 | setSharedLibraryLoader | void | self, ISlangSharedLibraryLoader* |
| 16 | getSharedLibraryLoader | ADDRESS | self |
| 17 | checkCompileTargetSupport | int | self, int target |
| 18 | checkPassThroughSupport | int | self, int passThrough |
| 19 | compileCoreModule | int | self, int flags |
| 20 | loadCoreModule | int | self, void* data, long size |
| 21 | saveCoreModule | int | self, int archiveType, ISlangBlob** |
| 22 | findCapability | int | self, char* name |
| 23 | setDownstreamCompilerForTransition | void | self, int src, int tgt, int compiler |
| 24 | getDownstreamCompilerForTransition | int | self, int src, int tgt |
| 25 | getCompilerElapsedTime | void | self, double*, double* |
| 26 | setSPIRVCoreGrammar | int | self, char* path |
| 27 | parseCommandLineArguments | int | self, int argc, char**, SessionDesc*, ISlangUnknown** |
| 28 | getSessionDescDigest | int | self, SessionDesc*, ISlangBlob** |
| 29 | compileBuiltinModule | int | self, int module, int flags |
| 30 | loadBuiltinModule | int | self, int module, void* data, long size |
| 31 | saveBuiltinModule | int | self, int module, int archiveType, ISlangBlob** |

### ISession extends ISlangUnknown (21 own methods, 24 total)
| Slot | Method | Return | Params |
|------|--------|--------|--------|
| 3 | getGlobalSession | ADDRESS | self |
| 4 | loadModule | ADDRESS (IModule*) | self, char* name, ISlangBlob** diag |
| 5 | loadModuleFromSource | ADDRESS | self, char* name, char* path, ISlangBlob* src, ISlangBlob** diag |
| 6 | createCompositeComponentType | int | self, IComponentType**, long count, IComponentType**, ISlangBlob** |
| 7 | specializeType | ADDRESS | self, TypeReflection*, SpecializationArg*, long count, ISlangBlob** |
| 8 | getTypeLayout | ADDRESS | self, TypeReflection*, long targetIdx, int rules, ISlangBlob** |
| 9 | getContainerType | ADDRESS | self, TypeReflection*, int containerType, ISlangBlob** |
| 10 | getDynamicType | ADDRESS | self |
| 11 | getTypeRTTIMangledName | int | self, TypeReflection*, ISlangBlob** |
| 12 | getTypeConformanceWitnessMangledName | int | self, TypeReflection*, TypeReflection*, ISlangBlob** |
| 13 | getTypeConformanceWitnessSequentialID | int | self, TypeReflection*, TypeReflection*, uint32_t* |
| 14 | createCompileRequest | int | self, SlangCompileRequest** |
| 15 | createTypeConformanceComponentType | int | self, TypeReflection*, TypeReflection*, ITypeConformance**, long, ISlangBlob** |
| 16 | loadModuleFromIRBlob | ADDRESS | self, char*, char*, ISlangBlob*, ISlangBlob** |
| 17 | getLoadedModuleCount | long | self |
| 18 | getLoadedModule | ADDRESS | self, long index |
| 19 | isBinaryModuleUpToDate | int (bool) | self, char* path, ISlangBlob* blob |
| 20 | loadModuleFromSourceString | ADDRESS | self, char*, char*, char*, ISlangBlob** |
| 21 | getDynamicObjectRTTIBytes | int | self, TypeReflection*, TypeReflection*, uint32_t*, uint32_t |
| 22 | loadModuleInfoFromIRBlob | int | self, char* moduleName, ISlangBlob* source, ISlangBlob** |
| 23 | getDeclSourceLocation | int | self, void* decl, SourceLocation* out |

### IComponentType extends ISlangUnknown (14 own methods, 17 total)
| Slot | Method | Return | Params |
|------|--------|--------|--------|
| 3 | getSession | ADDRESS | self |
| 4 | getLayout | ADDRESS (ProgramLayout*) | self, long targetIdx, ISlangBlob** |
| 5 | getSpecializationParamCount | long | self |
| 6 | getEntryPointCode | int | self, long epIdx, long tgtIdx, ISlangBlob**, ISlangBlob** |
| 7 | getResultAsFileSystem | int | self, long epIdx, long tgtIdx, ISlangMutableFileSystem** |
| 8 | getEntryPointHash | void | self, long epIdx, long tgtIdx, ISlangBlob** |
| 9 | specialize | int | self, SpecializationArg*, long count, IComponentType**, ISlangBlob** |
| 10 | link | int | self, IComponentType**, ISlangBlob** |
| 11 | getEntryPointHostCallable | int | self, int epIdx, int tgtIdx, ISlangSharedLibrary**, ISlangBlob** |
| 12 | renameEntryPoint | int | self, char* name, IComponentType** |
| 13 | linkWithOptions | int | self, IComponentType**, uint32_t count, CompilerOptionEntry*, ISlangBlob** |
| 14 | getTargetCode | int | self, long tgtIdx, ISlangBlob**, ISlangBlob** |
| 15 | getTargetMetadata | int | self, long tgtIdx, IMetadata**, ISlangBlob** |
| 16 | getEntryPointMetadata | int | self, long epIdx, long tgtIdx, IMetadata**, ISlangBlob** |

### IModule extends IComponentType (13 own methods, 30 total)
Inherits 17 slots from IComponentType.
| Slot | Method | Return | Params |
|------|--------|--------|--------|
| 17 | findEntryPointByName | int | self, char* name, IEntryPoint** |
| 18 | getDefinedEntryPointCount | int (int32) | self |
| 19 | getDefinedEntryPoint | int | self, int index, IEntryPoint** |
| 20 | serialize | int | self, ISlangBlob** |
| 21 | writeToFile | int | self, char* fileName |
| 22 | getName | ADDRESS (char*) | self |
| 23 | getFilePath | ADDRESS (char*) | self |
| 24 | getUniqueIdentity | ADDRESS (char*) | self |
| 25 | findAndCheckEntryPoint | int | self, char* name, int stage, IEntryPoint**, ISlangBlob** |
| 26 | getDependencyFileCount | int (int32) | self |
| 27 | getDependencyFilePath | ADDRESS (char*) | self, int index |
| 28 | getModuleReflection | ADDRESS | self |
| 29 | disassemble | int | self, ISlangBlob** |

### IEntryPoint extends IComponentType (1 own method, 18 total)
Inherits 17 slots from IComponentType.
| Slot | Method | Return | Params |
|------|--------|--------|--------|
| 17 | getFunctionReflection | ADDRESS | self |

---

## Task 1: Gradle Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `bindings/build.gradle.kts`
- Create: `api/build.gradle.kts`
- Create: `.gitignore`

- [ ] **Step 1: Create root `settings.gradle.kts`**

```kotlin
rootProject.name = "SlangBindings"

include("bindings")
include("api")
```

- [ ] **Step 2: Create `gradle.properties`**

```properties
slangVersion=2025.8
javaVersion=26
group=dev.slang
version=0.1.0-SNAPSHOT
```

- [ ] **Step 3: Create root `build.gradle.kts`**

```kotlin
plugins {
    java
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(property("javaVersion").toString().toInt()))
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}
```

- [ ] **Step 4: Create `bindings/build.gradle.kts`**

```kotlin
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

- [ ] **Step 5: Create `api/build.gradle.kts`**

```kotlin
dependencies {
    implementation(project(":bindings"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

- [ ] **Step 6: Create `.gitignore`**

```
.gradle/
build/
.idea/
*.iml
out/
```

- [ ] **Step 7: Initialize Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.13`
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/` created.

- [ ] **Step 8: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (no source files yet, but structure works)

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties bindings/build.gradle.kts api/build.gradle.kts .gitignore gradlew gradlew.bat gradle/
git commit -m "feat: scaffold Gradle Kotlin DSL multi-module project"
```

---

## Task 2: Native Loader

**Files:**
- Create: `bindings/src/main/java/dev/slang/bindings/NativeLoader.java`
- Create: `bindings/src/test/java/dev/slang/bindings/NativeLoaderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.slang.bindings;

import org.junit.jupiter.api.Test;
import java.lang.foreign.SymbolLookup;
import static org.junit.jupiter.api.Assertions.*;

class NativeLoaderTest {

    @Test
    void loadsSlangLibrary() {
        SymbolLookup lookup = NativeLoader.load();
        assertNotNull(lookup);
        // Verify we can find the main entry point
        assertTrue(lookup.find("slang_createGlobalSession2").isPresent(),
            "slang_createGlobalSession2 should be found in loaded library");
    }

    @Test
    void detectsPlatform() {
        String libName = NativeLoader.platformLibraryName();
        assertNotNull(libName);
        assertTrue(libName.endsWith(".so") || libName.endsWith(".dll") || libName.endsWith(".dylib"),
            "Library name should have platform-appropriate extension: " + libName);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :bindings:test --tests "dev.slang.bindings.NativeLoaderTest"`
Expected: FAIL — NativeLoader class does not exist.

- [ ] **Step 3: Write NativeLoader implementation**

```java
package dev.slang.bindings;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NativeLoader {

    private static volatile SymbolLookup INSTANCE;

    private NativeLoader() {}

    public static SymbolLookup load() {
        if (INSTANCE == null) {
            synchronized (NativeLoader.class) {
                if (INSTANCE == null) {
                    INSTANCE = loadLibrary();
                }
            }
        }
        return INSTANCE;
    }

    static SymbolLookup loadLibrary() {
        String libName = platformLibraryName();
        String resourcePath = "/natives/" + platformDir() + "/" + libName;

        try (InputStream in = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                // Fall back to system library path
                return SymbolLookup.libraryLookup(System.mapLibraryName("slang"), Arena.global());
            }
            Path tempDir = Files.createTempDirectory("slang-native");
            Path tempLib = tempDir.resolve(libName);
            Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
            tempLib.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();
            return SymbolLookup.libraryLookup(tempLib, Arena.global());
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract Slang native library", e);
        }
    }

    public static String platformLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) return "libslang.so";
        if (os.contains("win")) return "slang.dll";
        if (os.contains("mac")) return "libslang.dylib";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }

    static String platformDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String osName;
        if (os.contains("linux")) osName = "linux";
        else if (os.contains("win")) osName = "windows";
        else if (os.contains("mac")) osName = "macos";
        else throw new UnsupportedOperationException("Unsupported OS: " + os);

        String archName;
        if (arch.equals("amd64") || arch.equals("x86_64")) archName = "x86_64";
        else if (arch.equals("aarch64") || arch.equals("arm64")) archName = "aarch64";
        else throw new UnsupportedOperationException("Unsupported arch: " + arch);

        return osName + "-" + archName;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :bindings:test --tests "dev.slang.bindings.NativeLoaderTest"`
Expected: PASS (will fall back to system library path if bundled native not present; `detectsPlatform` should pass regardless)

Note: The `loadsSlangLibrary` test requires `libslang.so` to be installed on the system or bundled. If it fails, download Slang release and place the library on `LD_LIBRARY_PATH` / system path.

- [ ] **Step 5: Commit**

```bash
git add bindings/src/
git commit -m "feat: add NativeLoader for platform-specific Slang library loading"
```

---

## Task 3: SlangResult + COMObject Base

**Files:**
- Create: `bindings/src/main/java/dev/slang/bindings/SlangResult.java`
- Create: `bindings/src/main/java/dev/slang/bindings/COMObject.java`
- Create: `bindings/src/test/java/dev/slang/bindings/COMObjectTest.java`

- [ ] **Step 1: Write SlangResult utility**

```java
package dev.slang.bindings;

public final class SlangResult {

    private SlangResult() {}

    public static final int SLANG_OK = 0;
    public static final int SLANG_FAIL = Integer.MIN_VALUE; // 0x80000000

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
```

- [ ] **Step 2: Write COMObject base class**

```java
package dev.slang.bindings;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class COMObject implements AutoCloseable {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final int MAX_VTABLE_SLOTS = 64;

    protected final MemorySegment self;
    private final MethodHandle[] handleCache = new MethodHandle[MAX_VTABLE_SLOTS];
    private boolean closed = false;

    // ISlangUnknown vtable slots
    protected static final int SLOT_QUERY_INTERFACE = 0;
    protected static final int SLOT_ADD_REF = 1;
    protected static final int SLOT_RELEASE = 2;

    protected static final FunctionDescriptor DESC_ADD_REF =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
    protected static final FunctionDescriptor DESC_RELEASE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    public COMObject(MemorySegment self) {
        this.self = self.reinterpret(ValueLayout.ADDRESS.byteSize());
    }

    protected MemorySegment vtable() {
        return self.get(ValueLayout.ADDRESS, 0)
                   .reinterpret(256 * ValueLayout.ADDRESS.byteSize());
    }

    protected MemorySegment getVtableSlot(int index) {
        return vtable().get(ValueLayout.ADDRESS, (long) index * ValueLayout.ADDRESS.byteSize());
    }

    protected MethodHandle getHandle(int slot, FunctionDescriptor descriptor) {
        MethodHandle cached = handleCache[slot];
        if (cached != null) return cached;
        MemorySegment fnPtr = getVtableSlot(slot);
        MethodHandle handle = LINKER.downcallHandle(fnPtr, descriptor);
        handleCache[slot] = handle;
        return handle;
    }

    public int addRef() {
        try {
            return (int) getHandle(SLOT_ADD_REF, DESC_ADD_REF).invokeExact(self);
        } catch (Throwable t) {
            throw new RuntimeException("addRef failed", t);
        }
    }

    public int release() {
        try {
            return (int) getHandle(SLOT_RELEASE, DESC_RELEASE).invokeExact(self);
        } catch (Throwable t) {
            throw new RuntimeException("release failed", t);
        }
    }

    public MemorySegment ptr() {
        return self;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            release();
        }
    }
}
```

- [ ] **Step 3: Write COMObject test (integration with real Slang)**

```java
package dev.slang.bindings;

import org.junit.jupiter.api.Test;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import static org.junit.jupiter.api.Assertions.*;

class COMObjectTest {

    @Test
    void createGlobalSessionAndRelease() throws Throwable {
        SymbolLookup lookup = NativeLoader.load();
        MemorySegment createFn = lookup.find("slang_createGlobalSession2").orElseThrow();

        // SlangGlobalSessionDesc is a struct, but passing null/zeroed works for defaults
        try (Arena arena = Arena.ofConfined()) {
            // SlangGlobalSessionDesc: { size_t structureSize; ... } — zero-init is fine
            MemorySegment desc = arena.allocate(64); // oversized, zero-filled
            MemorySegment outSession = arena.allocate(ValueLayout.ADDRESS);

            MethodHandle create = Linker.nativeLinker().downcallHandle(
                createFn,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            int result = (int) create.invokeExact(desc, outSession);
            assertTrue(SlangResult.isOk(result), "createGlobalSession2 should succeed");

            MemorySegment sessionPtr = outSession.get(ValueLayout.ADDRESS, 0);
            assertFalse(sessionPtr.equals(MemorySegment.NULL), "Session pointer should be non-null");

            COMObject session = new COMObject(sessionPtr);
            // addRef then release to verify vtable dispatch works
            int refCount = session.addRef();
            assertTrue(refCount > 0, "addRef should return positive ref count");

            int refAfterRelease = session.release();
            assertTrue(refAfterRelease >= 1, "release should return remaining ref count >= 1");

            // Final release via close
            session.close();
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :bindings:test`
Expected: PASS — creates a global session, exercises addRef/release via vtable.

- [ ] **Step 5: Commit**

```bash
git add bindings/src/
git commit -m "feat: add SlangResult utility and COMObject vtable dispatch base"
```

---

## Task 4: Enums

**Files:**
- Create: `bindings/src/main/java/dev/slang/bindings/enums/CompileTarget.java`
- Create: `bindings/src/main/java/dev/slang/bindings/enums/SourceLanguage.java`
- Create: `bindings/src/main/java/dev/slang/bindings/enums/Stage.java`

- [ ] **Step 1: Write CompileTarget enum**

```java
package dev.slang.bindings.enums;

import java.util.Optional;

public enum CompileTarget {
    UNKNOWN(0),
    NONE(1),
    GLSL(2),
    HLSL(5),
    SPIRV(6),
    SPIRV_ASM(7),
    DXBC(8),
    DXBC_ASM(9),
    DXIL(10),
    DXIL_ASM(11),
    C_SOURCE(12),
    CPP_SOURCE(13),
    HOST_EXECUTABLE(14),
    SHADER_SHARED_LIBRARY(15),
    SHADER_HOST_CALLABLE(16),
    CUDA_SOURCE(17),
    PTX(18),
    CUDA_OBJECT_CODE(19),
    OBJECT_CODE(20),
    HOST_CPP_SOURCE(21),
    HOST_HOST_CALLABLE(22),
    CPP_PYTORCH_BINDING(23),
    METAL(24),
    METAL_LIB(25),
    METAL_LIB_ASM(26),
    HOST_SHARED_LIBRARY(27),
    WGSL(28),
    WGSL_SPIRV_ASM(29),
    WGSL_SPIRV(30),
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
```

- [ ] **Step 2: Write SourceLanguage enum**

```java
package dev.slang.bindings.enums;

import java.util.Optional;

public enum SourceLanguage {
    UNKNOWN(0),
    SLANG(1),
    HLSL(2),
    GLSL(3),
    C(4),
    CPP(5),
    CUDA(6),
    SPIRV(7),
    METAL(8),
    WGSL(9);

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
```

- [ ] **Step 3: Write Stage enum**

```java
package dev.slang.bindings.enums;

import java.util.Optional;

public enum Stage {
    NONE(0),
    VERTEX(1),
    HULL(2),
    DOMAIN(3),
    GEOMETRY(4),
    FRAGMENT(5),
    COMPUTE(6),
    RAY_GENERATION(7),
    INTERSECTION(8),
    ANY_HIT(9),
    CLOSEST_HIT(10),
    MISS(11),
    CALLABLE(12),
    MESH(13),
    AMPLIFICATION(14);

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
```

- [ ] **Step 4: Commit**

```bash
git add bindings/src/main/java/dev/slang/bindings/enums/
git commit -m "feat: add CompileTarget, SourceLanguage, and Stage enums"
```

---

## Task 5: Struct Layouts (TargetDesc, SessionDesc)

**Files:**
- Create: `bindings/src/main/java/dev/slang/bindings/structs/TargetDesc.java`
- Create: `bindings/src/main/java/dev/slang/bindings/structs/SessionDesc.java`
- Create: `bindings/src/main/java/dev/slang/bindings/structs/PreprocessorMacroDesc.java`

- [ ] **Step 1: Write TargetDesc struct layout**

```java
package dev.slang.bindings.structs;

import dev.slang.bindings.enums.CompileTarget;
import java.lang.foreign.*;

public final class TargetDesc {

    private TargetDesc() {}

    // struct TargetDesc {
    //     size_t structureSize;           // offset 0
    //     SlangCompileTarget format;      // offset 8  (int)
    //     SlangProfileID profile;         // offset 12 (int)
    //     SlangTargetFlags flags;         // offset 16 (int)
    //     SlangFloatingPointMode fpMode;  // offset 20 (int)
    //     SlangLineDirectiveMode lineMode;// offset 24 (int)
    //     bool forceGLSLScalar;           // offset 28 (1 byte)
    //     // padding to 32
    //     CompilerOptionEntry* options;   // offset 32 (pointer)
    //     uint32_t optionCount;           // offset 40 (int)
    // }

    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_LONG.withName("structureSize"),        // 0
        ValueLayout.JAVA_INT.withName("format"),                // 8
        ValueLayout.JAVA_INT.withName("profile"),               // 12
        ValueLayout.JAVA_INT.withName("flags"),                 // 16
        ValueLayout.JAVA_INT.withName("floatingPointMode"),     // 20
        ValueLayout.JAVA_INT.withName("lineDirectiveMode"),     // 24
        ValueLayout.JAVA_BYTE.withName("forceGLSLScalarBufferLayout"), // 28
        MemoryLayout.paddingLayout(3),                          // 29-31
        ValueLayout.ADDRESS.withName("compilerOptionEntries"),  // 32
        ValueLayout.JAVA_INT.withName("compilerOptionEntryCount"), // 40
        MemoryLayout.paddingLayout(4)                           // 44-47 (pad to 48)
    );

    public static MemorySegment allocate(Arena arena, CompileTarget target, int profileId) {
        MemorySegment seg = arena.allocate(LAYOUT);
        seg.set(ValueLayout.JAVA_LONG, 0, LAYOUT.byteSize());
        seg.set(ValueLayout.JAVA_INT, 8, target.value());
        seg.set(ValueLayout.JAVA_INT, 12, profileId);
        return seg;
    }
}
```

- [ ] **Step 2: Write SessionDesc struct layout**

```java
package dev.slang.bindings.structs;

import java.lang.foreign.*;

public final class SessionDesc {

    private SessionDesc() {}

    // struct SessionDesc {
    //     size_t structureSize;          // 0
    //     TargetDesc const* targets;     // 8  (pointer)
    //     SlangInt targetCount;          // 16 (long/int64)
    //     SessionFlags flags;            // 24 (uint32)
    //     // padding                     // 28
    //     SlangMatrixLayoutMode mode;    // 32 (int) -- actually at offset 28 if packed, need to verify
    //     char** searchPaths;            // pointer
    //     SlangInt searchPathCount;      // long
    //     PreprocessorMacroDesc* macros; // pointer
    //     SlangInt macroCount;           // long
    //     ISlangFileSystem* fileSystem;  // pointer
    //     bool enableEffectAnnotations;  // 1 byte
    //     bool allowGLSLSyntax;          // 1 byte
    //     CompilerOptionEntry* options;  // pointer
    //     uint32_t optionCount;          // uint32
    //     bool skipSPIRVValidation;      // 1 byte
    // }

    // Full layout is complex with alignment. We allocate generously and set key fields.
    // The struct starts with structureSize which tells Slang the version.
    public static final long SIZE = 128; // Conservative overallocation

    public static MemorySegment allocate(Arena arena, MemorySegment targets, long targetCount) {
        MemorySegment seg = arena.allocate(SIZE);
        // structureSize at offset 0
        seg.set(ValueLayout.JAVA_LONG, 0, SIZE);
        // targets pointer at offset 8
        seg.set(ValueLayout.ADDRESS, 8, targets);
        // targetCount at offset 16
        seg.set(ValueLayout.JAVA_LONG, 16, targetCount);
        // defaultMatrixLayoutMode at offset 28 — 0 = row major (default)
        return seg;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add bindings/src/main/java/dev/slang/bindings/structs/
git commit -m "feat: add TargetDesc and SessionDesc FFM struct layouts"
```

---

## Task 6: Raw IGlobalSession Binding

**Files:**
- Create: `bindings/src/main/java/dev/slang/bindings/raw/ISlangUnknown.java`
- Create: `bindings/src/main/java/dev/slang/bindings/raw/ISlangBlob.java`
- Create: `bindings/src/main/java/dev/slang/bindings/raw/IGlobalSession.java`
- Create: `bindings/src/test/java/dev/slang/bindings/raw/IGlobalSessionTest.java`

- [ ] **Step 1: Write ISlangUnknown raw binding**

This is just `COMObject` re-exported with the proper name. The base class already has slots 0-2.

```java
package dev.slang.bindings.raw;

import dev.slang.bindings.COMObject;
import java.lang.foreign.MemorySegment;

public class ISlangUnknown extends COMObject {
    public ISlangUnknown(MemorySegment self) {
        super(self);
    }
}
```

- [ ] **Step 2: Write ISlangBlob raw binding**

```java
package dev.slang.bindings.raw;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class ISlangBlob extends ISlangUnknown {

    private static final int SLOT_GET_BUFFER_POINTER = 3;
    private static final int SLOT_GET_BUFFER_SIZE = 4;

    private static final FunctionDescriptor DESC_GET_BUFFER_POINTER =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    private static final FunctionDescriptor DESC_GET_BUFFER_SIZE =
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

    public ISlangBlob(MemorySegment self) {
        super(self);
    }

    public MemorySegment getBufferPointer() {
        try {
            return (MemorySegment) getHandle(SLOT_GET_BUFFER_POINTER, DESC_GET_BUFFER_POINTER)
                .invokeExact(self);
        } catch (Throwable t) {
            throw new RuntimeException("getBufferPointer failed", t);
        }
    }

    public long getBufferSize() {
        try {
            return (long) getHandle(SLOT_GET_BUFFER_SIZE, DESC_GET_BUFFER_SIZE)
                .invokeExact(self);
        } catch (Throwable t) {
            throw new RuntimeException("getBufferSize failed", t);
        }
    }

    public byte[] toByteArray() {
        long size = getBufferSize();
        MemorySegment ptr = getBufferPointer().reinterpret(size);
        return ptr.toArray(ValueLayout.JAVA_BYTE);
    }
}
```

- [ ] **Step 3: Write IGlobalSession raw binding (core methods)**

```java
package dev.slang.bindings.raw;

import dev.slang.bindings.NativeLoader;
import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class IGlobalSession extends ISlangUnknown {

    // Vtable slots (ISlangUnknown has 3, our methods start at 3)
    private static final int SLOT_CREATE_SESSION = 3;
    private static final int SLOT_FIND_PROFILE = 4;
    private static final int SLOT_GET_BUILD_TAG_STRING = 8;

    private static final FunctionDescriptor DESC_CREATE_SESSION =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // self
            ValueLayout.ADDRESS, // SessionDesc*
            ValueLayout.ADDRESS  // ISession** out
        );

    private static final FunctionDescriptor DESC_FIND_PROFILE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // self
            ValueLayout.ADDRESS  // char* name
        );

    private static final FunctionDescriptor DESC_GET_BUILD_TAG_STRING =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS  // self
        );

    public IGlobalSession(MemorySegment self) {
        super(self);
    }

    public static IGlobalSession create() {
        SymbolLookup lookup = NativeLoader.load();
        MemorySegment createFn = lookup.find("slang_createGlobalSession2").orElseThrow(
            () -> new RuntimeException("slang_createGlobalSession2 not found")
        );

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment desc = arena.allocate(64); // SlangGlobalSessionDesc, zero-init for defaults
            MemorySegment outSession = arena.allocate(ValueLayout.ADDRESS);

            MethodHandle create = Linker.nativeLinker().downcallHandle(
                createFn,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

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
            MemorySegment nameStr = arena.allocateFrom(name);
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
            return ptr.reinterpret(256).getString(0);
        } catch (Throwable t) {
            throw new RuntimeException("getBuildTagString failed", t);
        }
    }
}
```

- [ ] **Step 4: Write IGlobalSession test**

```java
package dev.slang.bindings.raw;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IGlobalSessionTest {

    @Test
    void createAndGetBuildTag() {
        try (var session = IGlobalSession.create()) {
            String tag = session.getBuildTagString();
            assertNotNull(tag);
            assertFalse(tag.isEmpty(), "Build tag should not be empty");
            System.out.println("Slang build tag: " + tag);
        }
    }

    @Test
    void findProfile() {
        try (var session = IGlobalSession.create()) {
            try (var arena = java.lang.foreign.Arena.ofConfined()) {
                int profileId = session.findProfile(arena, "spirv_1_5");
                assertTrue(profileId >= 0, "spirv_1_5 profile should be found, got: " + profileId);
            }
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :bindings:test`
Expected: PASS — creates global session, reads build tag, finds profile.

- [ ] **Step 6: Commit**

```bash
git add bindings/src/
git commit -m "feat: add raw ISlangUnknown, ISlangBlob, IGlobalSession FFM bindings"
```

---

## Task 7: Raw ISession + IComponentType + IModule + IEntryPoint Bindings

**Files:**
- Create: `bindings/src/main/java/dev/slang/bindings/raw/ISession.java`
- Create: `bindings/src/main/java/dev/slang/bindings/raw/IComponentType.java`
- Create: `bindings/src/main/java/dev/slang/bindings/raw/IModule.java`
- Create: `bindings/src/main/java/dev/slang/bindings/raw/IEntryPoint.java`

- [ ] **Step 1: Write ISession raw binding**

```java
package dev.slang.bindings.raw;

import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;

public class ISession extends ISlangUnknown {

    private static final int SLOT_GET_GLOBAL_SESSION = 3;
    private static final int SLOT_LOAD_MODULE = 4;
    private static final int SLOT_LOAD_MODULE_FROM_SOURCE = 5;
    private static final int SLOT_CREATE_COMPOSITE_COMPONENT_TYPE = 6;
    private static final int SLOT_LOAD_MODULE_FROM_SOURCE_STRING = 20;

    private static final FunctionDescriptor DESC_LOAD_MODULE =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, // self
            ValueLayout.ADDRESS, // char* moduleName
            ValueLayout.ADDRESS  // IBlob** outDiagnostics
        );

    private static final FunctionDescriptor DESC_CREATE_COMPOSITE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // self
            ValueLayout.ADDRESS, // IComponentType** array
            ValueLayout.JAVA_LONG, // count
            ValueLayout.ADDRESS, // IComponentType** out
            ValueLayout.ADDRESS  // IBlob** outDiag
        );

    private static final FunctionDescriptor DESC_LOAD_MODULE_FROM_SOURCE_STRING =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, // self
            ValueLayout.ADDRESS, // char* moduleName
            ValueLayout.ADDRESS, // char* path
            ValueLayout.ADDRESS, // char* string
            ValueLayout.ADDRESS  // IBlob** outDiag
        );

    public ISession(MemorySegment self) {
        super(self);
    }

    public IModule loadModule(Arena arena, String moduleName) {
        MemorySegment nameStr = arena.allocateFrom(moduleName);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment modulePtr = (MemorySegment) getHandle(SLOT_LOAD_MODULE, DESC_LOAD_MODULE)
                .invokeExact(self, nameStr, outDiag);
            if (modulePtr.equals(MemorySegment.NULL)) {
                throw new RuntimeException("loadModule returned null for: " + moduleName);
            }
            return new IModule(modulePtr);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("loadModule failed", t);
        }
    }

    public IModule loadModuleFromSourceString(Arena arena, String moduleName, String path, String source) {
        MemorySegment nameStr = arena.allocateFrom(moduleName);
        MemorySegment pathStr = arena.allocateFrom(path);
        MemorySegment sourceStr = arena.allocateFrom(source);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment modulePtr = (MemorySegment)
                getHandle(SLOT_LOAD_MODULE_FROM_SOURCE_STRING, DESC_LOAD_MODULE_FROM_SOURCE_STRING)
                    .invokeExact(self, nameStr, pathStr, sourceStr, outDiag);
            if (modulePtr.equals(MemorySegment.NULL)) {
                // Try to get diagnostics
                MemorySegment diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
                String diagMsg = "";
                if (!diagPtr.equals(MemorySegment.NULL)) {
                    ISlangBlob diagBlob = new ISlangBlob(diagPtr);
                    byte[] bytes = diagBlob.toByteArray();
                    diagMsg = new String(bytes);
                    diagBlob.release();
                }
                throw new RuntimeException("loadModuleFromSourceString failed: " + diagMsg);
            }
            return new IModule(modulePtr);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("loadModuleFromSourceString failed", t);
        }
    }

    public IComponentType createCompositeComponentType(Arena arena, IComponentType... components) {
        MemorySegment array = arena.allocate(ValueLayout.ADDRESS, components.length);
        for (int i = 0; i < components.length; i++) {
            array.setAtIndex(ValueLayout.ADDRESS, i, components[i].ptr());
        }
        MemorySegment outComposite = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_CREATE_COMPOSITE_COMPONENT_TYPE, DESC_CREATE_COMPOSITE)
                .invokeExact(self, array, (long) components.length, outComposite, outDiag);
            SlangResult.check(result, "createCompositeComponentType");
            return new IComponentType(outComposite.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("createCompositeComponentType failed", t);
        }
    }
}
```

- [ ] **Step 2: Write IComponentType raw binding**

```java
package dev.slang.bindings.raw;

import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;

public class IComponentType extends ISlangUnknown {

    private static final int SLOT_GET_SESSION = 3;
    private static final int SLOT_GET_LAYOUT = 4;
    private static final int SLOT_GET_SPECIALIZATION_PARAM_COUNT = 5;
    private static final int SLOT_GET_ENTRY_POINT_CODE = 6;
    private static final int SLOT_LINK = 10;
    private static final int SLOT_GET_TARGET_CODE = 14;

    private static final FunctionDescriptor DESC_GET_ENTRY_POINT_CODE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // self
            ValueLayout.JAVA_LONG, // entryPointIndex
            ValueLayout.JAVA_LONG, // targetIndex
            ValueLayout.ADDRESS, // IBlob** outCode
            ValueLayout.ADDRESS  // IBlob** outDiag
        );

    private static final FunctionDescriptor DESC_LINK =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // self
            ValueLayout.ADDRESS, // IComponentType** out
            ValueLayout.ADDRESS  // IBlob** outDiag
        );

    private static final FunctionDescriptor DESC_GET_TARGET_CODE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // self
            ValueLayout.JAVA_LONG, // targetIndex
            ValueLayout.ADDRESS, // IBlob** outCode
            ValueLayout.ADDRESS  // IBlob** outDiag
        );

    public IComponentType(MemorySegment self) {
        super(self);
    }

    public IComponentType link(Arena arena) {
        MemorySegment outLinked = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_LINK, DESC_LINK)
                .invokeExact(self, outLinked, outDiag);
            SlangResult.check(result, "IComponentType::link");
            return new IComponentType(outLinked.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("link failed", t);
        }
    }

    public ISlangBlob getEntryPointCode(Arena arena, long entryPointIndex, long targetIndex) {
        MemorySegment outCode = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_GET_ENTRY_POINT_CODE, DESC_GET_ENTRY_POINT_CODE)
                .invokeExact(self, entryPointIndex, targetIndex, outCode, outDiag);
            SlangResult.check(result, "IComponentType::getEntryPointCode");
            return new ISlangBlob(outCode.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("getEntryPointCode failed", t);
        }
    }

    public ISlangBlob getTargetCode(Arena arena, long targetIndex) {
        MemorySegment outCode = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_GET_TARGET_CODE, DESC_GET_TARGET_CODE)
                .invokeExact(self, targetIndex, outCode, outDiag);
            SlangResult.check(result, "IComponentType::getTargetCode");
            return new ISlangBlob(outCode.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("getTargetCode failed", t);
        }
    }
}
```

- [ ] **Step 3: Write IModule raw binding**

```java
package dev.slang.bindings.raw;

import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;

public class IModule extends IComponentType {

    // IComponentType has 14 own methods (slots 3-16), total 17 slots
    // IModule own methods start at slot 17
    private static final int SLOT_FIND_ENTRY_POINT_BY_NAME = 17;
    private static final int SLOT_GET_DEFINED_ENTRY_POINT_COUNT = 18;
    private static final int SLOT_GET_DEFINED_ENTRY_POINT = 19;
    private static final int SLOT_GET_NAME = 22;
    private static final int SLOT_GET_FILE_PATH = 23;
    private static final int SLOT_FIND_AND_CHECK_ENTRY_POINT = 25;

    private static final FunctionDescriptor DESC_FIND_ENTRY_POINT_BY_NAME =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // self
            ValueLayout.ADDRESS, // char* name
            ValueLayout.ADDRESS  // IEntryPoint** out
        );

    private static final FunctionDescriptor DESC_GET_NAME =
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_FIND_AND_CHECK_ENTRY_POINT =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // self
            ValueLayout.ADDRESS, // char* name
            ValueLayout.JAVA_INT, // SlangStage
            ValueLayout.ADDRESS, // IEntryPoint** out
            ValueLayout.ADDRESS  // ISlangBlob** outDiag
        );

    public IModule(MemorySegment self) {
        super(self);
    }

    public IEntryPoint findEntryPointByName(Arena arena, String name) {
        MemorySegment nameStr = arena.allocateFrom(name);
        MemorySegment outEntryPoint = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_FIND_ENTRY_POINT_BY_NAME, DESC_FIND_ENTRY_POINT_BY_NAME)
                .invokeExact(self, nameStr, outEntryPoint);
            SlangResult.check(result, "IModule::findEntryPointByName(" + name + ")");
            return new IEntryPoint(outEntryPoint.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("findEntryPointByName failed", t);
        }
    }

    public IEntryPoint findAndCheckEntryPoint(Arena arena, String name, int stage) {
        MemorySegment nameStr = arena.allocateFrom(name);
        MemorySegment outEntryPoint = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_FIND_AND_CHECK_ENTRY_POINT, DESC_FIND_AND_CHECK_ENTRY_POINT)
                .invokeExact(self, nameStr, stage, outEntryPoint, outDiag);
            SlangResult.check(result, "IModule::findAndCheckEntryPoint(" + name + ")");
            return new IEntryPoint(outEntryPoint.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("findAndCheckEntryPoint failed", t);
        }
    }

    public String getName() {
        try {
            MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_NAME, DESC_GET_NAME)
                .invokeExact(self);
            return ptr.reinterpret(256).getString(0);
        } catch (Throwable t) {
            throw new RuntimeException("getName failed", t);
        }
    }
}
```

- [ ] **Step 4: Write IEntryPoint raw binding**

```java
package dev.slang.bindings.raw;

import java.lang.foreign.*;

public class IEntryPoint extends IComponentType {

    // IComponentType has 14 own methods (slots 3-16), total 17 slots
    // IEntryPoint own methods start at slot 17
    private static final int SLOT_GET_FUNCTION_REFLECTION = 17;

    public IEntryPoint(MemorySegment self) {
        super(self);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add bindings/src/main/java/dev/slang/bindings/raw/
git commit -m "feat: add raw ISession, IComponentType, IModule, IEntryPoint FFM bindings"
```

---

## Task 8: End-to-End Compilation Test (Raw Bindings)

**Files:**
- Create: `bindings/src/test/java/dev/slang/bindings/raw/CompilationRawTest.java`

- [ ] **Step 1: Write end-to-end compilation test using raw bindings**

```java
package dev.slang.bindings.raw;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.structs.SessionDesc;
import dev.slang.bindings.structs.TargetDesc;
import org.junit.jupiter.api.Test;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;

class CompilationRawTest {

    @Test
    void compileSimpleShaderToSpirv() {
        try (var globalSession = IGlobalSession.create()) {
            try (Arena arena = Arena.ofConfined()) {
                // Find SPIRV profile
                int profileId = globalSession.findProfile(arena, "spirv_1_5");

                // Create TargetDesc for SPIRV
                MemorySegment targetDesc = TargetDesc.allocate(arena, CompileTarget.SPIRV, profileId);

                // Create SessionDesc
                MemorySegment sessionDesc = SessionDesc.allocate(arena, targetDesc, 1);

                // Create session
                MemorySegment outSession = arena.allocate(ValueLayout.ADDRESS);
                globalSession.createSessionRaw(sessionDesc, outSession);
                ISession session = new ISession(outSession.get(ValueLayout.ADDRESS, 0));

                // Load shader from source string
                String shaderSource = """
                    [shader("compute")]
                    [numthreads(1, 1, 1)]
                    void computeMain(uint3 tid : SV_DispatchThreadID)
                    {
                    }
                    """;

                IModule module = session.loadModuleFromSourceString(
                    arena, "test-module", "test.slang", shaderSource);
                assertNotNull(module);

                // Find entry point
                IEntryPoint entryPoint = module.findAndCheckEntryPoint(
                    arena, "computeMain", 6 /* SLANG_STAGE_COMPUTE */);
                assertNotNull(entryPoint);

                // Create composite and link
                IComponentType composite = session.createCompositeComponentType(arena, module, entryPoint);
                IComponentType linked = composite.link(arena);

                // Get SPIRV output
                ISlangBlob spirvBlob = linked.getEntryPointCode(arena, 0, 0);
                byte[] spirvBytes = spirvBlob.toByteArray();
                assertTrue(spirvBytes.length > 0, "SPIRV output should not be empty");

                // SPIRV magic number check (0x07230203)
                assertTrue(spirvBytes.length >= 4, "SPIRV should be at least 4 bytes");
                int magic = (spirvBytes[0] & 0xFF)
                    | ((spirvBytes[1] & 0xFF) << 8)
                    | ((spirvBytes[2] & 0xFF) << 16)
                    | ((spirvBytes[3] & 0xFF) << 24);
                assertEquals(0x07230203, magic, "SPIRV magic number mismatch");

                System.out.println("SPIRV output size: " + spirvBytes.length + " bytes");

                // Cleanup
                spirvBlob.release();
                linked.release();
                composite.release();
                entryPoint.release();
                module.release();
                session.release();
            }
        }
    }

    @Test
    void compileToHLSL() {
        try (var globalSession = IGlobalSession.create()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment targetDesc = TargetDesc.allocate(arena, CompileTarget.HLSL, 0);
                MemorySegment sessionDesc = SessionDesc.allocate(arena, targetDesc, 1);

                MemorySegment outSession = arena.allocate(ValueLayout.ADDRESS);
                globalSession.createSessionRaw(sessionDesc, outSession);
                ISession session = new ISession(outSession.get(ValueLayout.ADDRESS, 0));

                String shaderSource = """
                    [shader("compute")]
                    [numthreads(1, 1, 1)]
                    void computeMain(uint3 tid : SV_DispatchThreadID)
                    {
                    }
                    """;

                IModule module = session.loadModuleFromSourceString(
                    arena, "test-hlsl", "test.slang", shaderSource);
                IEntryPoint entryPoint = module.findAndCheckEntryPoint(arena, "computeMain", 6);
                IComponentType composite = session.createCompositeComponentType(arena, module, entryPoint);
                IComponentType linked = composite.link(arena);

                ISlangBlob hlslBlob = linked.getEntryPointCode(arena, 0, 0);
                byte[] hlslBytes = hlslBlob.toByteArray();
                String hlsl = new String(hlslBytes);

                assertTrue(hlsl.contains("computeMain") || hlsl.contains("void"),
                    "HLSL output should contain shader code");
                System.out.println("HLSL output:\n" + hlsl);

                hlslBlob.release();
                linked.release();
                composite.release();
                entryPoint.release();
                module.release();
                session.release();
            }
        }
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :bindings:test`
Expected: PASS — compiles a simple shader to SPIRV and HLSL.

- [ ] **Step 3: Commit**

```bash
git add bindings/src/test/
git commit -m "test: add end-to-end raw binding compilation tests (SPIRV + HLSL)"
```

---

## Task 9: SlangException + Idiomatic API Wrappers

**Files:**
- Create: `api/src/main/java/dev/slang/api/SlangException.java`
- Create: `api/src/main/java/dev/slang/api/Blob.java`
- Create: `api/src/main/java/dev/slang/api/TargetDescBuilder.java`
- Create: `api/src/main/java/dev/slang/api/SessionDescBuilder.java`
- Create: `api/src/main/java/dev/slang/api/GlobalSession.java`
- Create: `api/src/main/java/dev/slang/api/Session.java`
- Create: `api/src/main/java/dev/slang/api/Module.java`
- Create: `api/src/main/java/dev/slang/api/EntryPoint.java`
- Create: `api/src/main/java/dev/slang/api/ComponentType.java`

- [ ] **Step 1: Write SlangException**

```java
package dev.slang.api;

public class SlangException extends Exception {
    private final int resultCode;

    public SlangException(int resultCode, String message) {
        super(message + " (SlangResult: 0x" + Integer.toHexString(resultCode) + ")");
        this.resultCode = resultCode;
    }

    public int getResultCode() { return resultCode; }
}
```

- [ ] **Step 2: Write Blob wrapper**

```java
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
```

- [ ] **Step 3: Write TargetDescBuilder**

```java
package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.structs.TargetDesc;
import java.lang.foreign.*;

public class TargetDescBuilder {
    private CompileTarget format = CompileTarget.UNKNOWN;
    private int profileId = 0;

    public TargetDescBuilder format(CompileTarget format) {
        this.format = format;
        return this;
    }

    public TargetDescBuilder profile(int profileId) {
        this.profileId = profileId;
        return this;
    }

    public MemorySegment build(Arena arena) {
        return TargetDesc.allocate(arena, format, profileId);
    }
}
```

- [ ] **Step 4: Write SessionDescBuilder**

```java
package dev.slang.api;

import dev.slang.bindings.structs.SessionDesc;
import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

public class SessionDescBuilder {
    private final List<TargetDescBuilder> targets = new ArrayList<>();

    public SessionDescBuilder addTarget(TargetDescBuilder target) {
        targets.add(target);
        return this;
    }

    public MemorySegment build(Arena arena) {
        MemorySegment targetArray = arena.allocate(
            dev.slang.bindings.structs.TargetDesc.LAYOUT.byteSize() * targets.size());
        for (int i = 0; i < targets.size(); i++) {
            MemorySegment target = targets.get(i).build(arena);
            MemorySegment.copy(target, 0, targetArray,
                i * dev.slang.bindings.structs.TargetDesc.LAYOUT.byteSize(),
                dev.slang.bindings.structs.TargetDesc.LAYOUT.byteSize());
        }
        return SessionDesc.allocate(arena, targetArray, targets.size());
    }
}
```

- [ ] **Step 5: Write ComponentType wrapper**

```java
package dev.slang.api;

import dev.slang.bindings.raw.IComponentType;
import dev.slang.bindings.raw.ISlangBlob;
import java.lang.foreign.Arena;

public class ComponentType implements AutoCloseable {
    protected final IComponentType raw;

    public ComponentType(IComponentType raw) {
        this.raw = raw;
    }

    public ComponentType link() {
        try (Arena arena = Arena.ofConfined()) {
            return new ComponentType(raw.link(arena));
        }
    }

    public Blob getEntryPointCode(int entryPointIndex, int targetIndex) {
        try (Arena arena = Arena.ofConfined()) {
            ISlangBlob blob = raw.getEntryPointCode(arena, entryPointIndex, targetIndex);
            return new Blob(blob);
        }
    }

    public Blob getTargetCode(int targetIndex) {
        try (Arena arena = Arena.ofConfined()) {
            ISlangBlob blob = raw.getTargetCode(arena, targetIndex);
            return new Blob(blob);
        }
    }

    public IComponentType raw() { return raw; }

    @Override
    public void close() {
        raw.release();
    }
}
```

- [ ] **Step 6: Write EntryPoint wrapper**

```java
package dev.slang.api;

import dev.slang.bindings.raw.IEntryPoint;

public class EntryPoint extends ComponentType {
    public EntryPoint(IEntryPoint raw) {
        super(raw);
    }
}
```

- [ ] **Step 7: Write Module wrapper**

```java
package dev.slang.api;

import dev.slang.bindings.raw.IModule;
import dev.slang.bindings.enums.Stage;
import java.lang.foreign.Arena;

public class Module extends ComponentType {
    private final IModule rawModule;

    public Module(IModule raw) {
        super(raw);
        this.rawModule = raw;
    }

    public EntryPoint findEntryPoint(String name) {
        try (Arena arena = Arena.ofConfined()) {
            return new EntryPoint(rawModule.findEntryPointByName(arena, name));
        }
    }

    public EntryPoint findAndCheckEntryPoint(String name, Stage stage) {
        try (Arena arena = Arena.ofConfined()) {
            return new EntryPoint(
                rawModule.findAndCheckEntryPoint(arena, name, stage.value()));
        }
    }

    public String getName() {
        return rawModule.getName();
    }
}
```

- [ ] **Step 8: Write Session wrapper**

```java
package dev.slang.api;

import dev.slang.bindings.raw.IComponentType;
import dev.slang.bindings.raw.ISession;
import java.lang.foreign.Arena;

public class Session implements AutoCloseable {
    private final ISession raw;

    public Session(ISession raw) {
        this.raw = raw;
    }

    public Module loadModule(String moduleName) {
        try (Arena arena = Arena.ofConfined()) {
            return new Module(raw.loadModule(arena, moduleName));
        }
    }

    public Module loadModuleFromSourceString(String moduleName, String path, String source) {
        try (Arena arena = Arena.ofConfined()) {
            return new Module(raw.loadModuleFromSourceString(arena, moduleName, path, source));
        }
    }

    public ComponentType createCompositeComponentType(ComponentType... components) {
        try (Arena arena = Arena.ofConfined()) {
            IComponentType[] rawComponents = new IComponentType[components.length];
            for (int i = 0; i < components.length; i++) {
                rawComponents[i] = components[i].raw();
            }
            return new ComponentType(raw.createCompositeComponentType(arena, rawComponents));
        }
    }

    @Override
    public void close() {
        raw.release();
    }
}
```

- [ ] **Step 9: Write GlobalSession wrapper**

```java
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
```

- [ ] **Step 10: Commit**

```bash
git add api/src/main/java/dev/slang/api/
git commit -m "feat: add idiomatic Java API wrappers with AutoCloseable and builders"
```

---

## Task 10: Idiomatic API End-to-End Tests

**Files:**
- Create: `api/src/test/java/dev/slang/api/GlobalSessionTest.java`
- Create: `api/src/test/java/dev/slang/api/CompilationTest.java`

- [ ] **Step 1: Write GlobalSession test**

```java
package dev.slang.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlobalSessionTest {

    @Test
    void createAndGetBuildTag() {
        try (var global = GlobalSession.create()) {
            String tag = global.getBuildTagString();
            assertNotNull(tag);
            assertFalse(tag.isEmpty());
            System.out.println("Slang version: " + tag);
        }
    }

    @Test
    void findProfile() {
        try (var global = GlobalSession.create()) {
            int spirv = global.findProfile("spirv_1_5");
            assertTrue(spirv >= 0);
        }
    }
}
```

- [ ] **Step 2: Write compilation test**

```java
package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompilationTest {

    private static final String SIMPLE_COMPUTE_SHADER = """
        [shader("compute")]
        [numthreads(1, 1, 1)]
        void computeMain(uint3 tid : SV_DispatchThreadID)
        {
        }
        """;

    @Test
    void compileToSpirv() {
        try (var global = GlobalSession.create()) {
            int profile = global.findProfile("spirv_1_5");

            try (var session = global.createSession(
                    new SessionDescBuilder().addTarget(
                        new TargetDescBuilder()
                            .format(CompileTarget.SPIRV)
                            .profile(profile)))) {

                var module = session.loadModuleFromSourceString(
                    "test", "test.slang", SIMPLE_COMPUTE_SHADER);
                var entryPoint = module.findAndCheckEntryPoint("computeMain", Stage.COMPUTE);
                var composite = session.createCompositeComponentType(module, entryPoint);
                var linked = composite.link();

                try (var spirv = linked.getEntryPointCode(0, 0)) {
                    byte[] bytes = spirv.toByteArray();
                    assertTrue(bytes.length > 0);

                    // SPIRV magic number
                    int magic = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8)
                        | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
                    assertEquals(0x07230203, magic);
                }

                linked.close();
                composite.close();
                entryPoint.close();
                module.close();
            }
        }
    }

    @Test
    void compileToGLSL() {
        try (var global = GlobalSession.create()) {
            try (var session = global.createSession(
                    new SessionDescBuilder().addTarget(
                        new TargetDescBuilder().format(CompileTarget.GLSL)))) {

                var module = session.loadModuleFromSourceString(
                    "test-glsl", "test.slang", SIMPLE_COMPUTE_SHADER);
                var entryPoint = module.findAndCheckEntryPoint("computeMain", Stage.COMPUTE);
                var composite = session.createCompositeComponentType(module, entryPoint);
                var linked = composite.link();

                try (var glsl = linked.getEntryPointCode(0, 0)) {
                    String code = new String(glsl.toByteArray());
                    assertTrue(code.contains("#version") || code.contains("void main"),
                        "GLSL output should contain GLSL code");
                    System.out.println("GLSL:\n" + code);
                }

                linked.close();
                composite.close();
                entryPoint.close();
                module.close();
            }
        }
    }

    @Test
    void invalidShaderThrows() {
        try (var global = GlobalSession.create()) {
            try (var session = global.createSession(
                    new SessionDescBuilder().addTarget(
                        new TargetDescBuilder().format(CompileTarget.SPIRV)))) {

                assertThrows(RuntimeException.class, () -> {
                    session.loadModuleFromSourceString(
                        "bad", "bad.slang", "this is not valid slang code!!!");
                });
            }
        }
    }
}
```

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add api/src/test/
git commit -m "test: add idiomatic API end-to-end compilation tests"
```

---

## Task 11: Final Verification + Cleanup

- [ ] **Step 1: Run full build and all tests**

Run: `./gradlew clean build test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Verify no warnings**

Run: `./gradlew compileJava 2>&1 | grep -i warn` (or check build output)
Expected: No warnings (or only expected FFM preview warnings).

- [ ] **Step 3: Commit any final fixes**

```bash
git add -A
git commit -m "chore: final cleanup and verification"
```
