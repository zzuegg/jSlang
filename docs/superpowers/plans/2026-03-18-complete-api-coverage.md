# Complete API Coverage Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring all Slang COM interface bindings, idiomatic API wrappers, and reflection wrappers to full coverage.

**Architecture:** Extend existing three-layer architecture (raw FFM → native loader → idiomatic API). Add missing vtable slots to raw interfaces, expose them through idiomatic wrappers, wrap SlangReflection in proper Java objects, switch from RuntimeException to checked SlangException, and add SessionDesc options (search paths, macros).

**Tech Stack:** Java 21 (FFM preview API), Gradle Kotlin DSL, Slang 2026.4.2, JUnit 5

**Java 21 preview API notes:** Use `arena.allocateUtf8String()` not `allocateFrom()`, `seg.getUtf8String(0)` not `getString(0)`, `arena.allocate(layout.byteSize() * n)` for arrays.

**COM ownership rule discovered during development:** The session owns modules/entrypoints it creates. Do NOT call release() on modules/entrypoints before releasing the session — it causes heap corruption. Release output objects (blobs, linked composites) explicitly, then release the session.

**Test execution:** All tests require `LD_LIBRARY_PATH=/tmp/slang-latest/lib` to find libslang.so.

---

## Task 1: IModule — Module Introspection Methods

**Files:**
- Modify: `bindings/src/main/java/dev/slang/bindings/raw/IModule.java`
- Modify: `api/src/main/java/dev/slang/api/Module.java`
- Create: `bindings/src/test/java/dev/slang/bindings/raw/IModuleTest.java`

Add slots 18-24, 26-29 to IModule.

- [ ] **Step 1: Add vtable slot constants and descriptors to IModule**

```java
// Add to IModule.java after existing slots
private static final int SLOT_GET_DEFINED_ENTRY_POINT_COUNT = 18;
private static final int SLOT_GET_DEFINED_ENTRY_POINT = 19;
private static final int SLOT_SERIALIZE = 20;
private static final int SLOT_WRITE_TO_FILE = 21;
private static final int SLOT_GET_FILE_PATH = 23;
private static final int SLOT_GET_UNIQUE_IDENTITY = 24;
private static final int SLOT_GET_DEPENDENCY_FILE_COUNT = 26;
private static final int SLOT_GET_DEPENDENCY_FILE_PATH = 27;
private static final int SLOT_GET_MODULE_REFLECTION = 28;
private static final int SLOT_DISASSEMBLE = 29;

private static final FunctionDescriptor DESC_GET_DEFINED_ENTRY_POINT_COUNT =
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

private static final FunctionDescriptor DESC_GET_DEFINED_ENTRY_POINT =
    FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

private static final FunctionDescriptor DESC_SERIALIZE =
    FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS);

private static final FunctionDescriptor DESC_WRITE_TO_FILE =
    FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS);

private static final FunctionDescriptor DESC_GET_FILE_PATH =
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

private static final FunctionDescriptor DESC_GET_UNIQUE_IDENTITY =
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

private static final FunctionDescriptor DESC_GET_DEPENDENCY_FILE_COUNT =
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

private static final FunctionDescriptor DESC_GET_DEPENDENCY_FILE_PATH =
    FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

private static final FunctionDescriptor DESC_GET_MODULE_REFLECTION =
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

private static final FunctionDescriptor DESC_DISASSEMBLE =
    FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS);
```

- [ ] **Step 2: Implement the methods**

