package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

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

    public TypeReflection type() { return new TypeReflection(SlangReflection.getTypeLayoutType(raw)); }

    public TypeLayoutReflection elementTypeLayout() {
        MemorySegment e = SlangReflection.getElementTypeLayout(raw);
        return e.equals(MemorySegment.NULL) ? null : new TypeLayoutReflection(e);
    }

    public int bindingRangeCount() { return SlangReflection.getBindingRangeCount(raw); }
    public int descriptorSetCount() { return SlangReflection.getDescriptorSetCount(raw); }

    public MemorySegment raw() { return raw; }
}
