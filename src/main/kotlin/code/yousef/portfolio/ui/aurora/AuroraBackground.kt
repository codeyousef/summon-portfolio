package code.yousef.portfolio.ui.aurora

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.left
import codes.yousef.summon.modifier.LayoutModifiers.top
import codes.yousef.sigil.summon.effects.SigilEffectCanvas
import codes.yousef.sigil.summon.effects.SigilEffect
import codes.yousef.sigil.schema.effects.SigilCanvasConfig
import codes.yousef.sigil.schema.effects.InteractionConfig
import codes.yousef.sigil.schema.effects.ShaderEffectData
import codes.yousef.sigil.schema.effects.UniformValue
import codes.yousef.sigil.schema.effects.Vec3
import codes.yousef.sigil.schema.effects.WGSLLib
import codes.yousef.sigil.schema.effects.GLSLLib

/**
 * Aurora background effect component using Sigil.
 * 
 * This component renders a WebGPU-powered aurora background effect using
 * Sigil's screen-space effect system. The effect uses IQ's cosine palette
 * formula for smooth color gradients and simplex noise for organic movement.
 * 
 * SSR renders the canvas with embedded effect configuration.
 * Client-side hydration creates WebGPU shader passes via Sigil's SigilEffectHydrator.
 * 
 * For browsers without WebGPU (e.g., Firefox), Sigil automatically falls back
 * to WebGL rendering using the GLSL shader variant.
 */
@Composable
fun AuroraBackground(
    config: AuroraConfig = AuroraConfig()
) {
    // Get the selected palette
    val palette = AuroraPalettes.ALL.getOrElse(config.initialPaletteIndex) { AuroraPalettes.DEFAULT }
    
    // CSS to force all descendants of aurora container to inherit full height
    // This is needed because Summon wraps composables in divs without explicit sizing
    GlobalStyle("""
        [data-aurora-container="true"] > * { width: 100%; height: 100%; }
        [data-aurora-container="true"] > * > * { width: 100%; height: 100%; }
        [data-aurora-container="true"] > * > * > * { width: 100%; height: 100%; }
    """.trimIndent())
    
    // Container for the aurora canvas - positioned fixed behind all content
    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .top(0.px)
            .left(0.px)
            .width(100.percent)
            .height(config.height.px)
            .zIndex(0)
            .pointerEvents(PointerEvents.None) // Allow clicks to pass through
            .dataAttribute("aurora-container", "true")
    ) {
        // Sigil effect canvas with aurora shader
        SigilEffectCanvas(
            id = config.canvasId,
            width = "100%",
            height = "100%",
            config = SigilCanvasConfig(
                id = config.canvasId,
                respectDevicePixelRatio = true,
                fallbackToWebGL = true,  // Enable WebGL fallback for Firefox
                fallbackToCSS = true
            ),
            interactions = InteractionConfig(
                enableMouseMove = config.enableMouseInteraction,
                enableMouseClick = config.enableClickCycle,
                enableKeyboard = config.enableKeyboardCycle,
                enableTouch = config.enableMouseInteraction
            ),
            fallback = { "" }
        ) {
            // Use SigilEffect directly to provide both WGSL and GLSL shaders
            SigilEffect(
                ShaderEffectData(
                    id = "aurora-effect",
                    name = "Aurora Background",
                    fragmentShader = buildAuroraWGSLShader(),
                    glslFragmentShader = buildAuroraGLSLShader(palette),
                    timeScale = config.timeScale,
                    enableMouseInteraction = config.enableMouseInteraction,
                    uniforms = mapOf(
                        "noiseScale" to UniformValue.FloatValue(config.noiseScale),
                        "paletteA" to UniformValue.Vec3Value(Vec3(palette.a.first, palette.a.second, palette.a.third)),
                        "paletteB" to UniformValue.Vec3Value(Vec3(palette.b.first, palette.b.second, palette.b.third)),
                        "paletteC" to UniformValue.Vec3Value(Vec3(palette.c.first, palette.c.second, palette.c.third)),
                        "paletteD" to UniformValue.Vec3Value(Vec3(palette.d.first, palette.d.second, palette.d.third))
                    )
                )
            )
        }
    }
}

/**
 * Builds the WGSL aurora shader using Sigil's WGSLLib.
 */
