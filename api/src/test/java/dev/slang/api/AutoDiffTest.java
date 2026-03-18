package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoDiffTest {

    // A differentiable function computing a simple lighting model.
    // Slang generates the backward derivative (gradient) automatically.
    private static final String DIFFERENTIABLE_SHADER = """
        // Mark functions as [Differentiable] — Slang will auto-generate derivatives

        [Differentiable]
        float schlickFresnel(float cosTheta)
        {
            float x = 1.0 - cosTheta;
            float x2 = x * x;
            return x2 * x2 * x;  // (1 - cosTheta)^5
        }

        [Differentiable]
        float ggxDistribution(float NdotH, float roughness)
        {
            float a = roughness * roughness;
            float a2 = a * a;
            float d = NdotH * NdotH * (a2 - 1.0) + 1.0;
            return a2 / (3.14159265 * d * d);
        }

        [Differentiable]
        float brdf(float3 normal, float3 lightDir, float3 viewDir,
                   float roughness, float metallic)
        {
            float3 H = normalize(lightDir + viewDir);
            float NdotL = max(dot(normal, lightDir), 0.001);
            float NdotV = max(dot(normal, viewDir), 0.001);
            float NdotH = max(dot(normal, H), 0.0);
            float VdotH = max(dot(viewDir, H), 0.0);

            float D = ggxDistribution(NdotH, roughness);
            float F = lerp(0.04, 1.0, schlickFresnel(VdotH));
            float specular = D * F;

            float diffuse = (1.0 - metallic) * NdotL / 3.14159265;

            return diffuse + specular * NdotL;
        }

        // Forward-mode: propagate tangents through the BRDF
        // All params of a [Differentiable] function become DifferentialPair in fwd_diff
        [shader("compute")]
        [numthreads(1, 1, 1)]
        void forwardDiff(
            uint3 tid : SV_DispatchThreadID,
            uniform float roughness,
            uniform float metallic,
            uniform RWStructuredBuffer<float> output)
        {
            float3 N = float3(0, 1, 0);
            float3 L = normalize(float3(1, 1, 0));
            float3 V = normalize(float3(0, 1, 1));

            // All params need DifferentialPair — zero derivative = held constant
            var dpN = diffPair(N, float3(0));
            var dpL = diffPair(L, float3(0));
            var dpV = diffPair(V, float3(0));
            var dpRoughness = diffPair(roughness, 1.0);  // d/dRoughness = 1
            var dpMetallic = diffPair(metallic, 0.0);    // held constant

            var result = fwd_diff(brdf)(dpN, dpL, dpV, dpRoughness, dpMetallic);

            output[0] = result.p;  // primal: the BRDF value
            output[1] = result.d;  // derivative: dBRDF/dRoughness
        }

        // Backward-mode: compute gradients of loss w.r.t. material parameters
        [shader("compute")]
        [numthreads(1, 1, 1)]
        void backwardDiff(
            uint3 tid : SV_DispatchThreadID,
            uniform float roughness,
            uniform float metallic,
            uniform RWStructuredBuffer<float> output)
        {
            float3 N = float3(0, 1, 0);
            float3 L = normalize(float3(1, 1, 0));
            float3 V = normalize(float3(0, 1, 1));

            var dpN = diffPair(N, float3(0));
            var dpL = diffPair(L, float3(0));
            var dpV = diffPair(V, float3(0));
            var dpRoughness = diffPair(roughness, 0.0);
            var dpMetallic = diffPair(metallic, 0.0);

            // Propagate gradient=1.0 backward through brdf
            bwd_diff(brdf)(dpN, dpL, dpV, dpRoughness, dpMetallic, 1.0);

            output[0] = dpRoughness.d; // dLoss/dRoughness
            output[1] = dpMetallic.d;  // dLoss/dMetallic
        }
        """;

    @Test
    void forwardModeDifferentiation() throws Exception {
        System.out.println("================================================================================");
        System.out.println("  SLANG AUTOMATIC DIFFERENTIATION — Forward Mode");
        System.out.println("================================================================================");
        System.out.println(DIFFERENTIABLE_SHADER);

        var global = GlobalSession.create();

        // Compile to HLSL to see the generated derivative code
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.HLSL)));

        var module = session.loadModuleFromSourceString(
            "autodiff", "autodiff.slang", DIFFERENTIABLE_SHADER);

        // Forward diff entry point
        var fwdEP = module.findAndCheckEntryPoint("forwardDiff", Stage.COMPUTE);
        var fwdComposite = session.createCompositeComponentType(module, fwdEP);
        var fwdLinked = fwdComposite.link();

        try (var hlsl = fwdLinked.getEntryPointCode(0, 0)) {
            String code = new String(hlsl.toByteArray());
            System.out.println("================================================================================");
            System.out.println("  FORWARD DIFF → HLSL (" + code.length() + " chars)");
            System.out.println("================================================================================");
            System.out.println(code);

            // The generated code should contain derivative computation functions
            assertTrue(code.contains("forwardDiff") || code.contains("fwd_diff"),
                "Should contain forward diff entry point");
            assertTrue(code.length() > 500,
                "Auto-diff code should be substantial (generated derivatives)");
        }

        fwdLinked.close();
        fwdComposite.close();

        // Backward diff entry point
        var bwdEP = module.findAndCheckEntryPoint("backwardDiff", Stage.COMPUTE);
        var bwdComposite = session.createCompositeComponentType(module, bwdEP);
        var bwdLinked = bwdComposite.link();

        try (var hlsl = bwdLinked.getEntryPointCode(0, 0)) {
            String code = new String(hlsl.toByteArray());
            System.out.println("================================================================================");
            System.out.println("  BACKWARD DIFF → HLSL (" + code.length() + " chars)");
            System.out.println("================================================================================");
            System.out.println(code);

            assertTrue(code.contains("backwardDiff") || code.contains("bwd_diff"),
                "Should contain backward diff entry point");
            assertTrue(code.length() > 500,
                "Backward diff code should be substantial (generated gradients)");
        }

        bwdLinked.close();
        bwdComposite.close();
        session.close();

        // Also compile to SPIRV to verify it produces valid binary
        var spirvSession = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder()
                    .format(CompileTarget.SPIRV)
                    .profile(global.findProfile("spirv_1_5"))));

        var spirvModule = spirvSession.loadModuleFromSourceString(
            "autodiff-spirv", "autodiff.slang", DIFFERENTIABLE_SHADER);
        var spirvFwd = spirvModule.findAndCheckEntryPoint("forwardDiff", Stage.COMPUTE);
        var spirvBwd = spirvModule.findAndCheckEntryPoint("backwardDiff", Stage.COMPUTE);

        // Forward SPIRV
        var fwdComp = spirvSession.createCompositeComponentType(spirvModule, spirvFwd);
        var fwdLink = fwdComp.link();
        try (var blob = fwdLink.getEntryPointCode(0, 0)) {
            byte[] bytes = blob.toByteArray();
            int magic = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
            assertEquals(0x07230203, magic);
            System.out.println("\nForward diff SPIRV: " + bytes.length + " bytes (valid)");
        }
        fwdLink.close();
        fwdComp.close();

        // Backward SPIRV
        var bwdComp = spirvSession.createCompositeComponentType(spirvModule, spirvBwd);
        var bwdLink = bwdComp.link();
        try (var blob = bwdLink.getEntryPointCode(0, 0)) {
            byte[] bytes = blob.toByteArray();
            int magic = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
            assertEquals(0x07230203, magic);
            System.out.println("Backward diff SPIRV: " + bytes.length + " bytes (valid)");
        }
        bwdLink.close();
        bwdComp.close();

        spirvSession.close();
        global.close();
    }
}
