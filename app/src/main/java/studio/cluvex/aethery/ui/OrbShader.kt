package studio.cluvex.aethery.ui

import android.os.Build

object OrbShader {
    fun supported(): Boolean = Build.VERSION.SDK_INT >= 33

    /**
     * A lit glass sphere: diffuse and specular light that shifts with tilt, a soft caustic swirl,
     * a fresnel rim, and a blend between the cold and warm colour sets. Output is premultiplied.
     */
    const val SRC = """
uniform float2 iCenter;
uniform float iRadius;
uniform float iTime;
uniform float iWarmth;
uniform float iPress;
uniform float2 iTilt;
uniform float3 iColdA;
uniform float3 iColdB;
uniform float3 iWarmA;
uniform float3 iWarmB;

half4 main(float2 fragCoord) {
    float2 p = (fragCoord - iCenter) / iRadius;
    float r = length(p);
    float edge = smoothstep(1.0, 0.985, r);
    if (edge <= 0.0) {
        return half4(0.0);
    }
    float z = sqrt(max(0.0, 1.0 - r * r));
    float3 n = float3(p, z);
    float3 l = normalize(float3(-0.45 + iTilt.x * 0.5, -0.6 + iTilt.y * 0.5, 0.65));
    float diff = clamp(dot(n, l), 0.0, 1.0);
    float spec = pow(clamp(dot(reflect(-l, n), float3(0.0, 0.0, 1.0)), 0.0, 1.0), 26.0);
    float a = atan(p.y, p.x);
    float swirl = 0.5 + 0.5 * sin(a * 3.0 + iTime * 0.6 + r * 5.0) * sin(iTime * 0.37 + r * 3.0);
    float3 cold = mix(iColdB, iColdA, clamp(diff * 0.9 + swirl * 0.15, 0.0, 1.0));
    float3 warm = mix(iWarmB, iWarmA, clamp(diff * 0.9 + swirl * 0.25, 0.0, 1.0));
    float3 col = mix(cold, warm, iWarmth);
    float fres = pow(1.0 - z, 2.5);
    col += fres * mix(float3(0.42, 0.48, 0.92), float3(1.0, 0.78, 0.5), iWarmth) * 0.55;
    col += spec * 0.5;
    col *= 1.0 - 0.07 * iPress;
    col = clamp(col, 0.0, 1.0);
    return half4(half3(col * edge), half(edge));
}
"""
}
