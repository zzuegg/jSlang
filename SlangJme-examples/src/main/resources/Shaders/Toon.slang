// Toon/cel-shading shader demonstrating a different material style
// sharing the same mode infrastructure as PBR.

uniform float4x4 WorldViewProjectionMatrix;
uniform float4x4 WorldMatrix;
uniform float3 CameraPosition;

uniform float3 baseColor;
uniform int toonLevels;  // Number of quantization levels (2-5 typical)
uniform float outlineWidth;

#ifdef HAS_ALBEDOTEX
Texture2D albedoTex;
SamplerState albedoSampler;
#endif

struct VsIn {
    float3 position : POSITION;
    float3 normal   : NORMAL;
    float2 uv       : TEXCOORD0;
};

struct VsOut {
    float4 clipPos     : SV_Position;
    float3 worldNormal : NORMAL;
    float2 uv          : TEXCOORD0;
};

[shader("vertex")]
VsOut vertexMain(VsIn input) {
    VsOut o;
    o.clipPos = mul(float4(input.position, 1.0), WorldViewProjectionMatrix);
    o.worldNormal = mul(input.normal, (float3x3)WorldMatrix);
    o.uv = input.uv;
    return o;
}

[shader("fragment")]
float4 fragmentMain(VsOut input) : SV_Target {
    float3 N = normalize(input.worldNormal);
    float3 L = normalize(float3(1, 1, 1));

    float NdotL = max(dot(N, L), 0.0);

    // Quantize lighting into discrete bands
    int levels = max(toonLevels, 2);
    float quantized = floor(NdotL * float(levels)) / float(levels);

    float3 albedo = baseColor;
    #ifdef HAS_ALBEDOTEX
    albedo *= albedoTex.Sample(albedoSampler, input.uv).rgb;
    #endif

    float3 color = albedo * (quantized * 0.8 + 0.2);
    return float4(color, 1.0);
}
