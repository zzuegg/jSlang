import IBRDF;

struct LambertBRDF : IBRDF {
    float3 evaluate(float3 N, float3 V, float3 L, float3 albedo) {
        float NdotL = max(dot(N, L), 0.0);
        return albedo * NdotL;
    }
};
