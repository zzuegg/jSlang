package dev.slang.api;

import dev.slang.bindings.enums.CompileTarget;
import dev.slang.bindings.enums.Stage;
import org.junit.jupiter.api.Test;

class CompilationBenchmark {

    private static final String TRIVIAL_SHADER = """
        [shader("compute")]
        [numthreads(1,1,1)]
        void csMain(uint3 tid : SV_DispatchThreadID) {}
        """;

    private static final String MEDIUM_SHADER = """
        struct Particle {
            float3 position;
            float3 velocity;
            float mass;
            float lifetime;
        };

        [shader("compute")]
        [numthreads(64, 1, 1)]
        void updateParticles(
            uint3 tid : SV_DispatchThreadID,
            uniform RWStructuredBuffer<Particle> particles,
            uniform StructuredBuffer<float3> forces,
            uniform float deltaTime,
            uniform uint particleCount)
        {
            if (tid.x >= particleCount) return;
            Particle p = particles[tid.x];
            float3 acceleration = forces[tid.x] / p.mass;
            p.velocity += acceleration * deltaTime;
            p.position += p.velocity * deltaTime;
            p.lifetime -= deltaTime;
            particles[tid.x] = p;
        }
        """;

    private static final String COMPLEX_SHADER = """
        struct Light {
            float3 position; float radius;
            float3 color;    float intensity;
        };
        struct Material {
            float4 baseColor;
            float roughness; float metallic; float2 padding;
        };

        float3 safeNormalize(float3 v) {
            float len = length(v);
            return len > 0.0001 ? v / len : float3(0);
        }

        float ggxD(float NdotH, float roughness) {
            float a = roughness * roughness;
            float a2 = a * a;
            float d = NdotH * NdotH * (a2 - 1.0) + 1.0;
            return a2 / (3.14159265 * d * d);
        }

        float schlickF(float VdotH) {
            float x = 1.0 - VdotH;
            float x2 = x * x;
            return x2 * x2 * x;
        }

        float smithG(float NdotV, float roughness) {
            float k = (roughness + 1.0) * (roughness + 1.0) / 8.0;
            return NdotV / (NdotV * (1.0 - k) + k);
        }

        float3 pbrShade(float3 N, float3 V, float3 L, Material mat) {
            float3 H = safeNormalize(L + V);
            float NdotL = max(dot(N, L), 0.001);
            float NdotV = max(dot(N, V), 0.001);
            float NdotH = max(dot(N, H), 0.0);
            float VdotH = max(dot(V, H), 0.0);
            float D = ggxD(NdotH, mat.roughness);
            float F = lerp(0.04, 1.0, schlickF(VdotH));
            float G = smithG(NdotL, mat.roughness) * smithG(NdotV, mat.roughness);
            float3 spec = float3(D * F * G / (4.0 * NdotL * NdotV));
            float3 diff = mat.baseColor.rgb * (1.0 - mat.metallic) / 3.14159265;
            return (diff + spec) * NdotL;
        }

        [shader("compute")]
        [numthreads(8, 8, 1)]
        void csMain(
            uint3 tid : SV_DispatchThreadID,
            uniform ConstantBuffer<Material> material,
            uniform StructuredBuffer<Light> lights,
            uniform uint lightCount,
            uniform Texture2D normalMap,
            uniform SamplerState samp,
            uniform float3 cameraPos,
            uniform RWTexture2D<float4> output)
        {
            float2 uv = float2(tid.xy) / 512.0;
            float3 N = normalMap.SampleLevel(samp, uv, 0).xyz * 2.0 - 1.0;
            N = safeNormalize(N);
            float3 worldPos = float3(uv * 10.0 - 5.0, 0);
            float3 V = safeNormalize(cameraPos - worldPos);
            Material mat = material;
            float3 color = float3(0);
            for (uint i = 0; i < lightCount; i++) {
                float3 L = safeNormalize(lights[i].position - worldPos);
                float dist = length(lights[i].position - worldPos);
                float atten = max(0, 1.0 - dist / lights[i].radius);
                color += pbrShade(N, V, L, mat) * lights[i].color * lights[i].intensity * atten;
            }
            output[tid.xy] = float4(color, 1.0);
        }
        """;

