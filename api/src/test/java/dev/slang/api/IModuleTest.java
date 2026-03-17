package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IModuleTest {

    private static final String TWO_ENTRY_POINT_SHADER = """
        struct Foo { float x; };

        [shader("compute")]
        [numthreads(1,1,1)]
        void main1(uint3 tid : SV_DispatchThreadID) {}

        [shader("compute")]
        [numthreads(1,1,1)]
        void main2(uint3 tid : SV_DispatchThreadID) {}
        """;

    private Session createSession(GlobalSession global) throws SlangException {
        int profile = global.findProfile("spirv_1_5");
        return global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(profile)));
    }

    @Test
    void getDefinedEntryPoints() throws Exception {
        var global = GlobalSession.create();
        var session = createSession(global);

        var module = session.loadModuleFromSourceString(
            "test-ep", "test.slang", TWO_ENTRY_POINT_SHADER);

        assertEquals(2, module.getDefinedEntryPointCount());

        var ep0 = module.getDefinedEntryPoint(0);
        assertNotNull(ep0);

        var ep1 = module.getDefinedEntryPoint(1);
        assertNotNull(ep1);

        session.close();
        global.close();
    }

    @Test
    void moduleNameAndIdentity() throws Exception {
        var global = GlobalSession.create();
        var session = createSession(global);

        var module = session.loadModuleFromSourceString(
            "test-identity", "test.slang", TWO_ENTRY_POINT_SHADER);

        assertEquals("test-identity", module.getName());

        String identity = module.getUniqueIdentity();
        // Modules loaded from source strings may or may not have a unique identity
        System.out.println("Module unique identity: " + identity);

        session.close();
        global.close();
    }

    @Test
    void disassemble() throws Exception {
        var global = GlobalSession.create();
        var session = createSession(global);

        var module = session.loadModuleFromSourceString(
            "test-disasm", "test.slang", TWO_ENTRY_POINT_SHADER);

        String disasm = module.disassemble();
        assertNotNull(disasm);
        assertFalse(disasm.isEmpty(), "Disassembly should not be empty");
        System.out.println("Disassembly (first 500 chars):\n"
            + disasm.substring(0, Math.min(500, disasm.length())));

        session.close();
        global.close();
    }

    @Test
    void serialize() throws Exception {
        var global = GlobalSession.create();
        var session = createSession(global);

        var module = session.loadModuleFromSourceString(
            "test-serialize", "test.slang", TWO_ENTRY_POINT_SHADER);

        byte[] bytes = module.serialize();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Serialized module should not be empty");
        System.out.println("Serialized module size: " + bytes.length + " bytes");

        session.close();
        global.close();
    }
}
