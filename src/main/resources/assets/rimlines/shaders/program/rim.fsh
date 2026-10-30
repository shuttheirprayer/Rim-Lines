#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D TerrainDepthSampler;
uniform sampler2D EntityDepthSampler;
uniform vec2 OutSize;
uniform vec4 RimData;
uniform vec4 RimColor;
uniform vec4 DepthData;
uniform float InPlace;

in vec2 texCoord;
out vec4 fragColor;

float saturate(float value) { return clamp(value, 0.0, 1.0); }
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

float linearizeDepth(float depth) {
    return (DepthData.z * DepthData.w)
            / max(DepthData.w - depth * (DepthData.w - DepthData.z), 0.00001);
}

float surfaceMask(float sceneDepth, float depthWidth) {
    if (RimData.x > 0.5) return 1.0;
    float terrainDepth = texture(TerrainDepthSampler, texCoord).r;
    float entityDepth = texture(EntityDepthSampler, texCoord).r;
    if (entityDepth < terrainDepth - 1.0e-6) return 0.0;
    float tolerance = max(2.0e-5, depthWidth * 0.5);
    bool terrainSky = terrainDepth >= 0.999999;
    if (!terrainSky && abs(sceneDepth - terrainDepth) < tolerance) return 1.0;
    if (entityDepth >= 0.999999 || abs(sceneDepth - entityDepth) >= tolerance) return 0.0;
    return terrainSky || abs(entityDepth - terrainDepth) >= tolerance ? 1.0 : 0.0;
}

float rimMask(vec3 color, float centerRaw, float depthWidth) {
    float lit = RimData.w > 0.0 ? smoothstep(RimData.w, RimData.w * 3.0, luma(color)) : 1.0;
    if (lit <= 0.0) return 0.0;
    float surface = surfaceMask(centerRaw, depthWidth) * lit;
    if (surface <= 0.0) return 0.0;
    float zCenter = linearizeDepth(centerRaw);
    float distanceFade = saturate(1.0 - zCenter / max(DepthData.y, 1.0));
    if (distanceFade <= 0.0) return 0.0;
    vec2 texel = 1.0 / max(OutSize, vec2(1.0));
    vec2 offset = texel * clamp(RimData.y, 1.0, 12.0);
    vec2 minUv = texel * 0.5;
    vec2 maxUv = vec2(1.0) - minUv;
    float dzTR = linearizeDepth(texture(DepthSampler, clamp(texCoord + offset, minUv, maxUv)).r) - zCenter;
    float dzBL = linearizeDepth(texture(DepthSampler, clamp(texCoord - offset, minUv, maxUv)).r) - zCenter;
    float dzTL = linearizeDepth(texture(DepthSampler, clamp(texCoord + vec2(-offset.x, offset.y), minUv, maxUv)).r) - zCenter;
    float dzBR = linearizeDepth(texture(DepthSampler, clamp(texCoord + vec2(offset.x, -offset.y), minUv, maxUv)).r) - zCenter;
    float maxBehind = max(max(dzTR, dzBL), max(dzTL, dzBR));
    float edgeness = max(abs(dzTR - dzBL), abs(dzTL - dzBR));
    float discontinuity = max(abs(dzTR + dzBL), abs(dzTL + dzBR));
    float discontinuityRatio = discontinuity / max(edgeness, 0.00001);
    float relativeDepth = maxBehind / max(zCenter, 1.0);
    float relativeEdge = edgeness / max(zCenter, 1.0);
    float baseThreshold = max(DepthData.x, 0.01) * 0.33;
    return smoothstep(baseThreshold, baseThreshold * 2.2, relativeDepth)
            * smoothstep(baseThreshold * 0.5, baseThreshold * 1.2, relativeEdge)
            * smoothstep(0.18, 0.42, discontinuityRatio) * distanceFade * surface;
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    float depthWidth = fwidth(depth);
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float mask = depth < 0.999999 ? rimMask(color, depth, depthWidth) : 0.0;
    if (mask <= 0.0) {
        if (InPlace > 0.5) discard;
        fragColor = vec4(color, 1.0);
        return;
    }
    vec3 sceneTint = normalize(max(sqrt(max(color, vec3(0.0))), vec3(0.00001)));
    vec3 tint = RimColor.rgb * mix(vec3(1.0), sceneTint, max(clamp(RimColor.a, 0.0, 1.0), 0.68));
    vec3 glow = sqrt(max(color, vec3(0.04))) * tint * RimData.z;
    fragColor = vec4(max(1.0 - (1.0 - color) * (1.0 - clamp(glow * mask, 0.0, 1.0)), vec3(0.0)), 1.0);
}