```java
public int getDefinedEntryPointCount() {
    try {
        return (int) getHandle(SLOT_GET_DEFINED_ENTRY_POINT_COUNT, DESC_GET_DEFINED_ENTRY_POINT_COUNT)
            .invokeExact(self);
    } catch (Throwable t) { throw new RuntimeException("getDefinedEntryPointCount failed", t); }
}

public IEntryPoint getDefinedEntryPoint(Arena arena, int index) {
    MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
    try {
        int result = (int) getHandle(SLOT_GET_DEFINED_ENTRY_POINT, DESC_GET_DEFINED_ENTRY_POINT)
            .invokeExact(self, index, out);
        SlangResult.check(result, "IModule::getDefinedEntryPoint");
        return new IEntryPoint(out.get(ValueLayout.ADDRESS, 0));
    } catch (RuntimeException e) { throw e;
    } catch (Throwable t) { throw new RuntimeException("getDefinedEntryPoint failed", t); }
}

public ISlangBlob serialize(Arena arena) {
    MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
    try {
        int result = (int) getHandle(SLOT_SERIALIZE, DESC_SERIALIZE)
            .invokeExact(self, out);
        SlangResult.check(result, "IModule::serialize");
        return new ISlangBlob(out.get(ValueLayout.ADDRESS, 0));
    } catch (RuntimeException e) { throw e;
    } catch (Throwable t) { throw new RuntimeException("serialize failed", t); }
}

public void writeToFile(Arena arena, String fileName) {
    MemorySegment nameStr = arena.allocateUtf8String(fileName);
    try {
        int result = (int) getHandle(SLOT_WRITE_TO_FILE, DESC_WRITE_TO_FILE)
            .invokeExact(self, nameStr);
        SlangResult.check(result, "IModule::writeToFile");
    } catch (RuntimeException e) { throw e;
    } catch (Throwable t) { throw new RuntimeException("writeToFile failed", t); }
}

public String getFilePath() {
    try {
        MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_FILE_PATH, DESC_GET_FILE_PATH)
            .invokeExact(self);
        if (ptr.equals(MemorySegment.NULL)) return null;
        return ptr.reinterpret(256).getUtf8String(0);
    } catch (Throwable t) { throw new RuntimeException("getFilePath failed", t); }
}

public String getUniqueIdentity() {
    try {
        MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_UNIQUE_IDENTITY, DESC_GET_UNIQUE_IDENTITY)
            .invokeExact(self);
        if (ptr.equals(MemorySegment.NULL)) return null;
        return ptr.reinterpret(256).getUtf8String(0);
    } catch (Throwable t) { throw new RuntimeException("getUniqueIdentity failed", t); }
}

public int getDependencyFileCount() {
    try {
        return (int) getHandle(SLOT_GET_DEPENDENCY_FILE_COUNT, DESC_GET_DEPENDENCY_FILE_COUNT)
            .invokeExact(self);
    } catch (Throwable t) { throw new RuntimeException("getDependencyFileCount failed", t); }
}

public String getDependencyFilePath(int index) {
    try {
        MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_DEPENDENCY_FILE_PATH, DESC_GET_DEPENDENCY_FILE_PATH)
            .invokeExact(self, index);
        if (ptr.equals(MemorySegment.NULL)) return null;
        return ptr.reinterpret(1024).getUtf8String(0);
    } catch (Throwable t) { throw new RuntimeException("getDependencyFilePath failed", t); }
}

public MemorySegment getModuleReflection() {
    try {
        return (MemorySegment) getHandle(SLOT_GET_MODULE_REFLECTION, DESC_GET_MODULE_REFLECTION)
            .invokeExact(self);
    } catch (Throwable t) { throw new RuntimeException("getModuleReflection failed", t); }
}

public ISlangBlob disassemble(Arena arena) {
    MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
    try {
        int result = (int) getHandle(SLOT_DISASSEMBLE, DESC_DISASSEMBLE)
            .invokeExact(self, out);
        SlangResult.check(result, "IModule::disassemble");
        return new ISlangBlob(out.get(ValueLayout.ADDRESS, 0));
    } catch (RuntimeException e) { throw e;
    } catch (Throwable t) { throw new RuntimeException("disassemble failed", t); }
}
```

- [ ] **Step 3: Add idiomatic wrappers to Module.java**

```java
// Add to Module.java
public int getDefinedEntryPointCount() {
    return rawModule.getDefinedEntryPointCount();
}

public EntryPoint getDefinedEntryPoint(int index) {
    try (Arena arena = Arena.ofConfined()) {
        return new EntryPoint(rawModule.getDefinedEntryPoint(arena, index));
    }
}

public byte[] serialize() {
    try (Arena arena = Arena.ofConfined()) {
        try (var blob = new Blob(rawModule.serialize(arena))) {
            return blob.toByteArray();
        }
    }
}

public void writeToFile(String fileName) {
    try (Arena arena = Arena.ofConfined()) {
        rawModule.writeToFile(arena, fileName);
    }
}

public String getFilePath() {
    return rawModule.getFilePath();
}

public String getUniqueIdentity() {
    return rawModule.getUniqueIdentity();
}

public int getDependencyFileCount() {
    return rawModule.getDependencyFileCount();
}

public String getDependencyFilePath(int index) {
    return rawModule.getDependencyFilePath(index);
}

public String disassemble() {
    try (Arena arena = Arena.ofConfined()) {
        try (var blob = new Blob(rawModule.disassemble(arena))) {
            return new String(blob.toByteArray());
        }
    }
}
```

- [ ] **Step 4: Write tests**

