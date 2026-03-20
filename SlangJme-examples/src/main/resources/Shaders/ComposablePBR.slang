// Composable PBR shader using Slang interfaces for feature composition.
//
// Generic type parameters:
//   B : IBRDF         — controls surface shading model
//   N : INormalSource  — controls normal mapping strategy
//
// Implementations are in separate files and resolved via import.

import IBRDF;
import INormalSource;
import LambertBRDF;
import BlinnPhongBRDF;
import CookTorranceBRDF;
import FlatNormal;
import HemisphereNormal;

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
