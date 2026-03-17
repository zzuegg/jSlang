package dev.slang.bindings.raw;

import dev.slang.bindings.COMObject;
import java.lang.foreign.MemorySegment;

public class ISlangUnknown extends COMObject {
    public ISlangUnknown(MemorySegment self) {
        super(self);
    }
}