```java
// IModuleTest.java
package dev.slang.bindings.raw;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import dev.slang.bindings.structs.SessionDesc;
import dev.slang.bindings.structs.TargetDesc;
import dev.slang.api.*;
import org.junit.jupiter.api.Test;
import java.lang.foreign.*;
import static org.junit.jupiter.api.Assertions.*;

class IModuleTest {

    private static final String SHADER = """
        struct Foo { float x; };

        [shader("compute")]
        [numthreads(1,1,1)]
        void main1(uint3 tid : SV_DispatchThreadID) {}

        [shader("compute")]
        [numthreads(1,1,1)]
        void main2(uint3 tid : SV_DispatchThreadID) {}
        """;

    @Test
    void getDefinedEntryPoints() {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.SPIRV)
                    .profile(global.findProfile("spirv_1_5"))));

        var module = session.loadModuleFromSourceString("ep-test", "test.slang", SHADER);
        int count = module.getDefinedEntryPointCount();
        assertEquals(2, count, "Should have 2 entry points");

        var ep0 = module.getDefinedEntryPoint(0);
        var ep1 = module.getDefinedEntryPoint(1);
        assertNotNull(ep0);
        assertNotNull(ep1);

        session.close();
        global.close();
    }

    @Test
    void moduleNameAndIdentity() {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.SPIRV)));

        var module = session.loadModuleFromSourceString("my-mod", "my-mod.slang", SHADER);
        assertEquals("my-mod", module.getName());
        assertNotNull(module.getUniqueIdentity());
        System.out.println("Name: " + module.getName());
        System.out.println("Identity: " + module.getUniqueIdentity());

        session.close();
        global.close();
    }

    @Test
    void disassemble() {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.SPIRV)));

        var module = session.loadModuleFromSourceString("dis-test", "test.slang", SHADER);
        String ir = module.disassemble();
        assertNotNull(ir);
        assertFalse(ir.isEmpty(), "Disassembly should not be empty");
        System.out.println("IR (first 500 chars):\n" + ir.substring(0, Math.min(500, ir.length())));

        session.close();
        global.close();
    }

    @Test
    void serialize() {
        var global = GlobalSession.create();
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.SPIRV)));

        var module = session.loadModuleFromSourceString("ser-test", "test.slang", SHADER);
        byte[] data = module.serialize();
        assertTrue(data.length > 0, "Serialized data should not be empty");
        System.out.println("Serialized module: " + data.length + " bytes");

        session.close();
        global.close();
    }
}
```

- [ ] **Step 5: Run tests**

Run: `LD_LIBRARY_PATH=/tmp/slang-latest/lib ./gradlew :bindings:test --tests "dev.slang.bindings.raw.IModuleTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add bindings/src/main/java/dev/slang/bindings/raw/IModule.java api/src/main/java/dev/slang/api/Module.java bindings/src/test/java/dev/slang/bindings/raw/IModuleTest.java
git commit -m "feat: add IModule introspection — entry points, serialize, disassemble, paths"
```

---

## Task 2: IComponentType — Remaining Methods

**Files:**
- Modify: `bindings/src/main/java/dev/slang/bindings/raw/IComponentType.java`
- Modify: `api/src/main/java/dev/slang/api/ComponentType.java`
- Create: `bindings/src/test/java/dev/slang/bindings/raw/IComponentTypeTest.java`

Add slots 3, 5, 7-9, 11-13, 15-16.

- [ ] **Step 1: Add slot constants and descriptors**

```java
// Add to IComponentType.java
private static final int SLOT_GET_SESSION = 3;
private static final int SLOT_GET_SPECIALIZATION_PARAM_COUNT = 5;
private static final int SLOT_GET_RESULT_AS_FILE_SYSTEM = 7;
private static final int SLOT_GET_ENTRY_POINT_HASH = 8;
private static final int SLOT_SPECIALIZE = 9;
private static final int SLOT_GET_ENTRY_POINT_HOST_CALLABLE = 11;
private static final int SLOT_RENAME_ENTRY_POINT = 12;
private static final int SLOT_LINK_WITH_OPTIONS = 13;
private static final int SLOT_GET_TARGET_METADATA = 15;
private static final int SLOT_GET_ENTRY_POINT_METADATA = 16;

private static final FunctionDescriptor DESC_GET_SESSION =
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
private static final FunctionDescriptor DESC_GET_SPECIALIZATION_PARAM_COUNT =
    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
private static final FunctionDescriptor DESC_GET_ENTRY_POINT_HASH =
    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
private static final FunctionDescriptor DESC_SPECIALIZE =
    FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS);
private static final FunctionDescriptor DESC_RENAME_ENTRY_POINT =
    FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
```

- [ ] **Step 2: Implement methods**

