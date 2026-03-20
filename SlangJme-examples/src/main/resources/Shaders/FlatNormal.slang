import INormalSource;

struct FlatNormal : INormalSource {
    float3 getNormal(float3 worldNormal) {
        return normalize(worldNormal);
    }
};
