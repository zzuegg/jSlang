package dev.slang.api.reflect;

import dev.slang.api.*;
import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import dev.slang.bindings.raw.SlangReflection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgramLayoutTest {

    private static final String SHADER = """
        struct Particle { float3 position; float mass; };
        ConstantBuffer<Particle> cb;
        StructuredBuffer<Particle> particles;
        Texture2D tex;
        SamplerState samp;

        [shader("compute")]
        [numthreads(16,1,1)]
        void csMain(uint3 tid : SV_DispatchThreadID) {}
        """;

    private ComponentType compileAndLink() throws Exception {
        var global = GlobalSession.create();
        int profile = global.findProfile("spirv_1_5");
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));
        var module = session.loadModuleFromSourceString(
            "reflect-test", "reflect-test.slang", SHADER);
        var entryPoint = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, entryPoint);
        return composite.link();
    }

    @Test
    void parametersViaWrappers() throws Exception {
        var linked = compileAndLink();
        ProgramLayout layout = linked.getLayout(0);

        List<ParameterReflection> params = layout.parameters();
        assertFalse(params.isEmpty(), "Should have global parameters");

        // Collect parameter names
        List<String> names = params.stream().map(ParameterReflection::name).toList();
        System.out.println("Global parameters: " + names);

        // The shader declares cb, particles, tex, samp as global parameters
        assertTrue(names.contains("cb"), "Should contain 'cb'");
        assertTrue(names.contains("particles"), "Should contain 'particles'");
        assertTrue(names.contains("tex"), "Should contain 'tex'");
        assertTrue(names.contains("samp"), "Should contain 'samp'");

        // Verify types
        for (var p : params) {
            TypeReflection type = p.type();
            assertNotNull(type.name(), "Parameter type should have a name: " + p.name());
            System.out.println("  " + p.name() + ": kind=" + type.kind() + " type=" + type.name());
        }

        linked.close();
    }

    @Test
    void entryPointReflection() throws Exception {
        var linked = compileAndLink();
        ProgramLayout layout = linked.getLayout(0);

        assertEquals(1, layout.entryPointCount());
        EntryPointReflection ep = layout.entryPoint(0);
        assertEquals("csMain", ep.name());
        assertEquals(Stage.COMPUTE.value(), ep.stage());

        long[] tgs = ep.threadGroupSize();
        assertEquals(16, tgs[0], "X thread group size");
        assertEquals(1, tgs[1], "Y thread group size");
        assertEquals(1, tgs[2], "Z thread group size");

        // Verify entry points list helper
        List<EntryPointReflection> eps = layout.entryPoints();
        assertEquals(1, eps.size());
        assertEquals("csMain", eps.get(0).name());

        linked.close();
    }

    @Test
    void structFieldsViaWrappers() throws Exception {
        var linked = compileAndLink();
        ProgramLayout layout = linked.getLayout(0);

        // Find the StructuredBuffer<Particle> parameter ("particles")
        ParameterReflection particlesParam = null;
        for (var p : layout.parameters()) {
            if ("particles".equals(p.name())) {
                particlesParam = p;
                break;
            }
        }
        assertNotNull(particlesParam, "Should find 'particles' parameter");

        TypeReflection type = particlesParam.type();
        assertTrue(type.isResource(), "StructuredBuffer should be a resource type");

        // Navigate to element type (Particle struct)
        TypeReflection elemType = type.elementType();
        assertNotNull(elemType, "Resource should have an element type");
        assertTrue(elemType.isStruct(), "Element type should be a struct");
        assertEquals("Particle", elemType.name());

        // Verify struct fields
        List<VariableReflection> fields = elemType.fields();
        assertEquals(2, fields.size(), "Particle has 2 fields");

        List<String> fieldNames = fields.stream().map(VariableReflection::name).toList();
        assertTrue(fieldNames.contains("position"), "Should have 'position' field");
        assertTrue(fieldNames.contains("mass"), "Should have 'mass' field");

        // Verify field types
        for (var f : fields) {
            assertNotNull(f.type().name(), "Field should have a type name: " + f.name());
            System.out.println("  Particle." + f.name() + ": " + f.type().name());
        }

        linked.close();
    }

    @Test
    void typeLayoutSizes() throws Exception {
        var linked = compileAndLink();
        ProgramLayout layout = linked.getLayout(0);

        // Find the ConstantBuffer<Particle> parameter ("cb")
        ParameterReflection cbParam = null;
        for (var p : layout.parameters()) {
            if ("cb".equals(p.name())) {
                cbParam = p;
                break;
            }
        }
        assertNotNull(cbParam, "Should find 'cb' parameter");

        TypeLayoutReflection typeLayout = cbParam.typeLayout();
        assertNotNull(typeLayout);

        // The element type layout should be the Particle struct layout
        TypeLayoutReflection elemLayout = typeLayout.elementTypeLayout();
        assertNotNull(elemLayout, "ConstantBuffer should have element type layout");

        long uniformSize = elemLayout.uniformSize();
        System.out.println("Particle uniform size: " + uniformSize);
        // Particle has float3 (12 bytes) + float (4 bytes) = 16 bytes
        assertTrue(uniformSize > 0, "Uniform size should be positive");
        assertEquals(16, uniformSize, "Particle should be 16 bytes (float3 + float)");

        // Check field count through layout
        int fieldCount = elemLayout.fieldCount();
        assertEquals(2, fieldCount, "Particle layout should have 2 fields");

        // Verify field layout offsets
        for (int i = 0; i < fieldCount; i++) {
            ParameterReflection fieldParam = elemLayout.field(i);
            long offset = fieldParam.bindingOffset(SlangReflection.PARAMETER_CATEGORY_UNIFORM);
            System.out.println("  field[" + i + "] " + fieldParam.name()
                + " offset=" + offset);
        }

        linked.close();
    }
}