```java
public MemorySegment getSession() {
    try {
        return (MemorySegment) getHandle(SLOT_GET_SESSION, DESC_GET_SESSION).invokeExact(self);
    } catch (Throwable t) { throw new RuntimeException("getSession failed", t); }
}

public long getSpecializationParamCount() {
    try {
        return (long) getHandle(SLOT_GET_SPECIALIZATION_PARAM_COUNT, DESC_GET_SPECIALIZATION_PARAM_COUNT)
            .invokeExact(self);
    } catch (Throwable t) { throw new RuntimeException("getSpecializationParamCount failed", t); }
}

public ISlangBlob getEntryPointHash(Arena arena, long entryPointIndex, long targetIndex) {
    MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
    try {
        getHandle(SLOT_GET_ENTRY_POINT_HASH, DESC_GET_ENTRY_POINT_HASH)
            .invokeExact(self, entryPointIndex, targetIndex, out);
        MemorySegment ptr = out.get(ValueLayout.ADDRESS, 0);
        return ptr.equals(MemorySegment.NULL) ? null : new ISlangBlob(ptr);
    } catch (Throwable t) { throw new RuntimeException("getEntryPointHash failed", t); }
}

public IComponentType specialize(Arena arena, MemorySegment specializationArgs, long argCount) {
    MemorySegment outSpecialized = arena.allocate(ValueLayout.ADDRESS);
    MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
    try {
        int result = (int) getHandle(SLOT_SPECIALIZE, DESC_SPECIALIZE)
            .invokeExact(self, specializationArgs, argCount, outSpecialized, outDiag);
        SlangResult.check(result, "IComponentType::specialize");
        return new IComponentType(outSpecialized.get(ValueLayout.ADDRESS, 0));
    } catch (RuntimeException e) { throw e;
    } catch (Throwable t) { throw new RuntimeException("specialize failed", t); }
}

public IComponentType renameEntryPoint(Arena arena, String newName) {
    MemorySegment nameStr = arena.allocateUtf8String(newName);
    MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
    try {
        int result = (int) getHandle(SLOT_RENAME_ENTRY_POINT, DESC_RENAME_ENTRY_POINT)
            .invokeExact(self, nameStr, out);
        SlangResult.check(result, "IComponentType::renameEntryPoint");
        return new IComponentType(out.get(ValueLayout.ADDRESS, 0));
    } catch (RuntimeException e) { throw e;
    } catch (Throwable t) { throw new RuntimeException("renameEntryPoint failed", t); }
}
```

- [ ] **Step 3: Add idiomatic wrappers to ComponentType.java**

```java
public long getSpecializationParamCount() {
    return raw.getSpecializationParamCount();
}

public ComponentType renameEntryPoint(String newName) {
    try (Arena arena = Arena.ofConfined()) {
        return new ComponentType(raw.renameEntryPoint(arena, newName));
    }
}
```

- [ ] **Step 4: Write tests**

Test getSpecializationParamCount, renameEntryPoint, getSession. Test that getEntryPointHash returns non-null after linking.

- [ ] **Step 5: Run tests**

Run: `LD_LIBRARY_PATH=/tmp/slang-latest/lib ./gradlew :bindings:test --tests "dev.slang.bindings.raw.IComponentTypeTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add bindings/src/main/java/dev/slang/bindings/raw/IComponentType.java api/src/main/java/dev/slang/api/ComponentType.java bindings/src/test/java/dev/slang/bindings/raw/IComponentTypeTest.java
git commit -m "feat: add IComponentType — specialize, rename, hash, session access"
```

---

## Task 3: IEntryPoint — getFunctionReflection

**Files:**
- Modify: `bindings/src/main/java/dev/slang/bindings/raw/IEntryPoint.java`
- Modify: `api/src/main/java/dev/slang/api/EntryPoint.java`

- [ ] **Step 1: Implement getFunctionReflection**

```java
// In IEntryPoint.java
private static final FunctionDescriptor DESC_GET_FUNCTION_REFLECTION =
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

public MemorySegment getFunctionReflection() {
    try {
        return (MemorySegment) getHandle(SLOT_GET_FUNCTION_REFLECTION, DESC_GET_FUNCTION_REFLECTION)
            .invokeExact(self);
    } catch (Throwable t) { throw new RuntimeException("getFunctionReflection failed", t); }
}
```

- [ ] **Step 2: Add test** (extend IModuleTest with entry point reflection test)

- [ ] **Step 3: Commit**

```bash
git add bindings/src/main/java/dev/slang/bindings/raw/IEntryPoint.java
git commit -m "feat: add IEntryPoint::getFunctionReflection"
```

---

## Task 4: ISession — Module Management and Type Queries

**Files:**
- Modify: `bindings/src/main/java/dev/slang/bindings/raw/ISession.java`
- Modify: `api/src/main/java/dev/slang/api/Session.java`
- Create: `bindings/src/test/java/dev/slang/bindings/raw/ISessionTest.java`

Add slots 3, 5, 17-20.

- [ ] **Step 1: Add slot constants**

```java
private static final int SLOT_GET_GLOBAL_SESSION = 3;
private static final int SLOT_LOAD_MODULE_FROM_SOURCE = 5;
private static final int SLOT_GET_LOADED_MODULE_COUNT = 17;
private static final int SLOT_GET_LOADED_MODULE = 18;
private static final int SLOT_IS_BINARY_MODULE_UP_TO_DATE = 19;

private static final FunctionDescriptor DESC_GET_GLOBAL_SESSION =
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
private static final FunctionDescriptor DESC_LOAD_MODULE_FROM_SOURCE =
    FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS);
private static final FunctionDescriptor DESC_GET_LOADED_MODULE_COUNT =
    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
private static final FunctionDescriptor DESC_GET_LOADED_MODULE =
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
```

- [ ] **Step 2: Implement methods**

