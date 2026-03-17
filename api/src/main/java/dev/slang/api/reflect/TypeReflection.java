package dev.slang.api.reflect;

import dev.slang.bindings.raw.SlangReflection;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

public class TypeReflection {
    private final MemorySegment raw;

    public TypeReflection(MemorySegment raw) { this.raw = raw; }

    public int kind() { return SlangReflection.getTypeKind(raw); }
    public String name() { return SlangReflection.getTypeName(raw); }
    public int fieldCount() { return SlangReflection.getTypeFieldCount(raw); }

    public VariableReflection field(int index) {
        return new VariableReflection(SlangReflection.getTypeFieldByIndex(raw, index));
    }

    public List<VariableReflection> fields() {
        int n = fieldCount();
        var list = new ArrayList<VariableReflection>(n);
        for (int i = 0; i < n; i++) list.add(field(i));
        return list;
    }

    public int resourceShape() { return SlangReflection.getResourceShape(raw); }

    public TypeReflection elementType() {
        MemorySegment e = SlangReflection.getElementType(raw);
        return e.equals(MemorySegment.NULL) ? null : new TypeReflection(e);
    }

    public boolean isStruct() { return kind() == SlangReflection.TYPE_KIND_STRUCT; }
    public boolean isResource() { return kind() == SlangReflection.TYPE_KIND_RESOURCE; }
    public boolean isScalar() { return kind() == SlangReflection.TYPE_KIND_SCALAR; }
    public boolean isVector() { return kind() == SlangReflection.TYPE_KIND_VECTOR; }
    public boolean isMatrix() { return kind() == SlangReflection.TYPE_KIND_MATRIX; }

    public MemorySegment raw() { return raw; }
}
