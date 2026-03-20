import INormalSource;

struct HemisphereNormal : INormalSource {
    float3 getNormal(float3 worldNormal) {
        float3 N = normalize(worldNormal);
        return normalize(N + float3(0, 0.3, 0));
    }
};