```java
public MemorySegment getGlobalSession() {
    try {
        return (MemorySegment) getHandle(SLOT_GET_GLOBAL_SESSION, DESC_GET_GLOBAL_SESSION)
            .invokeExact(self);
    } catch (Throwable t) { throw new RuntimeException("getGlobalSession failed", t); }
}

public IModule loadModuleFromSource(Arena arena, String moduleName, String path,
        ISlangBlob sourceBlob) {
    MemorySegment nameStr = arena.allocateUtf8String(moduleName);
    MemorySegment pathStr = arena.allocateUtf8String(path);
    MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
    try {
        MemorySegment modulePtr = (MemorySegment) getHandle(SLOT_LOAD_MODULE_FROM_SOURCE, DESC_LOAD_MODULE_FROM_SOURCE)
            .invokeExact(self, nameStr, pathStr, sourceBlob.ptr(), outDiag);
        if (modulePtr.equals(MemorySegment.NULL)) {
            MemorySegment diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
            String diagMsg = "";
            if (!diagPtr.equals(MemorySegment.NULL)) {
                ISlangBlob diagBlob = new ISlangBlob(diagPtr);
                diagMsg = new String(diagBlob.toByteArray());
                diagBlob.release();
            }
            throw new RuntimeException("loadModuleFromSource failed: " + diagMsg);
        }
        return new IModule(modulePtr);
    } catch (RuntimeException e) { throw e;
    } catch (Throwable t) { throw new RuntimeException("loadModuleFromSource failed", t); }
}

public long getLoadedModuleCount() {
    try {
        return (long) getHandle(SLOT_GET_LOADED_MODULE_COUNT, DESC_GET_LOADED_MODULE_COUNT)
            .invokeExact(self);
    } catch (Throwable t) { throw new RuntimeException("getLoadedModuleCount failed", t); }
}

public IModule getLoadedModule(long index) {
    try {
        MemorySegment ptr = (MemorySegment) getHandle(SLOT_GET_LOADED_MODULE, DESC_GET_LOADED_MODULE)
            .invokeExact(self, index);
        return new IModule(ptr);
    } catch (Throwable t) { throw new RuntimeException("getLoadedModule failed", t); }
}
```

- [ ] **Step 3: Add idiomatic wrappers to Session.java**

```java
public long getLoadedModuleCount() {
    return raw.getLoadedModuleCount();
}

public Module getLoadedModule(int index) {
    return new Module(raw.getLoadedModule(index));
}
```

- [ ] **Step 4: Write tests**

Test getLoadedModuleCount after loading modules, getLoadedModule retrieval, getGlobalSession non-null.

- [ ] **Step 5: Run tests and commit**

```bash
git commit -m "feat: add ISession — module management, loaded module queries"
```

---

## Task 5: IGlobalSession — Compiler Configuration and Capabilities

**Files:**
- Modify: `bindings/src/main/java/dev/slang/bindings/raw/IGlobalSession.java`
- Modify: `api/src/main/java/dev/slang/api/GlobalSession.java`
- Create: `bindings/src/test/java/dev/slang/bindings/raw/IGlobalSessionExtTest.java`

Add slots 5-7, 9-12, 14, 17-18, 22, 25.

- [ ] **Step 1: Add slot constants and descriptors**

```java
private static final int SLOT_SET_DOWNSTREAM_COMPILER_PATH = 5;
private static final int SLOT_SET_DOWNSTREAM_COMPILER_PRELUDE = 6;
private static final int SLOT_GET_DOWNSTREAM_COMPILER_PRELUDE = 7;
private static final int SLOT_SET_DEFAULT_DOWNSTREAM_COMPILER = 9;
private static final int SLOT_GET_DEFAULT_DOWNSTREAM_COMPILER = 10;
private static final int SLOT_SET_LANGUAGE_PRELUDE = 11;
private static final int SLOT_GET_LANGUAGE_PRELUDE = 12;
private static final int SLOT_ADD_BUILTINS = 14;
private static final int SLOT_CHECK_COMPILE_TARGET_SUPPORT = 17;
private static final int SLOT_CHECK_PASS_THROUGH_SUPPORT = 18;
private static final int SLOT_FIND_CAPABILITY = 22;
private static final int SLOT_GET_COMPILER_ELAPSED_TIME = 25;
```

- [ ] **Step 2: Implement methods**

Key methods: `checkCompileTargetSupport`, `checkPassThroughSupport`, `findCapability`, `getCompilerElapsedTime`, `setDownstreamCompilerPath`, `setLanguagePrelude/getLanguagePrelude`.

- [ ] **Step 3: Add idiomatic wrappers**

```java
// GlobalSession.java
public boolean isCompileTargetSupported(CompileTarget target) {
    // checkCompileTargetSupport returns SlangResult; >= 0 means supported
    ...
}

public double[] getCompilerElapsedTime() {
    // Returns [totalTime, downstreamTime]
    ...
}
```

- [ ] **Step 4: Write tests**

Test checkCompileTargetSupport for SPIRV (should pass), findCapability, getCompilerElapsedTime.

- [ ] **Step 5: Run tests and commit**

