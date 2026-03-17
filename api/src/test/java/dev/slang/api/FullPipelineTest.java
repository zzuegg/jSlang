package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import dev.slang.bindings.raw.SlangReflection;
import org.junit.jupiter.api.Test;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;

class FullPipelineTest {

    private static final String SHADER = """
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
    void fullPipelineSpirvAndHlslAndGlsl() throws Exception {
        System.out.println("================================================================================");
        System.out.println("  SLANG INPUT");
        System.out.println("================================================================================");
        System.out.println(SHADER);

        var global = GlobalSession.create();
        int spirvProfile = global.findProfile("spirv_1_5");

        // --- SPIRV ---
        {
            var session = global.createSession(
                new SessionDescBuilder().addTarget(
                    new TargetDescBuilder().format(CompileTarget.SPIRV).profile(spirvProfile)));

            var module = session.loadModuleFromSourceString("demo", "demo.slang", SHADER);
            var ep = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
            var composite = session.createCompositeComponentType(module, ep);
            var linked = composite.link();

            try (var blob = linked.getEntryPointCode(0, 0)) {
                byte[] bytes = blob.toByteArray();
                int magic = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8)
                    | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
                assertEquals(0x07230203, magic);
                System.out.println("================================================================================");
                System.out.println("  SPIRV OUTPUT (" + bytes.length + " bytes, magic=0x" + Integer.toHexString(magic) + ")");
                System.out.println("================================================================================");
                System.out.println("  [" + bytes.length + " bytes of SPIRV binary]");
            }

            // --- REFLECTION ---
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment layout = linked.raw().getLayout(arena, 0);

                System.out.println();
                System.out.println("================================================================================");
                System.out.println("  REFLECTION");
                System.out.println("================================================================================");

                // Entry points
                int epCount = SlangReflection.getEntryPointCount(layout);
                System.out.println();
                System.out.println("Entry Points (" + epCount + "):");
                for (int i = 0; i < epCount; i++) {
                    MemorySegment epRef = SlangReflection.getEntryPointByIndex(layout, i);
                    String name = SlangReflection.getEntryPointName(epRef);
                    int stage = SlangReflection.getEntryPointStage(epRef);
                    long[] tgs = SlangReflection.getComputeThreadGroupSize(arena, epRef);
                    System.out.println("  [" + i + "] " + name
                        + " (stage=" + stageName(stage) + ")"
                        + (stage == Stage.COMPUTE.value()
                            ? " [numthreads(" + tgs[0] + ", " + tgs[1] + ", " + tgs[2] + ")]"
                            : ""));
                }

                // Global parameters with bindings
                int paramCount = SlangReflection.getParameterCount(layout);
                System.out.println();
                System.out.println("Global Parameters (" + paramCount + "):");
                System.out.println(String.format("  %-15s %-20s %-8s %-8s %-8s %-8s",
                    "Name", "Type", "Binding", "Space", "Set", "Category"));
                System.out.println("  " + "-".repeat(75));

                for (int i = 0; i < paramCount; i++) {
                    MemorySegment param = SlangReflection.getParameterByIndex(layout, i);
                    MemorySegment variable = SlangReflection.getVariable(param);
                    String name = SlangReflection.getVariableName(variable);
                    MemorySegment type = SlangReflection.getVariableType(variable);
                    int kind = SlangReflection.getTypeKind(type);
                    String typeName = SlangReflection.getTypeName(type);

                    long dtSlot = SlangReflection.getOffset(param,
                        SlangReflection.PARAMETER_CATEGORY_DESCRIPTOR_TABLE_SLOT);
                    long space = SlangReflection.getBindingSpace(param,
                        SlangReflection.PARAMETER_CATEGORY_DESCRIPTOR_TABLE_SLOT);

                    String cat = resourceCategory(kind, type);

                    System.out.println(String.format("  %-15s %-20s %-8d %-8d %-8s %-8s",
                        name, typeName, dtSlot, space, "0", cat));
                }

                // Struct layouts
                System.out.println();
                System.out.println("Struct Layouts:");
                for (int i = 0; i < paramCount; i++) {
                    MemorySegment param = SlangReflection.getParameterByIndex(layout, i);
                    MemorySegment variable = SlangReflection.getVariable(param);
                    String name = SlangReflection.getVariableName(variable);
                    MemorySegment type = SlangReflection.getVariableType(variable);
                    int kind = SlangReflection.getTypeKind(type);
                    MemorySegment typeLayout = SlangReflection.getTypeLayout(param);

                    if (kind == SlangReflection.TYPE_KIND_CONSTANT_BUFFER) {
                        printStructLayout(name + " (ConstantBuffer)", typeLayout, true);
                    } else if (kind == SlangReflection.TYPE_KIND_RESOURCE) {
                        String typeName = SlangReflection.getTypeName(type);
                        MemorySegment elemType = SlangReflection.getElementType(type);
                        if (!elemType.equals(MemorySegment.NULL)) {
                            int elemKind = SlangReflection.getTypeKind(elemType);
                            if (elemKind == SlangReflection.TYPE_KIND_STRUCT) {
                                MemorySegment elemLayout = SlangReflection.getElementTypeLayout(typeLayout);
                                if (!elemLayout.equals(MemorySegment.NULL)) {
                                    String elemName = SlangReflection.getTypeName(elemType);
                                    printStructLayout(name + " (" + typeName + "<" + elemName + ">)", elemLayout, false);
                                }
                            }
                        }
                    }
                }
            }

            linked.close();
            composite.close();
            session.close();
        }

        // --- HLSL ---
        {
            var session = global.createSession(
                new SessionDescBuilder().addTarget(
                    new TargetDescBuilder().format(CompileTarget.HLSL)));

            var module = session.loadModuleFromSourceString("demo-hlsl", "demo.slang", SHADER);
            var ep = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
            var composite = session.createCompositeComponentType(module, ep);
            var linked = composite.link();

            try (var blob = linked.getEntryPointCode(0, 0)) {
                String code = new String(blob.toByteArray());
                System.out.println();
                System.out.println("================================================================================");
                System.out.println("  HLSL OUTPUT (" + code.length() + " chars)");
                System.out.println("================================================================================");
                System.out.println(code);
            }

            linked.close();
            composite.close();
            session.close();
        }

        // --- GLSL ---
        {
            var session = global.createSession(
                new SessionDescBuilder().addTarget(
                    new TargetDescBuilder().format(CompileTarget.GLSL)));

            var module = session.loadModuleFromSourceString("demo-glsl", "demo.slang", SHADER);
            var ep = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
            var composite = session.createCompositeComponentType(module, ep);
            var linked = composite.link();

            try (var blob = linked.getEntryPointCode(0, 0)) {
                String code = new String(blob.toByteArray());
                System.out.println("================================================================================");
                System.out.println("  GLSL OUTPUT (" + code.length() + " chars)");
                System.out.println("================================================================================");
                System.out.println(code);
            }

            linked.close();
            composite.close();
            session.close();
        }

        global.close();
    }

    private void printStructLayout(String label, MemorySegment typeLayout, boolean isConstantBuffer) {
        MemorySegment innerLayout = isConstantBuffer
            ? SlangReflection.getElementTypeLayout(typeLayout)
            : typeLayout;
        if (innerLayout.equals(MemorySegment.NULL)) return;

        int fieldCount = SlangReflection.getTypeLayoutFieldCount(innerLayout);
        long structSize = SlangReflection.getTypeLayoutSize(innerLayout,
            SlangReflection.PARAMETER_CATEGORY_UNIFORM);
        int alignment = SlangReflection.getTypeLayoutAlignment(innerLayout,
            SlangReflection.PARAMETER_CATEGORY_UNIFORM);

        System.out.println();
        System.out.println("  " + label + ":");
        System.out.println("    total size = " + structSize + " bytes, alignment = " + alignment);
        System.out.println(String.format("    %-15s %-12s %-8s %-8s", "Field", "Type", "Offset", "Size"));
        System.out.println("    " + "-".repeat(50));

        for (int f = 0; f < fieldCount; f++) {
            MemorySegment fieldLayout = SlangReflection.getTypeLayoutFieldByIndex(innerLayout, f);
            MemorySegment fieldVar = SlangReflection.getVariable(fieldLayout);
            String fieldName = SlangReflection.getVariableName(fieldVar);
            long offset = SlangReflection.getOffset(fieldLayout,
                SlangReflection.PARAMETER_CATEGORY_UNIFORM);
            MemorySegment fieldTypeLayout = SlangReflection.getTypeLayout(fieldLayout);
            long size = SlangReflection.getTypeLayoutSize(fieldTypeLayout,
                SlangReflection.PARAMETER_CATEGORY_UNIFORM);
            MemorySegment fieldType = SlangReflection.getVariableType(fieldVar);
            String fieldTypeName = SlangReflection.getTypeName(fieldType);

            System.out.println(String.format("    %-15s %-12s %-8d %-8d", fieldName, fieldTypeName, offset, size));
        }
    }

    private static String stageName(int stage) {
        return switch (stage) {
            case 0 -> "NONE";
            case 1 -> "VERTEX";
            case 2 -> "HULL";
            case 3 -> "DOMAIN";
            case 4 -> "GEOMETRY";
            case 5 -> "FRAGMENT";
            case 6 -> "COMPUTE";
            default -> "UNKNOWN(" + stage + ")";
        };
    }

    private static String resourceCategory(int kind, MemorySegment type) {
        return switch (kind) {
            case 6 -> "CBV";
            case 7 -> {
                int shape = SlangReflection.getResourceShape(type);
                int access = SlangReflection.getTypeKind(type); // approximate
                String typeName = SlangReflection.getTypeName(type);
                if (typeName != null && typeName.startsWith("RW")) yield "UAV";
                if (typeName != null && typeName.equals("SamplerState")) yield "Sampler";
                yield "SRV";
            }
            case 8 -> "Sampler";
            default -> "Other";
        };
    }
}