private fun buildAuroraWGSLShader(): String = """
${WGSLLib.Structs.EFFECT_UNIFORMS}

// Custom uniforms for aurora effect
@group(0) @binding(1) var<uniform> noiseScale: f32;
@group(0) @binding(2) var<uniform> paletteA: vec3<f32>;
@group(0) @binding(3) var<uniform> paletteB: vec3<f32>;
@group(0) @binding(4) var<uniform> paletteC: vec3<f32>;
@group(0) @binding(5) var<uniform> paletteD: vec3<f32>;

${WGSLLib.Noise.SIMPLEX_2D}

// IQ's cosine palette function
fn palette(t: f32) -> vec3<f32> {
    return paletteA + paletteB * cos(6.28318 * (paletteC * t + paletteD));
}

// Fractal Brownian Motion using 2D simplex noise with z as time
fn fbm(p: vec3<f32>) -> f32 {
    var f = 0.0;
    var scale = noiseScale;
    var amp = 0.5;
    for (var i = 0; i < 5; i++) {
        f += amp * simplex2D(p.xy * scale + vec2<f32>(p.z * 0.3));
        scale *= 2.0;
        amp *= 0.5;
    }
    return f;
}

@fragment
fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
    let aspect = uniforms.resolution.x / uniforms.resolution.y;
    var p = uv;
    p.x *= aspect;
    
    let t = uniforms.time * 0.1;
    
    // Create flowing aurora bands using FBM noise
    let n1 = fbm(vec3<f32>(p * 2.0, t));
    let n2 = fbm(vec3<f32>(p * 3.0 + 100.0, t * 0.7));
    let n3 = fbm(vec3<f32>(p * 1.5 + 200.0, t * 1.3));
    
    // Aurora intensity based on vertical position and noise
    let verticalFade = smoothstep(0.0, 0.6, 1.0 - uv.y);
    let aurora1 = smoothstep(0.1, 0.5, n1) * verticalFade;
    let aurora2 = smoothstep(0.2, 0.6, n2) * verticalFade * 0.7;
    let aurora3 = smoothstep(0.15, 0.55, n3) * verticalFade * 0.5;
    
    // Color each band using the palette
    let c1 = palette(n1 * 0.5 + 0.2) * aurora1;
    let c2 = palette(n2 * 0.5 + 0.5) * aurora2;
    let c3 = palette(n3 * 0.5 + 0.8) * aurora3;
    
    var color = c1 + c2 + c3;
    
    // Add subtle mouse interaction
    let mouseDist = length(uv - uniforms.mouse);
    let mouseGlow = exp(-mouseDist * 3.0) * 0.15;
    color += palette(uniforms.time * 0.05) * mouseGlow;
    
    // Soft fade at edges
    let edgeFade = smoothstep(0.0, 0.1, uv.y) * (1.0 - smoothstep(0.9, 1.0, uv.y));
    color *= edgeFade;
    
    let alpha = max(max(aurora1, aurora2), aurora3) * 0.8;
    return vec4<f32>(color, alpha * edgeFade);
}
"""

/**
 * Builds the GLSL aurora shader for WebGL fallback using Sigil's GLSLLib.
 */
private fun buildAuroraGLSLShader(palette: AuroraPalette): String = """
${GLSLLib.Presets.FRAGMENT_HEADER_WITH_UNIFORMS}

// Custom uniforms for aurora effect
uniform float noiseScale;
uniform vec3 paletteA;
uniform vec3 paletteB;
uniform vec3 paletteC;
uniform vec3 paletteD;

${GLSLLib.Noise.SIMPLEX_2D}

// IQ's cosine palette function
vec3 palette(float t) {
    return paletteA + paletteB * cos(6.28318 * (paletteC * t + paletteD));
}

// Fractal Brownian Motion using 2D simplex noise with z as time
float fbm(vec3 p) {
    float f = 0.0;
    float scale = noiseScale;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        f += amp * simplex2D(p.xy * scale + vec2(p.z * 0.3));
        scale *= 2.0;
        amp *= 0.5;
    }
    return f;
}

void main() {
    float aspect = resolution.x / resolution.y;
    vec2 p = vUv;
    p.x *= aspect;
    
    float t = time * 0.1;
    
    // Create flowing aurora bands using FBM noise
    float n1 = fbm(vec3(p * 2.0, t));
    float n2 = fbm(vec3(p * 3.0 + 100.0, t * 0.7));
    float n3 = fbm(vec3(p * 1.5 + 200.0, t * 1.3));
    
    // Aurora intensity based on vertical position and noise
    float verticalFade = smoothstep(0.0, 0.6, 1.0 - vUv.y);
    float aurora1 = smoothstep(0.1, 0.5, n1) * verticalFade;
    float aurora2 = smoothstep(0.2, 0.6, n2) * verticalFade * 0.7;
    float aurora3 = smoothstep(0.15, 0.55, n3) * verticalFade * 0.5;
    
    // Color each band using the palette
    vec3 c1 = palette(n1 * 0.5 + 0.2) * aurora1;
    vec3 c2 = palette(n2 * 0.5 + 0.5) * aurora2;
    vec3 c3 = palette(n3 * 0.5 + 0.8) * aurora3;
    
    vec3 color = c1 + c2 + c3;
    
    // Add subtle mouse interaction
    float mouseDist = length(vUv - mouse);
    float mouseGlow = exp(-mouseDist * 3.0) * 0.15;
    color += palette(time * 0.05) * mouseGlow;
    
    // Soft fade at edges
    float edgeFade = smoothstep(0.0, 0.1, vUv.y) * (1.0 - smoothstep(0.9, 1.0, vUv.y));
    color *= edgeFade;
    
    float alpha = max(max(aurora1, aurora2), aurora3) * 0.8;
    gl_FragColor = vec4(color, alpha * edgeFade);
}
"""
