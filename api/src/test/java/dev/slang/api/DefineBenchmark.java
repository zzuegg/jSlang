package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;

class DefineBenchmark {

    private static final String PBR_SHADER = """
        struct Light {
            float3 position; float radius;
            float3 color;    float intensity;
        };
        struct Material {
            float4 baseColor;
            float roughness; float metallic; float2 pad;
        };

        float3 safeNorm(float3 v) {
            float l = length(v);
            return l > 0.0001 ? v / l : float3(0);
        }

        float ggxD(float NdotH, float r) {
            float a = r*r; float a2 = a*a;
            float d = NdotH*NdotH*(a2-1.0)+1.0;
            return a2 / (3.14159265*d*d);
        }

        float schlickF(float VdotH) {
            float x = 1.0-VdotH; float x2 = x*x;
            return x2*x2*x;
        }

        [shader("compute")]
        [numthreads(8,8,1)]
        void csMain(
            uint3 tid : SV_DispatchThreadID,
            uniform ConstantBuffer<Material> material,
            uniform StructuredBuffer<Light> lights,
            uniform float3 cameraPos,
        #ifdef USE_NORMAL_MAP
            uniform Texture2D normalMap,
            uniform SamplerState samp,
        #endif
            uniform RWTexture2D<float4> output)
        {
            float2 uv = float2(tid.xy) / 512.0;
            float3 worldPos = float3(uv * 10.0 - 5.0, 0);

        #ifdef USE_NORMAL_MAP
            float3 N = safeNorm(normalMap.SampleLevel(samp, uv, 0).xyz * 2.0 - 1.0);
        #else
            float3 N = float3(0, 1, 0);
        #endif

            float3 V = safeNorm(cameraPos - worldPos);
            Material mat = material;
            float3 color = float3(0);

        #ifdef MAX_LIGHTS
            uint maxLights = MAX_LIGHTS;
        #else
            uint maxLights = 4;
        #endif

            for (uint i = 0; i < maxLights; i++) {
                Light light = lights[i];
                float3 L = safeNorm(light.position - worldPos);
                float3 H = safeNorm(L + V);
                float NdotL = max(dot(N, L), 0.001);
                float NdotH = max(dot(N, H), 0.0);
                float VdotH = max(dot(V, H), 0.0);
                float D = ggxD(NdotH, mat.roughness);
                float F = lerp(0.04, 1.0, schlickF(VdotH));

            #ifdef USE_SHADOWS
                float shadow = light.intensity > 0.5 ? 1.0 : 0.5;
            #else
                float shadow = 1.0;
            #endif

                float dist = length(light.position - worldPos);
                float atten = max(0, 1.0 - dist / light.radius);
                color += mat.baseColor.rgb * (D * F + (1.0-mat.metallic)/3.14159265)
                       * NdotL * light.color * light.intensity * atten * shadow;
            }

        #ifdef USE_TONEMAPPING
            color = color / (color + 1.0);  // Reinhard tonemap
            color = pow(color, float3(1.0/2.2));  // Gamma
        #endif

            output[tid.xy] = float4(color, 1.0);
        }
        """;

    private record Variant(String name, String[] macroNames, String[] macroValues) {}

    private static final Variant[] VARIANTS = {
        new Variant("base",          new String[]{}, new String[]{}),
        new Variant("normalMap",     new String[]{"USE_NORMAL_MAP"}, new String[]{"1"}),
        new Variant("shadows",       new String[]{"USE_SHADOWS"}, new String[]{"1"}),
        new Variant("tonemap",       new String[]{"USE_TONEMAPPING"}, new String[]{"1"}),
        new Variant("8lights",       new String[]{"MAX_LIGHTS"}, new String[]{"8"}),
        new Variant("16lights",      new String[]{"MAX_LIGHTS"}, new String[]{"16"}),
        new Variant("full_4",
            new String[]{"USE_NORMAL_MAP","USE_SHADOWS","USE_TONEMAPPING","MAX_LIGHTS"},
            new String[]{"1","1","1","4"}),
        new Variant("full_16",
            new String[]{"USE_NORMAL_MAP","USE_SHADOWS","USE_TONEMAPPING","MAX_LIGHTS"},
            new String[]{"1","1","1","16"}),
    };

