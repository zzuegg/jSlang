// Simple unlit shader demonstrating minimal material setup.
// Only needs WorldViewProjectionMatrix and a color.

uniform float4x4 WorldViewProjectionMatrix;

uniform float4 color;

#ifdef HAS_COLORTEX
Texture2D colorTex;
SamplerState colorSampler;
#endif

struct VsIn {
    float3 position : POSITION;
    float2 uv       : TEXCOORD0;
};

struct VsOut {
    float4 pos : SV_Position;
    float2 uv  : TEXCOORD0;
};

[shader("vertex")]
VsOut vertexMain(VsIn input) {
    VsOut o;
    o.pos = mul(WorldViewProjectionMatrix, float4(input.position, 1.0));
    o.uv = input.uv;
    return o;
}

[shader("fragment")]
float4 fragmentMain(VsOut input) : SV_Target {
    float4 result = color;

    #ifdef HAS_COLORTEX
    result *= colorTex.Sample(colorSampler, input.uv);
    #endif

    return result;
}
