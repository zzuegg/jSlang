import IBRDF;

struct CookTorranceBRDF : IBRDF {
    float3 evaluate(float3 N, float3 V, float3 L, float3 albedo) {
        float NdotL = max(dot(N, L), 0.0);
        float3 H = normalize(V + L);
        float NdotH = max(dot(N, H), 0.0);
        float NdotV = max(dot(N, V), 0.001);

        // Simplified GGX
        float roughness = 0.5;
        float a = roughness * roughness;
        float a2 = a * a;
        float denom = NdotH * NdotH * (a2 - 1.0) + 1.0;
        float D = a2 / (3.14159 * denom * denom);

        float3 diffuse = albedo * NdotL * (1.0 / 3.14159);
        float3 specular = float3(D * 0.04 * NdotL);

        return diffuse + specular;
    }
};
