package dev.slang.jme;

import com.jme3.shader.UniformBinding;
import com.jme3.shader.VarType;
import dev.slang.api.reflect.ParameterReflection;
import dev.slang.api.reflect.ProgramLayout;
import dev.slang.api.reflect.TypeLayoutReflection;
import dev.slang.api.reflect.TypeReflection;
import dev.slang.bindings.raw.SlangReflection;

import java.util.*;

public class ReflectionMapper {

    public record MatParamMapping(String name, VarType varType) {}
    public record DefineMapping(String paramName, String defineName, VarType defineType) {}

    private static final Set<String> WORLD_PARAM_NAMES;
    static {
        var names = new HashSet<String>();
        for (UniformBinding b : UniformBinding.values()) {
            names.add(b.name());
        }
        WORLD_PARAM_NAMES = Collections.unmodifiableSet(names);
    }

    public List<MatParamMapping> extractParameters(ProgramLayout layout) {
        var result = new ArrayList<MatParamMapping>();
        for (var param : layout.parameters()) {
            String name = param.name();
            if (name == null || WORLD_PARAM_NAMES.contains(name)) continue;

            var type = param.type();
            int kind = type.kind();

            // Skip sampler states — they're implicit with textures in jME
            if (kind == SlangReflection.TYPE_KIND_SAMPLER_STATE) continue;

            VarType varType = mapType(type, param.typeLayout());
            if (varType != null) {
                result.add(new MatParamMapping(name, varType));
            }
        }
        return result;
    }

    public VarType mapType(TypeReflection type, TypeLayoutReflection typeLayout) {
        int kind = type.kind();

        return switch (kind) {
            case SlangReflection.TYPE_KIND_SCALAR -> mapScalar(type, typeLayout);
            case SlangReflection.TYPE_KIND_VECTOR -> mapVector(typeLayout);
            case SlangReflection.TYPE_KIND_MATRIX -> mapMatrix(typeLayout);
            case SlangReflection.TYPE_KIND_RESOURCE -> mapResource(type);
            case SlangReflection.TYPE_KIND_CONSTANT_BUFFER -> VarType.UniformBufferObject;
            case SlangReflection.TYPE_KIND_SHADER_STORAGE_BUFFER -> VarType.ShaderStorageBufferObject;
            default -> null;
        };
    }

    public List<UniformBinding> extractWorldBindings(ProgramLayout layout) {
        var result = new ArrayList<UniformBinding>();
        for (var param : layout.parameters()) {
            String name = param.name();
            if (name != null && WORLD_PARAM_NAMES.contains(name)) {
                result.add(UniformBinding.valueOf(name));
            }
        }
        return result;
    }

    public Map<String, DefineMapping> extractDefines(ProgramLayout layout) {
        var result = new LinkedHashMap<String, DefineMapping>();
        for (var param : layout.parameters()) {
            String name = param.name();
            if (name == null || WORLD_PARAM_NAMES.contains(name)) continue;

            var type = param.type();
            int kind = type.kind();

            if (kind == SlangReflection.TYPE_KIND_SCALAR && isBoolType(type)) {
                result.put(name, new DefineMapping(name, "HAS_" + name.toUpperCase(), VarType.Boolean));
            } else if (kind == SlangReflection.TYPE_KIND_RESOURCE) {
                VarType vt = mapResource(type);
                if (vt != null && vt.isTextureType()) {
                    result.put(name, new DefineMapping(name, "HAS_" + name.toUpperCase(), vt));
                }
            }
        }
        return result;
    }

    private boolean isBoolType(TypeReflection type) {
        String name = type.name();
        // Slang reflection reports "bool" for bool type
        return "bool".equals(name);
    }

    private VarType mapScalar(TypeReflection type, TypeLayoutReflection typeLayout) {
        String name = type.name();
        if ("bool".equals(name)) return VarType.Boolean;
        if ("int".equals(name) || "uint".equals(name)) return VarType.Int;
        // "float", "half", or just check the uniform size
        if ("float".equals(name) || "half".equals(name)) return VarType.Float;
        // Fallback: check size
        if (typeLayout != null) {
            long size = typeLayout.uniformSize();
            if (size == 4) return VarType.Float;
        }
        return VarType.Float; // default scalar
    }

    private VarType mapVector(TypeLayoutReflection typeLayout) {
        // Slang reports vectors as kind=VECTOR with type name "vector"
        // Use the uniform size to determine dimensionality: 8=vec2, 12=vec3, 16=vec4
        if (typeLayout != null) {
            long size = typeLayout.uniformSize();
            if (size <= 8) return VarType.Vector2;
            if (size <= 12) return VarType.Vector3;
            return VarType.Vector4;
        }
        return VarType.Vector4; // default
    }

    private VarType mapMatrix(TypeLayoutReflection typeLayout) {
        // Use uniform size: mat3 = 48 (3 vec4 rows padded), mat4 = 64
        if (typeLayout != null) {
            long size = typeLayout.uniformSize();
            if (size <= 48) return VarType.Matrix3;
            return VarType.Matrix4;
        }
        return VarType.Matrix4; // default
    }

    private VarType mapResource(TypeReflection type) {
        String name = type.name();
        if (name == null) return null;
        // Slang reports resource types as "_Texture" for Texture2D, etc.
        // Use resourceShape to distinguish
        // For now, check if it's a texture resource by name patterns
        // Slang may report "_Texture" for all texture types
        if (name.contains("Texture") || name.equals("_Texture")) {
            // Try to determine texture dimension from resourceShape
            int shape = type.resourceShape();
            // Slang resource shape constants:
            // 1=Texture1D, 2=Texture2D, 3=Texture3D, 4=TextureCube, 6=Texture2DArray
            return switch (shape & 0xF) { // mask off flags
                case 1 -> VarType.Texture2D; // Texture1D mapped to 2D
                case 2 -> VarType.Texture2D;
                case 3 -> VarType.Texture3D;
                case 4 -> VarType.TextureCubeMap;
                case 6 -> VarType.TextureArray;
                default -> VarType.Texture2D; // fallback
            };
        }
        if (name.startsWith("RWStructuredBuffer")) return VarType.ShaderStorageBufferObject;
        if (name.contains("RWTexture")) return VarType.Image2D;
        return null;
    }
}
