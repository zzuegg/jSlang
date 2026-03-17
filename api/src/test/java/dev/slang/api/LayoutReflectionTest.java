package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import dev.slang.bindings.raw.SlangReflection;
import org.junit.jupiter.api.Test;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;

class LayoutReflectionTest {

    private static final String SHADER_WITH_BINDINGS = """
        struct Light
        {
            float3 position;
            float  radius;
            float3 color;
            float  intensity;
        };

        struct MaterialData
        {
            float4 baseColor;
            float  roughness;
            float  metallic;
            float2 padding;
        };

        ConstantBuffer<MaterialData> material;
        StructuredBuffer<Light> lights;
        RWTexture2D<float4> outputTex;
        Texture2D inputTex;
        SamplerState texSampler;

        [shader("compute")]
        [numthreads(8, 8, 1)]
        void csMain(uint3 tid : SV_DispatchThreadID)
        {
            float4 color = inputTex.SampleLevel(texSampler, float2(tid.xy) / 512.0, 0);
            MaterialData mat = material;
            Light light = lights[0];
            outputTex[tid.xy] = color * mat.baseColor * float4(light.color * light.intensity, 1.0);
        }
        """;

    @Test
    void queryBindingOffsetsAndSpaces() throws Exception {
        var global = GlobalSession.create();
        int profile = global.findProfile("spirv_1_5");

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));

        var module = session.loadModuleFromSourceString(
            "bindings", "bindings.slang", SHADER_WITH_BINDINGS);
        var entryPoint = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, entryPoint);
        var linked = composite.link();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment layout = linked.raw().getLayout(arena, 0);

            int paramCount = SlangReflection.getParameterCount(layout);
            System.out.println("=== Parameter Bindings ===");
            System.out.println("Parameter count: " + paramCount);

            for (int i = 0; i < paramCount; i++) {
                MemorySegment param = SlangReflection.getParameterByIndex(layout, i);
                MemorySegment variable = SlangReflection.getVariable(param);
                String name = SlangReflection.getVariableName(variable);
                MemorySegment type = SlangReflection.getVariableType(variable);
                int kind = SlangReflection.getTypeKind(type);
                String typeName = SlangReflection.getTypeName(type);

                System.out.println("\n  " + name + " (" + typeName + ", kind=" + kind + ")");

                // Query binding offset for various categories
                for (int cat : new int[] {
                    SlangReflection.PARAMETER_CATEGORY_DESCRIPTOR_TABLE_SLOT,
                    SlangReflection.PARAMETER_CATEGORY_CONSTANT_BUFFER,
                    SlangReflection.PARAMETER_CATEGORY_SHADER_RESOURCE,
                    SlangReflection.PARAMETER_CATEGORY_UNORDERED_ACCESS,
                    SlangReflection.PARAMETER_CATEGORY_SAMPLER_STATE,
                    SlangReflection.PARAMETER_CATEGORY_UNIFORM
                }) {
                    long offset = SlangReflection.getOffset(param, cat);
                    long space = SlangReflection.getBindingSpace(param, cat);
                    if (offset >= 0 && offset < 1000) {
                        String catName = categoryName(cat);
                        System.out.println("    " + catName + ": binding=" + offset + ", space=" + space);
                    }
                }
            }

            // Query type layout sizes for struct
            System.out.println("\n=== TypeLayout Info ===");
            for (int i = 0; i < paramCount; i++) {
                MemorySegment param = SlangReflection.getParameterByIndex(layout, i);
                MemorySegment variable = SlangReflection.getVariable(param);
                String name = SlangReflection.getVariableName(variable);
                MemorySegment typeLayout = SlangReflection.getTypeLayout(param);

                int typeLayoutKind = SlangReflection.getTypeLayoutKind(typeLayout);
                long uniformSize = SlangReflection.getTypeLayoutSize(typeLayout,
                    SlangReflection.PARAMETER_CATEGORY_UNIFORM);

                if (uniformSize > 0) {
                    System.out.println("  " + name + ": uniform size = " + uniformSize + " bytes");
                }
            }

            // Query binding ranges on the global type layout
            MemorySegment globalParamsLayout = SlangReflection.getTypeLayout(
                SlangReflection.getParameterByIndex(layout, 0));
            // Get the root type layout from the program
            System.out.println("\n=== Descriptor Sets ===");
            // Use entry point's type layout for binding range queries
            MemorySegment ep = SlangReflection.getEntryPointByIndex(layout, 0);
            int epParamCount = SlangReflection.getEntryPointParameterCount(ep);
            System.out.println("Entry point '" + SlangReflection.getEntryPointName(ep) +
                "' has " + epParamCount + " parameters");

            // Verify we got meaningful data
            assertTrue(paramCount > 0, "Should have global parameters");

            // Query struct field layouts through the TypeLayout API
            System.out.println("\n=== Struct Field Layouts ===");
            for (int i = 0; i < paramCount; i++) {
                MemorySegment param = SlangReflection.getParameterByIndex(layout, i);
                MemorySegment variable = SlangReflection.getVariable(param);
                String name = SlangReflection.getVariableName(variable);
                MemorySegment typeLayout = SlangReflection.getTypeLayout(param);

                // Check for constant buffer with struct inside
                MemorySegment innerType = SlangReflection.getTypeLayoutType(typeLayout);
                int kind = SlangReflection.getTypeKind(innerType);

                if (kind == SlangReflection.TYPE_KIND_CONSTANT_BUFFER) {
                    MemorySegment elemLayout = SlangReflection.getElementTypeLayout(typeLayout);
                    if (!elemLayout.equals(MemorySegment.NULL)) {
                        int fieldCount = SlangReflection.getTypeLayoutFieldCount(elemLayout);
                        long structSize = SlangReflection.getTypeLayoutSize(elemLayout,
                            SlangReflection.PARAMETER_CATEGORY_UNIFORM);
                        System.out.println("  " + name + " (ConstantBuffer, " + structSize + " bytes, " + fieldCount + " fields):");

                        for (int f = 0; f < fieldCount; f++) {
                            MemorySegment fieldLayout = SlangReflection.getTypeLayoutFieldByIndex(elemLayout, f);
                            MemorySegment fieldVar = SlangReflection.getVariable(fieldLayout);
                            String fieldName = SlangReflection.getVariableName(fieldVar);
                            long fieldOffset = SlangReflection.getOffset(fieldLayout,
                                SlangReflection.PARAMETER_CATEGORY_UNIFORM);
                            MemorySegment fieldTypeLayout = SlangReflection.getTypeLayout(fieldLayout);
                            long fieldSize = SlangReflection.getTypeLayoutSize(fieldTypeLayout,
                                SlangReflection.PARAMETER_CATEGORY_UNIFORM);
                            System.out.println("      " + fieldName + ": offset=" + fieldOffset + ", size=" + fieldSize);
                        }

                        // Verify MaterialData layout
                        assertTrue(structSize > 0, "Struct should have non-zero size");
                        assertTrue(fieldCount == 4, "MaterialData should have 4 fields");
                    }
                }
            }
        }

        linked.close();
        composite.close();
        session.close();
        global.close();
    }

    @Test
    void queryVaryingInputSemantics() throws Exception {
        var global = GlobalSession.create();
        int profile = global.findProfile("spirv_1_5");

        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));

        String shader = """
            struct VSInput
            {
                float3 position : POSITION;
                float3 normal   : NORMAL;
                float2 uv       : TEXCOORD0;
                float4 color    : COLOR0;
            };

            struct VSOutput
            {
                float4 sv_position : SV_Position;
                float3 normal      : NORMAL;
                float2 uv          : TEXCOORD0;
                float4 color       : COLOR0;
            };

            uniform float4x4 mvp;

            [shader("vertex")]
            VSOutput vsMain(VSInput input)
            {
                VSOutput output;
                output.sv_position = mul(mvp, float4(input.position, 1.0));
                output.normal = input.normal;
                output.uv = input.uv;
                output.color = input.color;
                return output;
            }
            """;

        var module = session.loadModuleFromSourceString(
            "varying", "varying.slang", shader);
        var entryPoint = module.findAndCheckEntryPoint("vsMain", Stage.VERTEX);
        var composite = session.createCompositeComponentType(module, entryPoint);
        var linked = composite.link();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment layout = linked.raw().getLayout(arena, 0);

            MemorySegment ep = SlangReflection.getEntryPointByIndex(layout, 0);
            int epParamCount = SlangReflection.getEntryPointParameterCount(ep);

            System.out.println("=== Vertex Input Semantics ===");
            for (int i = 0; i < epParamCount; i++) {
                MemorySegment param = SlangReflection.getEntryPointParameterByIndex(ep, i);
                MemorySegment variable = SlangReflection.getVariable(param);
                String name = SlangReflection.getVariableName(variable);
                MemorySegment type = SlangReflection.getVariableType(variable);
                int kind = SlangReflection.getTypeKind(type);

                System.out.println("  " + name + " (kind=" + kind + ")");

                // For struct parameters, check field layouts
                if (kind == SlangReflection.TYPE_KIND_STRUCT) {
                    MemorySegment typeLayout = SlangReflection.getTypeLayout(param);
                    int fieldCount = SlangReflection.getTypeLayoutFieldCount(typeLayout);
                    for (int f = 0; f < fieldCount; f++) {
                        MemorySegment fieldLayout = SlangReflection.getTypeLayoutFieldByIndex(typeLayout, f);
                        MemorySegment fieldVar = SlangReflection.getVariable(fieldLayout);
                        String fieldName = SlangReflection.getVariableName(fieldVar);
                        String semantic = SlangReflection.getSemanticName(fieldLayout);
                        int semanticIdx = SlangReflection.getSemanticIndex(fieldLayout);
                        long varyingOffset = SlangReflection.getOffset(fieldLayout,
                            SlangReflection.PARAMETER_CATEGORY_VARYING_INPUT);

                        System.out.println("    " + fieldName
                            + ": semantic=" + semantic
                            + ", index=" + semanticIdx
                            + ", location=" + varyingOffset);
                    }
                }
            }
        }

        linked.close();
        composite.close();
        session.close();
        global.close();
    }

    private static String categoryName(int cat) {
        return switch (cat) {
            case 2 -> "ConstantBuffer";
            case 3 -> "ShaderResource";
            case 4 -> "UnorderedAccess";
            case 5 -> "VaryingInput";
            case 6 -> "VaryingOutput";
            case 7 -> "SamplerState";
            case 8 -> "Uniform";
            case 9 -> "DescriptorTableSlot";
            default -> "Category(" + cat + ")";
        };
    }
}
