# SlangBindings Design Spec

## Overview

Java bindings for the Slang shader compiler using Java 26's Foreign Function & Memory (FFM) API. Provides comprehensive access to Slang's compilation, reflection, diagnostics, and type system APIs through hand-written FFM downcalls with idiomatic Java wrappers on top.

**Target Slang version:** Latest stable release (pinned in `gradle.properties`). A version compatibility check via `spGetBuildVersionString()` runs at library load time.

## Build System

- Gradle Kotlin DSL
- Java 26 target
- Two Gradle modules:
  - `bindings` — raw FFM bindings + native loader
  - `api` — idiomatic Java wrappers (depends on `bindings`)

## Architecture

Three layers:

### 1. Raw Bindings (`bindings/raw`)

Direct FFM `MethodHandle` downcalls matching Slang's COM-style vtable layout. One Java class per Slang interface.

#### COM Vtable Dispatch

Slang uses single-inheritance COM layout. Each object starts with a pointer to its vtable, which is a contiguous array of function pointers. Inherited methods come first (in declaration order), followed by the interface's own methods.

**`ISlangUnknown` base methods (slots 0-2):**

| Slot | Method | Signature |
|------|--------|-----------|
| 0 | `queryInterface` | `(MemorySegment self, MemorySegment guid, MemorySegment outObject) → int` |
| 1 | `addRef` | `(MemorySegment self) → int` |
| 2 | `release` | `(MemorySegment self) → int` |

For derived interfaces, methods start at slot `parentMethodCount`. For example, `IGlobalSession` extends `ISlangUnknown` (3 methods), so `IGlobalSession`'s own methods start at slot 3.

**Vtable access pattern:**
```java
// Read vtable pointer from object (offset 0)
MemorySegment vtable = obj.get(ValueLayout.ADDRESS, 0);
// Read function pointer at slot N
MemorySegment fnPtr = vtable.get(ValueLayout.ADDRESS, N * ValueLayout.ADDRESS.byteSize());
// Create downcall handle
MethodHandle handle = Linker.nativeLinker().downcallHandle(fnPtr, descriptor);
```

A `COMObject` base class encapsulates this pattern, providing `getVtableSlot(int index)` and caching `MethodHandle` instances per slot.

#### Interface Inheritance

Slang uses single-inheritance COM (no diamond patterns):

```
ISlangUnknown (3 methods: slots 0-2)
├── ISlangBlob (2 methods: slots 3-4)
├── IGlobalSession (slots 3+)
├── ISession (slots 3+)
└── IComponentType (slots 3+)
    ├── IModule (inherits IComponentType methods + own)
    └── IEntryPoint (inherits IComponentType methods + own)
```

In Java, this is modeled via class inheritance: `IModule extends IComponentType extends ISlangUnknown`. Each class defines static `SLOT_*` constants for its methods. `IComponentType` methods like `link()` and `getEntryPointCode()` are callable on `IModule` and `IEntryPoint` instances through inheritance.

#### Mapped Interfaces

| Slang Interface | Raw Java Class | Extends |
|---|---|---|
| `ISlangUnknown` | `ISlangUnknown` | `COMObject` |
| `IGlobalSession` | `IGlobalSession` | `ISlangUnknown` |
| `ISession` | `ISession` | `ISlangUnknown` |
| `IComponentType` | `IComponentType` | `ISlangUnknown` |
| `IModule` | `IModule` | `IComponentType` |
| `IEntryPoint` | `IEntryPoint` | `IComponentType` |
| `ISlangBlob` | `ISlangBlob` | `ISlangUnknown` |
| `ITypeLayoutReflection` | `ITypeLayoutReflection` | — (plain pointer, not COM) |
| `IVariableLayoutReflection` | `IVariableLayoutReflection` | — (plain pointer, not COM) |

**Note:** Reflection types (`ITypeLayoutReflection`, `IVariableLayoutReflection`) are not COM objects — they are raw pointers with static-style function calls, not vtable dispatch.

#### Enums

Slang C enums mapped to Java enums with `int` values. Each enum has:
- `value()` method returning the int
- Static `fromValue(int)` lookup (returns `Optional.empty()` for unknown values)
- Flag/bitmask enums (e.g., `SlangCompileFlags`) use an `EnumSet`-style helper with bitwise OR/AND operations

#### Structs