    @Test
    void benchmark() throws Exception {
        var global = GlobalSession.create();
        int spirvProfile = global.findProfile("spirv_1_5");

        CompileTarget[] targets = { CompileTarget.SPIRV, CompileTarget.HLSL, CompileTarget.GLSL };
        String[][] shaders = {
            { "trivial", TRIVIAL_SHADER },
            { "medium",  MEDIUM_SHADER },
            { "complex", COMPLEX_SHADER }
        };

        int warmup = 3;
        int iterations = 20;

        System.out.println("================================================================================");
        System.out.println("  SLANG COMPILATION BENCHMARK");
        System.out.println("  Warmup: " + warmup + " iterations, Measured: " + iterations + " iterations");
        System.out.println("================================================================================\n");

        for (var target : targets) {
            System.out.println("--- Target: " + target + " ---");
            System.out.printf("  %-10s  %8s  %8s  %8s  %8s  %8s%n",
                "Shader", "Min", "Avg", "Max", "Med", "Output");
            System.out.println("  " + "-".repeat(60));

            for (var shader : shaders) {
                String name = shader[0];
                String source = shader[1];

                // Warmup
                for (int i = 0; i < warmup; i++) {
                    compileOnce(global, target, spirvProfile, name + "-w" + i, source);
                }

                // Measured runs
                long[] times = new long[iterations];
                int outputSize = 0;
                for (int i = 0; i < iterations; i++) {
                    long start = System.nanoTime();
                    outputSize = compileOnce(global, target, spirvProfile,
                        name + "-" + target + "-" + i, source);
                    times[i] = System.nanoTime() - start;
                }

                java.util.Arrays.sort(times);
                long min = times[0];
                long max = times[iterations - 1];
                long med = times[iterations / 2];
                long sum = 0;
                for (long t : times) sum += t;
                long avg = sum / iterations;

                System.out.printf("  %-10s  %6.2f ms  %6.2f ms  %6.2f ms  %6.2f ms  %6d B%n",
                    name, min / 1e6, avg / 1e6, max / 1e6, med / 1e6, outputSize);
            }
            System.out.println();
        }

        // Also measure session creation + full pipeline
        System.out.println("--- Full Pipeline (session create → load → compile → link → get code) ---");
        System.out.printf("  %-10s  %8s  %8s  %8s%n", "Shader", "Min", "Avg", "Med");
        System.out.println("  " + "-".repeat(45));

        for (var shader : shaders) {
            String name = shader[0];
            String source = shader[1];

            for (int i = 0; i < warmup; i++) {
                fullPipeline(global, spirvProfile, name + "-fp-w" + i, source);
            }

            long[] times = new long[iterations];
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                fullPipeline(global, spirvProfile, name + "-fp-" + i, source);
                times[i] = System.nanoTime() - start;
            }

            java.util.Arrays.sort(times);
            long min = times[0];
            long med = times[iterations / 2];
            long sum = 0;
            for (long t : times) sum += t;

            System.out.printf("  %-10s  %6.2f ms  %6.2f ms  %6.2f ms%n",
                name, min / 1e6, (sum / iterations) / 1e6, med / 1e6);
        }

        // Compiler elapsed time from Slang's internal counters
        double[] elapsed = global.getCompilerElapsedTime();
        System.out.printf("%nSlang internal timers: total=%.3f s, downstream=%.3f s%n",
            elapsed[0], elapsed[1]);

        global.close();
    }

    private int compileOnce(GlobalSession global, CompileTarget target, int spirvProfile,
                            String moduleName, String source) throws Exception {
        var builder = new SessionDescBuilder().addTarget(
            new TargetDescBuilder().format(target)
                .profile(target == CompileTarget.SPIRV ? spirvProfile : 0));
        var session = global.createSession(builder);
        var module = session.loadModuleFromSourceString(moduleName, moduleName + ".slang", source);
        var ep = module.findAndCheckEntryPoint(
            source.contains("updateParticles") ? "updateParticles" : "csMain",
            Stage.COMPUTE);
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

    private void fullPipeline(GlobalSession global, int spirvProfile,
                              String moduleName, String source) throws Exception {
        var session = global.createSession(
            new SessionDescBuilder().addTarget(
                new TargetDescBuilder().format(CompileTarget.SPIRV).profile(spirvProfile)));
        var module = session.loadModuleFromSourceString(moduleName, moduleName + ".slang", source);
        var ep = module.findAndCheckEntryPoint(
            source.contains("updateParticles") ? "updateParticles" : "csMain",
            Stage.COMPUTE);
        var composite = session.createCompositeComponentType(module, ep);
        var linked = composite.link();
        try (var blob = linked.getEntryPointCode(0, 0)) {
            blob.toByteArray();
        }
        linked.close();
        composite.close();
        session.close();
    }
}
