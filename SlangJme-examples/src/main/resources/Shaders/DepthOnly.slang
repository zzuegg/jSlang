// Depth-only pass shader for shadow mapping.
// Registered as a reusable mode — applied to all materials automatically.

uniform float4x4 WorldViewProjectionMatrix;

struct VsIn {
    float3 position : POSITION;
};

struct VsOut {
    float4 pos : SV_Position;
};

[shader("vertex")]
VsOut vertexMain(VsIn input) {
    VsOut o;
    o.pos = mul(WorldViewProjectionMatrix, float4(input.position, 1.0));
    return o;
}

[shader("fragment")]
float4 fragmentMain(VsOut input) : SV_Target {
    return float4(input.pos.z / input.pos.w, 0, 0, 1);
}