Mapped via `MemoryLayout`/`StructLayout` with static `LAYOUT` constant and typed accessors. Allocated in caller-provided `Arena`.

### 2. Native Loader (`bindings/natives`)

- Detects `os.name` + `os.arch` at runtime
- Extracts platform-specific Slang binary from JAR resources to temp directory
- Loads via `SymbolLookup.libraryLookup()`
- Calls `spGetBuildVersionString()` to verify version compatibility
- Platforms: `linux-x86_64`, `windows-x86_64`, `macos-aarch64`
- Gradle task downloads Slang release binaries from `shader-slang/slang` GitHub releases at build time
- Temp-extracted libraries are cleaned up via JVM shutdown hook

### 3. Idiomatic API (`api/`)

Java-friendly wrappers over raw bindings.

**Packages:**

- `api.session` — `GlobalSession`, `Session` wrappers
- `api.module` — `Module`, `EntryPoint`, `ComponentType`
- `api.compile` — Compilation orchestration, target configuration
- `api.reflect` — Reflection API (parameters, types, resource layouts)
- `api.diagnostics` — Diagnostic messages, severity levels

**Design principles:**

- All wrappers implement `AutoCloseable`, calling COM `release()` on close
- Builders for `SessionDesc` and `TargetDesc` configuration
- `List`/`Optional` instead of raw pointers and null checks
- Checked `SlangException` from `SlangResult` error codes
- Diagnostics retrieved from Slang as `ISlangBlob` strings (from `getDiagnosticOutput()` on compilation results), parsed into `List<Diagnostic>`

## Memory Management (Arena Strategy)

Each `AutoCloseable` wrapper owns a **confined `Arena`** for its temporary allocations (struct descriptors, string arguments, out-pointers). The arena is closed when the wrapper is closed.

- `GlobalSession` — owns `Arena.ofConfined()`, used for `SessionDesc` allocations and similar
- `Session` — owns `Arena.ofConfined()`, scoped to the session lifetime
- Compilation results (`Blob`) — wraps the native `ISlangBlob` pointer; data is valid until `release()`. `toByteArray()` copies data to Java heap.
- Reflection pointers — valid for the lifetime of the parent `IComponentType`. No arena needed; wrapped as lightweight value objects.

**Thread safety:** `Arena.ofConfined()` restricts access to the creating thread. This matches Slang's threading model where `ISession` and compilation are single-threaded. `GlobalSession` is documented as thread-safe in Slang, but we use confined arenas per-operation regardless for simplicity. Users who need multi-threaded compilation should create separate `Session` instances per thread.

## Compilation Workflow (User-Facing)

```java
try (var global = GlobalSession.create()) {
    try (var session = global.createSession(SessionDesc.builder()
            .addTarget(TargetDesc.builder()
                .format(CompileTarget.SPIRV)
                .profile(global.findProfile("spirv_1_5"))
                .build())
            .build())) {

        var module = session.loadModule("shader.slang");
        var entryPoint = module.findEntryPoint("main");
        var program = session.createCompositeComponentType(module, entryPoint);
        var linked = program.link();

        Blob spirv = linked.getEntryPointCode(0, 0);
        byte[] bytes = spirv.toByteArray();
    }
}
```

## Error Handling

- `SlangResult` return codes converted to checked `SlangException`
- Exception includes error code enum + diagnostic message string
- Diagnostics retrieved as `ISlangBlob` strings from compilation methods, parsed into structured `Diagnostic` objects
- Each `Diagnostic` has: severity, message, file path, line, column

## Testing Strategy

- Unit tests with bundled `.slang` test shader files
- Compile to SPIRV/HLSL/GLSL, verify non-empty output blobs
- Reflection tests: load known shader, assert expected parameters/bindings/types
- Native loader tests: verify correct platform detection and library extraction
- **Error path tests:** invalid shaders, missing files, bad configuration — verify proper exceptions
- **Vtable regression tests:** call each bound method on a real Slang session to catch slot miscalculations
- **Memory leak tests:** verify `AutoCloseable` correctly releases COM refs (track `addRef`/`release` counts)
- Platform CI matrix: Linux x86_64, Windows x86_64, macOS aarch64

## Dependencies

- Java 26 (FFM API)
- Gradle Kotlin DSL
- Slang shared library (bundled per-platform, version pinned)
- JUnit 5 for testing
