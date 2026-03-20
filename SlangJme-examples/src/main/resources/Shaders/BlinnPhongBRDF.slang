import IBRDF;

struct BlinnPhongBRDF : IBRDF {
    float3 evaluate(float3 N, float3 V, float3 L, float3 albedo) {
        float NdotL = max(dot(N, L), 0.0);
        float3 H = normalize(V + L);
        float NdotH = max(dot(N, H), 0.0);
        float spec = pow(NdotH, 64.0);
        return albedo * NdotL + float3(spec);
    }
};
