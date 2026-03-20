// Interface for surface shading models (BRDFs).
interface IBRDF {
    float3 evaluate(float3 N, float3 V, float3 L, float3 albedo);
};