    @Test
    void defineVariantBenchmark() throws Exception {
        var global = GlobalSession.create();
        int spirvProfile = global.findProfile("spirv_1_5");

        int warmup = 3;
        int iterations = 20;

        System.out.println("================================================================================");
        System.out.println("  DEFINE VARIANT COMPILATION BENCHMARK");
        System.out.println("  Same PBR shader, different #define combinations");
        System.out.println("  Warmup: " + warmup + ", Measured: " + iterations);
        System.out.println("================================================================================\n");

        CompileTarget[] targets = { CompileTarget.SPIRV, CompileTarget.HLSL };

        for (var target : targets) {
            System.out.println("--- " + target + " ---");
            System.out.printf("  %-12s  %8s  %8s  %8s  %7s  %s%n",
                "Variant", "Min", "Avg", "Med", "Size", "Defines");
            System.out.println("  " + "-".repeat(75));

            for (var variant : VARIANTS) {
                // Warmup
                for (int w = 0; w < warmup; w++) {
                    compileVariant(global, target, spirvProfile,
                        variant, "w" + w);
                }

                // Measured
                long[] times = new long[iterations];
                int outputSize = 0;
                for (int i = 0; i < iterations; i++) {
                    long start = System.nanoTime();
                    outputSize = compileVariant(global, target, spirvProfile,
                        variant, "i" + i);
                    times[i] = System.nanoTime() - start;
                }

                java.util.Arrays.sort(times);
                long min = times[0];
                long med = times[iterations / 2];
                long sum = 0;
                for (long t : times) sum += t;

                String defines = variant.macroNames.length == 0 ? "(none)"
                    : String.join(", ", variant.macroNames);

                System.out.printf("  %-12s  %5.2f ms  %5.2f ms  %5.2f ms  %5d B  %s%n",
                    variant.name, min / 1e6, (sum / iterations) / 1e6,
                    med / 1e6, outputSize, defines);
            }
            System.out.println();
        }

        // Batch: compile ALL 8 variants in sequence, measure total time
        System.out.println("--- Batch: compile all " + VARIANTS.length + " SPIRV variants ---");
        for (int w = 0; w < warmup; w++) {
            for (var v : VARIANTS) compileVariant(global, CompileTarget.SPIRV, spirvProfile, v, "bw" + w);
        }

        long[] batchTimes = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            for (var v : VARIANTS) {
                compileVariant(global, CompileTarget.SPIRV, spirvProfile, v, "b" + i);
            }
            batchTimes[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(batchTimes);
        long bMin = batchTimes[0];
        long bMed = batchTimes[iterations / 2];
        long bSum = 0;
        for (long t : batchTimes) bSum += t;

        System.out.printf("  All %d variants:  min=%.1f ms  avg=%.1f ms  med=%.1f ms%n",
            VARIANTS.length, bMin / 1e6, (bSum / iterations) / 1e6, bMed / 1e6);
        System.out.printf("  Per variant avg:  %.2f ms%n", (bSum / iterations) / 1e6 / VARIANTS.length);

        global.close();
    }

    private int compileVariant(GlobalSession global, CompileTarget target, int spirvProfile,
                               Variant variant, String suffix) throws Exception {
        var builder = new SessionDescBuilder()
            .addTarget(new TargetDescBuilder()
                .format(target)
                .profile(target == CompileTarget.SPIRV ? spirvProfile : 0));

        for (int i = 0; i < variant.macroNames.length; i++) {
            builder.addMacro(variant.macroNames[i], variant.macroValues[i]);
        }

        var session = global.createSession(builder);
        var module = session.loadModuleFromSourceString(
            variant.name + "-" + suffix, variant.name + ".slang", PBR_SHADER);
        var ep = module.findAndCheckEntryPoint("csMain", Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, ep);
        var linked = composite.link();
        int size;
        try (var blob = linked.getEntryPointCode(0, 0)) {
            size = blob.toByteArray().length;
        }
        linked.close();
        composite.close();
        session.close();
        return size;
    }
}