```bash
git commit -m "feat: add IGlobalSession — compiler config, target support, capabilities"
```

---

## Task 6: SessionDesc — Search Paths, Macros, and Flags

**Files:**
- Create: `bindings/src/main/java/dev/slang/bindings/structs/PreprocessorMacroDesc.java`
- Modify: `bindings/src/main/java/dev/slang/bindings/structs/SessionDesc.java`
- Modify: `api/src/main/java/dev/slang/api/SessionDescBuilder.java`
- Create: `api/src/test/java/dev/slang/api/SessionConfigTest.java`

- [ ] **Step 1: Create PreprocessorMacroDesc**

```java
package dev.slang.bindings.structs;

import java.lang.foreign.*;

public final class PreprocessorMacroDesc {
    private PreprocessorMacroDesc() {}

    // struct { char* name; char* value; }
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("name"),
        ValueLayout.ADDRESS.withName("value")
    );

    public static MemorySegment allocate(Arena arena, String name, String value) {
        MemorySegment seg = arena.allocate(LAYOUT);
        seg.set(ValueLayout.ADDRESS, 0, arena.allocateUtf8String(name));
        seg.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize(),
            arena.allocateUtf8String(value));
        return seg;
    }
}
```

- [ ] **Step 2: Update SessionDesc with field offsets for search paths, macros, flags**

Update `SessionDesc.allocate()` to accept search paths, macros, and flags. Document the struct offsets more precisely.

- [ ] **Step 3: Extend SessionDescBuilder**

```java
// Add to SessionDescBuilder
private final List<String> searchPaths = new ArrayList<>();
private final Map<String, String> macros = new LinkedHashMap<>();
private int flags = 0;

public SessionDescBuilder addSearchPath(String path) {
    searchPaths.add(path);
    return this;
}

public SessionDescBuilder addMacro(String name, String value) {
    macros.put(name, value);
    return this;
}

public SessionDescBuilder flags(int flags) {
    this.flags = flags;
    return this;
}
```

Update `build()` to write search paths, macros, and flags into the SessionDesc struct at the correct offsets.

- [ ] **Step 4: Write tests**

Test preprocessor macros by compiling a shader that uses `#ifdef` and verifying different output based on macro presence. Test search paths by loading a module by name (requires a file on disk).

- [ ] **Step 5: Run tests and commit**

```bash
git commit -m "feat: add SessionDesc search paths, preprocessor macros, and flags"
```

---

## Task 7: SlangException — Checked Exception Conversion

**Files:**
- Modify: `api/src/main/java/dev/slang/api/SlangException.java`
- Modify: `api/src/main/java/dev/slang/api/GlobalSession.java`
- Modify: `api/src/main/java/dev/slang/api/Session.java`
- Modify: `api/src/main/java/dev/slang/api/Module.java`
- Modify: `api/src/main/java/dev/slang/api/ComponentType.java`
- Modify: All API test files to handle checked exceptions

- [ ] **Step 1: Add static factory to SlangException**

```java
public class SlangException extends Exception {
    private final int resultCode;

    public SlangException(int resultCode, String message) {
        super(message + " (SlangResult: 0x" + Integer.toHexString(resultCode) + ")");
        this.resultCode = resultCode;
    }

    public SlangException(String message) {
        super(message);
        this.resultCode = Integer.MIN_VALUE;
    }

    public SlangException(String message, Throwable cause) {
        super(message, cause);
        this.resultCode = Integer.MIN_VALUE;
    }

    public int getResultCode() { return resultCode; }
}
```

- [ ] **Step 2: Add `throws SlangException` to all API wrapper methods**

Methods that call Slang and can fail should throw `SlangException` instead of `RuntimeException`. This includes: `GlobalSession.createSession`, `Session.loadModule`, `Session.loadModuleFromSourceString`, `Module.findEntryPoint`, `Module.findAndCheckEntryPoint`, `ComponentType.link`, `ComponentType.getEntryPointCode`, `ComponentType.getTargetCode`, `Module.serialize`, `Module.writeToFile`.

- [ ] **Step 3: Update all test files to handle checked exceptions**

Either add `throws SlangException` to test methods or wrap in try-catch where exception testing is needed.

- [ ] **Step 4: Run all tests**

Run: `LD_LIBRARY_PATH=/tmp/slang-latest/lib ./gradlew clean test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: convert API layer to checked SlangException"
```

---

## Task 8: Idiomatic Reflection Wrappers

**Files:**
- Create: `api/src/main/java/dev/slang/api/reflect/ProgramLayout.java`
- Create: `api/src/main/java/dev/slang/api/reflect/EntryPointReflection.java`
- Create: `api/src/main/java/dev/slang/api/reflect/ParameterReflection.java`
- Create: `api/src/main/java/dev/slang/api/reflect/TypeReflection.java`
- Create: `api/src/main/java/dev/slang/api/reflect/TypeLayoutReflection.java`
- Create: `api/src/main/java/dev/slang/api/reflect/VariableReflection.java`
- Create: `api/src/test/java/dev/slang/api/reflect/ProgramLayoutTest.java`

