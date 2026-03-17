package dev.slang.bindings.raw;

import dev.slang.bindings.SlangResult;
import java.lang.foreign.*;

public class ISession extends ISlangUnknown {

    private static final int SLOT_LOAD_MODULE = 4;
    private static final int SLOT_CREATE_COMPOSITE_COMPONENT_TYPE = 6;
    private static final int SLOT_LOAD_MODULE_FROM_SOURCE_STRING = 20;

    private static final FunctionDescriptor DESC_LOAD_MODULE =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_CREATE_COMPOSITE =
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private static final FunctionDescriptor DESC_LOAD_MODULE_FROM_SOURCE_STRING =
        FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    public ISession(MemorySegment self) {
        super(self);
    }

    public IModule loadModule(Arena arena, String moduleName) {
        MemorySegment nameStr = arena.allocateUtf8String(moduleName);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment modulePtr = (MemorySegment) getHandle(SLOT_LOAD_MODULE, DESC_LOAD_MODULE)
                .invokeExact(self, nameStr, outDiag);
            if (modulePtr.equals(MemorySegment.NULL)) {
                throw new RuntimeException("loadModule returned null for: " + moduleName);
            }
            return new IModule(modulePtr);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("loadModule failed", t); }
    }

    public IModule loadModuleFromSourceString(Arena arena, String moduleName, String path, String source) {
        MemorySegment nameStr = arena.allocateUtf8String(moduleName);
        MemorySegment pathStr = arena.allocateUtf8String(path);
        MemorySegment sourceStr = arena.allocateUtf8String(source);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            MemorySegment modulePtr = (MemorySegment)
                getHandle(SLOT_LOAD_MODULE_FROM_SOURCE_STRING, DESC_LOAD_MODULE_FROM_SOURCE_STRING)
                    .invokeExact(self, nameStr, pathStr, sourceStr, outDiag);
            if (modulePtr.equals(MemorySegment.NULL)) {
                MemorySegment diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
                String diagMsg = "";
                if (!diagPtr.equals(MemorySegment.NULL)) {
                    ISlangBlob diagBlob = new ISlangBlob(diagPtr);
                    diagMsg = new String(diagBlob.toByteArray());
                    diagBlob.release();
                }
                throw new RuntimeException("loadModuleFromSourceString failed: " + diagMsg);
            }
            return new IModule(modulePtr);
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("loadModuleFromSourceString failed", t); }
    }

    public IComponentType createCompositeComponentType(Arena arena, IComponentType... components) {
        MemorySegment array = arena.allocate(ValueLayout.ADDRESS.byteSize() * components.length);
        for (int i = 0; i < components.length; i++) {
            array.set(ValueLayout.ADDRESS, (long) i * ValueLayout.ADDRESS.byteSize(), components[i].ptr());
        }
        MemorySegment outComposite = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment outDiag = arena.allocate(ValueLayout.ADDRESS);
        try {
            int result = (int) getHandle(SLOT_CREATE_COMPOSITE_COMPONENT_TYPE, DESC_CREATE_COMPOSITE)
                .invokeExact(self, array, (long) components.length, outComposite, outDiag);
            SlangResult.check(result, "createCompositeComponentType");
            return new IComponentType(outComposite.get(ValueLayout.ADDRESS, 0));
        } catch (RuntimeException e) { throw e;
        } catch (Throwable t) { throw new RuntimeException("createCompositeComponentType failed", t); }
    }
}
