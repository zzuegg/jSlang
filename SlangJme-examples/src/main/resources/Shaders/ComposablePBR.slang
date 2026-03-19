// Composable PBR shader using Slang interfaces for feature composition.
//
// Generic type parameters:
//   B : IBRDF         — controls surface shading model
//   N : INormalSource  — controls normal mapping strategy

// --- Interfaces ---

interface IBRDF {
    float3 evaluate(float3 N, float3 V, float3 L, float3 albedo);
};

interface INormalSource {
    float3 getNormal(float3 worldNormal);
};

// --- BRDF Implementations ---

struct LambertBRDF : IBRDF {
    float3 evaluate(float3 N, float3 V, float3 L, float3 albedo) {
        float NdotL = max(dot(N, L), 0.0);
        return albedo * NdotL;
    }
};

struct BlinnPhongBRDF : IBRDF {
    float3 evaluate(float3 N, float3 V, float3 L, float3 albedo) {
        float NdotL = max(dot(N, L), 0.0);
        float3 H = normalize(V + L);
        float NdotH = max(dot(N, H), 0.0);
        float spec = pow(NdotH, 64.0);
        return albedo * NdotL + float3(spec);
    }
};

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

// --- Normal Source Implementations ---

struct FlatNormal : INormalSource {
    float3 getNormal(float3 worldNormal) {
        return normalize(worldNormal);
    }
};

struct HemisphereNormal : INormalSource {
    float3 getNormal(float3 worldNormal) {
        // Blend normal toward up direction for a soft hemisphere look
        float3 N = normalize(worldNormal);
        return normalize(N + float3(0, 0.3, 0));
    }
};

// --- World / Material Uniforms ---

uniform float4x4 WorldViewProjectionMatrix;
uniform float4x4 WorldMatrix;
uniform float3 CameraPosition;

uniform float3 baseColor;

// --- Vertex Stage ---

struct VsIn {
    float3 position : POSITION;
    float3 normal   : NORMAL;
};

struct VsOut {
    float4 clipPos    : SV_Position;
    float3 worldPos   : TEXCOORD1;
    float3 worldNormal: NORMAL;
};

[shader("vertex")]
VsOut vertexMain(VsIn input) {
    VsOut o;
    o.clipPos = mul(float4(input.position, 1.0), WorldViewProjectionMatrix);
    o.worldPos = mul(float4(input.position, 1.0), WorldMatrix).xyz;
    o.worldNormal = mul(input.normal, (float3x3)WorldMatrix);
    return o;
}

// --- Fragment Stage (generic) ---

[shader("fragment")]
float4 fragmentMain<B : IBRDF, N : INormalSource>(VsOut input) : SV_Target {
    B brdf;
    N normalSource;

    float3 N_vec = normalSource.getNormal(input.worldNormal);
    float3 V = normalize(CameraPosition - input.worldPos);
    float3 L = normalize(float3(1, 1, 1));

    float3 color = brdf.evaluate(N_vec, V, L, baseColor);
    float3 ambient = baseColor * 0.1;

    return float4(ambient + color, 1.0);
}