Wrap raw `MemorySegment` + `SlangReflection` static calls into proper Java objects.

- [ ] **Step 1: Create TypeReflection**

```java
package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.ArrayList;

public class TypeReflection {
    private final MemorySegment raw;

    public TypeReflection(MemorySegment raw) { this.raw = raw; }

    public int kind() { return SlangReflection.getTypeKind(raw); }
    public String name() { return SlangReflection.getTypeName(raw); }
    public int fieldCount() { return SlangReflection.getTypeFieldCount(raw); }
    public VariableReflection field(int index) {
        return new VariableReflection(SlangReflection.getTypeFieldByIndex(raw, index));
    }
    public int resourceShape() { return SlangReflection.getResourceShape(raw); }
    public TypeReflection elementType() {
        MemorySegment e = SlangReflection.getElementType(raw);
        return e.equals(MemorySegment.NULL) ? null : new TypeReflection(e);
    }
    public boolean isStruct() { return kind() == SlangReflection.TYPE_KIND_STRUCT; }
    public boolean isResource() { return kind() == SlangReflection.TYPE_KIND_RESOURCE; }
    public boolean isScalar() { return kind() == SlangReflection.TYPE_KIND_SCALAR; }

    public List<VariableReflection> fields() {
        int n = fieldCount();
        var list = new ArrayList<VariableReflection>(n);
        for (int i = 0; i < n; i++) list.add(field(i));
        return list;
    }

    public MemorySegment raw() { return raw; }
}
```

- [ ] **Step 2: Create VariableReflection**

```java
package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;

public class VariableReflection {
    private final MemorySegment raw;

    public VariableReflection(MemorySegment raw) { this.raw = raw; }

    public String name() { return SlangReflection.getVariableName(raw); }
    public TypeReflection type() {
        return new TypeReflection(SlangReflection.getVariableType(raw));
    }
    public MemorySegment raw() { return raw; }
}
```

- [ ] **Step 3: Create ParameterReflection (wraps VariableLayout)**

```java
package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;

public class ParameterReflection {
    private final MemorySegment raw; // VariableLayout pointer

    public ParameterReflection(MemorySegment raw) { this.raw = raw; }

    public String name() {
        return SlangReflection.getVariableName(SlangReflection.getVariable(raw));
    }
    public TypeReflection type() {
        return new TypeReflection(SlangReflection.getVariableType(SlangReflection.getVariable(raw)));
    }
    public TypeLayoutReflection typeLayout() {
        return new TypeLayoutReflection(SlangReflection.getTypeLayout(raw));
    }
    public long bindingOffset(int category) { return SlangReflection.getOffset(raw, category); }
    public long bindingSpace(int category) { return SlangReflection.getBindingSpace(raw, category); }
    public String semanticName() { return SlangReflection.getSemanticName(raw); }
    public int semanticIndex() { return SlangReflection.getSemanticIndex(raw); }
    public MemorySegment raw() { return raw; }
}
```

- [ ] **Step 4: Create TypeLayoutReflection**

```java
package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.ArrayList;

public class TypeLayoutReflection {
    private final MemorySegment raw;

    public TypeLayoutReflection(MemorySegment raw) { this.raw = raw; }

    public long size(int category) { return SlangReflection.getTypeLayoutSize(raw, category); }
    public long stride(int category) { return SlangReflection.getTypeLayoutStride(raw, category); }
    public int alignment(int category) { return SlangReflection.getTypeLayoutAlignment(raw, category); }
    public long uniformSize() { return size(SlangReflection.PARAMETER_CATEGORY_UNIFORM); }

    public int fieldCount() { return SlangReflection.getTypeLayoutFieldCount(raw); }
    public ParameterReflection field(int index) {
        return new ParameterReflection(SlangReflection.getTypeLayoutFieldByIndex(raw, index));
    }
    public List<ParameterReflection> fields() {
        int n = fieldCount();
        var list = new ArrayList<ParameterReflection>(n);
        for (int i = 0; i < n; i++) list.add(field(i));
        return list;
    }

    public TypeReflection type() {
        return new TypeReflection(SlangReflection.getTypeLayoutType(raw));
    }
    public TypeLayoutReflection elementTypeLayout() {
        MemorySegment e = SlangReflection.getElementTypeLayout(raw);
        return e.equals(MemorySegment.NULL) ? null : new TypeLayoutReflection(e);
    }

    public int bindingRangeCount() { return SlangReflection.getBindingRangeCount(raw); }
    public int descriptorSetCount() { return SlangReflection.getDescriptorSetCount(raw); }

    public MemorySegment raw() { return raw; }
}
```

- [ ] **Step 5: Create EntryPointReflection**

