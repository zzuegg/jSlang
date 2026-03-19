// PBR-like material shader demonstrating:
// - Auto-discovered material parameters (roughness, metallic, baseColor)
// - Texture support (albedoTex, normalTex)
// - Boolean toggle defines (useNormalMap)
// - World parameter recognition (WorldViewProjectionMatrix, etc.)

uniform float4x4 WorldViewProjectionMatrix;
uniform float4x4 WorldMatrix;
uniform float3 CameraPosition;

// Material parameters — auto-discovered via reflection
uniform float roughness;
uniform float metallic;
uniform float3 baseColor;
uniform bool useNormalMap;

// Textures — auto-discovered, generates HAS_ALBEDOTEX / HAS_NORMALTEX defines
#ifdef HAS_ALBEDOTEX
Texture2D albedoTex;
SamplerState albedoSampler;
#endif

#ifdef HAS_NORMALTEX
Texture2D normalTex;
SamplerState normalSampler;
#endif

struct VsIn {
    float3 position : POSITION;
    float3 normal   : NORMAL;
    float2 uv       : TEXCOORD0;
};

struct VsOut {
    float4 clipPos    : SV_Position;
    float3 worldPos   : TEXCOORD1;
    float3 worldNormal: NORMAL;
    float2 uv         : TEXCOORD0;
};

[shader("vertex")]
VsOut vertexMain(VsIn input) {
    VsOut o;
    o.clipPos = mul(WorldViewProjectionMatrix, float4(input.position, 1.0));
    o.worldPos = mul(WorldMatrix, float4(input.position, 1.0)).xyz;
    o.worldNormal = mul((float3x3)WorldMatrix, input.normal);
    o.uv = input.uv;
    return o;
}

[shader("fragment")]
float4 fragmentMain(VsOut input) : SV_Target {
    float3 N = normalize(input.worldNormal);
    float3 V = normalize(CameraPosition - input.worldPos);

    // Simple directional light
    float3 L = normalize(float3(1, 1, 1));
    float NdotL = max(dot(N, L), 0.0);

    // Base albedo
    float3 albedo = baseColor;

    #ifdef HAS_ALBEDOTEX
    albedo *= albedoTex.Sample(albedoSampler, input.uv).rgb;
    #endif

    // Simple PBR approximation
    float3 H = normalize(V + L);
    float NdotH = max(dot(N, H), 0.0);
    float spec = pow(NdotH, mix(8.0, 256.0, 1.0 - roughness));

    float3 diffuse = albedo * NdotL * (1.0 - metallic);
    float3 specular = float3(spec * mix(0.04, 1.0, metallic));

    float3 ambient = albedo * 0.1;
    float3 color = ambient + diffuse + specular;

    return float4(color, 1.0);
}
