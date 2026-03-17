# SlangBindings Design Spec

## Overview

Java bindings for the Slang shader compiler using Java 26's Foreign Function & Memory (FFM) API. Provides comprehensive access to Slang's compilation, reflection, diagnostics, and type system APIs through hand-written FFM downcalls with idiomatic Java wrappers on top.

## Build System

- Gradle Kotlin DSL
- Java 26 target
- Multi-module project (bindings + api)

## Architecture

Three layers:

### 1. Raw Bindings (`bindings/raw`)

Direct FFM `MethodHandle` downcalls matching Slang's COM-style vtable layout. One Java class per Slang interface.

**COM vtable dispatch:** Slang interfaces inherit from `ISlangUnknown`. Each object has a vtable pointer at offset 0. We read the vtable, compute function pointer offsets per method, and create downcall handles via `Linker.downcallHandle()`.

**Mapped interfaces:**

| Slang Interface | Raw Java Class |
|---|---|
| `ISlangUnknown` | `ISlangUnknown` |
| `IGlobalSession` | `IGlobalSession` |
| `ISession` | `ISession` |
| `IModule` | `IModule` |
| `IEntryPoint` | `IEntryPoint` |
| `IComponentType` | `IComponentType` |
| `IBlob` | `IBlob` |
| `ITypeLayoutReflection` | `ITypeLayoutReflection` |
| `IVariableLayoutReflection` | `IVariableLayoutReflection` |

**Enums:** Slang C enums mapped to Java enums with `int` values (e.g., `SlangCompileTarget`, `SlangSourceLanguage`, `SlangStage`, `SlangProfileID`).

**Structs:** Mapped via `MemoryLayout`/`StructLayout` (e.g., `SessionDesc`, `TargetDesc`, `PreprocessorMacroDesc`, `CompilerOptionEntry`).

### 2. Native Loader (`bindings/natives`)

- Detects `os.name` + `os.arch` at runtime
- Extracts platform-specific Slang binary from JAR resources to temp directory
- Loads via `SymbolLookup.libraryLookup()`
- Platforms: `linux-x86_64`, `windows-x86_64`, `macos-aarch64`
- Gradle task downloads Slang release binaries from `shader-slang/slang` GitHub releases at build time

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
- `DiagnosticSink` collects warnings/errors as `List<Diagnostic>`

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
- `DiagnosticSink` aggregates all warnings/errors during compilation
- Each `Diagnostic` has: severity, message, file path, line, column

## Testing Strategy

- Unit tests with bundled `.slang` test shader files
- Compile to SPIRV/HLSL/GLSL, verify non-empty output blobs
- Reflection tests: load known shader, assert expected parameters/bindings/types
- Native loader tests: verify correct platform detection and library extraction
- Platform CI matrix: Linux x86_64, Windows x86_64, macOS aarch64

## Dependencies

- Java 26 (FFM API)
- Gradle Kotlin DSL
- Slang shared library (bundled per-platform)
- JUnit 5 for testing