```java
package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.*;
import java.util.List;
import java.util.ArrayList;

public class EntryPointReflection {
    private final MemorySegment raw;

    public EntryPointReflection(MemorySegment raw) { this.raw = raw; }

    public String name() { return SlangReflection.getEntryPointName(raw); }
    public int stage() { return SlangReflection.getEntryPointStage(raw); }
    public int parameterCount() { return SlangReflection.getEntryPointParameterCount(raw); }
    public ParameterReflection parameter(int index) {
        return new ParameterReflection(SlangReflection.getEntryPointParameterByIndex(raw, index));
    }
    public List<ParameterReflection> parameters() {
        int n = parameterCount();
        var list = new ArrayList<ParameterReflection>(n);
        for (int i = 0; i < n; i++) list.add(parameter(i));
        return list;
    }

    public long[] threadGroupSize() {
        try (Arena arena = Arena.ofConfined()) {
            return SlangReflection.getComputeThreadGroupSize(arena, raw);
        }
    }

    public MemorySegment raw() { return raw; }
}
```

- [ ] **Step 6: Create ProgramLayout**

```java
package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.ArrayList;

public class ProgramLayout {
    private final MemorySegment raw;

    public ProgramLayout(MemorySegment raw) { this.raw = raw; }

    public int parameterCount() { return SlangReflection.getParameterCount(raw); }
    public ParameterReflection parameter(int index) {
        return new ParameterReflection(SlangReflection.getParameterByIndex(raw, index));
    }
    public List<ParameterReflection> parameters() {
        int n = parameterCount();
        var list = new ArrayList<ParameterReflection>(n);
        for (int i = 0; i < n; i++) list.add(parameter(i));
        return list;
    }

    public int entryPointCount() { return SlangReflection.getEntryPointCount(raw); }
    public EntryPointReflection entryPoint(int index) {
        return new EntryPointReflection(SlangReflection.getEntryPointByIndex(raw, index));
    }
    public List<EntryPointReflection> entryPoints() {
        int n = entryPointCount();
        var list = new ArrayList<EntryPointReflection>(n);
        for (int i = 0; i < n; i++) list.add(entryPoint(i));
        return list;
    }

    public MemorySegment raw() { return raw; }
}
```

- [ ] **Step 7: Add `getLayout()` to idiomatic ComponentType returning ProgramLayout**

```java
// In ComponentType.java
public ProgramLayout getLayout(int targetIndex) {
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment layout = raw.getLayout(arena, targetIndex);
        return new ProgramLayout(layout);
    }
}
```

- [ ] **Step 8: Write tests**

```java
// ProgramLayoutTest.java — test using idiomatic reflection wrappers
// Verify parameters(), entryPoints(), TypeReflection fields, TypeLayoutReflection sizes
```

- [ ] **Step 9: Run all tests**

Run: `LD_LIBRARY_PATH=/tmp/slang-latest/lib ./gradlew clean test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git commit -m "feat: add idiomatic reflection wrappers — ProgramLayout, TypeReflection, etc."
```

---

## Task 9: IGlobalSession — Core/Builtin Module Management

**Files:**
- Modify: `bindings/src/main/java/dev/slang/bindings/raw/IGlobalSession.java`

Add slots 13, 15-16, 19-21, 23-24, 26-31. These are less commonly used but complete the interface.

- [ ] **Step 1: Add remaining slot constants and methods**

Slots: createCompileRequest(13), setSharedLibraryLoader(15), getSharedLibraryLoader(16), compileCoreModule(19), loadCoreModule(20), saveCoreModule(21), setDownstreamCompilerForTransition(23), getDownstreamCompilerForTransition(24), setSPIRVCoreGrammar(26), parseCommandLineArguments(27), getSessionDescDigest(28), compileBuiltinModule(29), loadBuiltinModule(30), saveBuiltinModule(31).

- [ ] **Step 2: Write tests for testable methods** (compileCoreModule, parseCommandLineArguments)

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add remaining IGlobalSession methods — core modules, transitions, CLI parsing"
```

---

## Task 10: ISession — Remaining Type System Methods

**Files:**
- Modify: `bindings/src/main/java/dev/slang/bindings/raw/ISession.java`

Add slots 7-16, 21-23.

- [ ] **Step 1: Add type system methods**

Slots: specializeType(7), getTypeLayout(8), getContainerType(9), getDynamicType(10), getTypeRTTIMangledName(11), getTypeConformanceWitnessMangledName(12), getTypeConformanceWitnessSequentialID(13), createCompileRequest(14), createTypeConformanceComponentType(15), loadModuleFromIRBlob(16), getDynamicObjectRTTIBytes(21), loadModuleInfoFromIRBlob(22), getDeclSourceLocation(23).

- [ ] **Step 2: Write tests for specializeType, getTypeLayout**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add remaining ISession methods — type system, IR loading, specialization"
```

---

## Task 11: Final Verification

- [ ] **Step 1: Run full clean build and all tests**

Run: `LD_LIBRARY_PATH=/tmp/slang-latest/lib ./gradlew clean build test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Commit any fixes**

- [ ] **Step 3: Push**

```bash
git push
```
