package code.yousef.portfolio.ui.aurora

import codes.yousef.sigil.schema.effects.InteractionConfig
import codes.yousef.sigil.schema.effects.ShaderEffectData
import codes.yousef.sigil.schema.effects.SigilCanvasConfig
import codes.yousef.sigil.summon.effects.SigilEffect
import codes.yousef.sigil.summon.effects.SigilEffectCanvas
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.vh
import codes.yousef.summon.extensions.vw
import codes.yousef.summon.modifier.*

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
    
    // Container for the aurora canvas - positioned fixed behind all content
    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .top(0.px)
            .left(0.px)
            .width(100.vw)
            .height(100.vh)
            .zIndex(0)
            .pointerEvents(PointerEvents.None) // Allow clicks to pass through
    ) {
        // Sigil effect canvas with aurora shader
        SigilEffectCanvas(
            id = config.canvasId,
            width = "100vw",
            height = "100vh",
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
            // Uniforms are declared here so Sigil/Materia know what to provide
            SigilEffect(
                ShaderEffectData(
                    id = "aurora-effect",
                    name = "Aurora Background",
                    fragmentShader = buildAuroraWGSLShader(palette),
                    glslFragmentShader = buildAuroraGLSLShader(palette),
                    timeScale = config.timeScale,
                    enableMouseInteraction = config.enableMouseInteraction,
                    // Uniforms map tells Sigil what uniform fields exist in the shader.
                    // Values here are just initial/default values - Sigil overrides time, deltaTime,
                    // resolution, and mouse with animated values in the render loop.
                    uniforms = mapOf(
                        "time" to codes.yousef.sigil.schema.effects.UniformValue.FloatValue(0f),
                        "deltaTime" to codes.yousef.sigil.schema.effects.UniformValue.FloatValue(0f),
                        "resolution" to codes.yousef.sigil.schema.effects.UniformValue.Vec2Value(
                            codes.yousef.sigil.schema.effects.Vec2(1920f, 1080f)
                        ),
                        "mouse" to codes.yousef.sigil.schema.effects.UniformValue.Vec2Value(
                            codes.yousef.sigil.schema.effects.Vec2(0.5f, 0.5f)
                        )
                    )
                )
            )
        }
    }
}

/**
 * Builds the WGSL aurora shader.
 * Creates flowing aurora ribbons using sine waves - consistent with GLSL version.
 * Uses IQ palette formula for colors.
 * 
 * NOTE: Sigil auto-generates the Uniforms struct and @group(0) @binding(0) var<uniform> u: Uniforms;
 * We just use u.time, u.deltaTime, u.resolution, u.mouse in our fragment code.
 */
private fun buildAuroraWGSLShader(palette: AuroraPalette): String = """
// IQ Palette formula for aurora colors
fn palette(t: f32) -> vec3<f32> {
    let a = vec3<f32>(${palette.a.first}, ${palette.a.second}, ${palette.a.third});
    let b = vec3<f32>(${palette.b.first}, ${palette.b.second}, ${palette.b.third});
    let c = vec3<f32>(${palette.c.first}, ${palette.c.second}, ${palette.c.third});
    let d = vec3<f32>(${palette.d.first}, ${palette.d.second}, ${palette.d.third});
    return a + b * cos(6.283185 * (c * t + d));
}

@fragment
fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
    let aspect = u.resolution.x / u.resolution.y;
    var p = uv;
    p.x = p.x * aspect;
    
    let t = u.time * 0.3;
    
    // Wave distortion for aurora ribbon shape
    let wave = sin(p.x * 2.0 + t) * 0.05 
             + sin(p.x * 1.5 - t * 0.7) * 0.07
             + sin(p.x * 4.0 + t * 1.3) * 0.03;
    
    // Aurora band at y=0.7 (upper area since y=1 is top)
    let auroraY = 0.7 + wave;
    let dist = uv.y - auroraY;
    
    // Soft glow - Gaussian falloff (wide spread)
    var glow = exp(-dist * dist * 15.0);
    
    // Add glow that extends downward
    let lowerGlow = exp(-pow(dist + 0.2, 2.0) * 6.0) * 0.5;
    glow = glow + lowerGlow;
    
    // Vertical rays (curtain effect)
    var rays = sin(p.x * 15.0 + t * 2.0) * 0.15 + 0.85;
    rays = rays * (sin(p.x * 8.0 - t) * 0.1 + 0.9);
    glow = glow * rays;
    
    // Gentle fade only at very edges
    glow = glow * smoothstep(0.0, 0.2, uv.y);   // Fade near bottom
    glow = glow * smoothstep(1.0, 0.9, uv.y);   // Slight fade at very top
    
    // Color from palette
    let colorT = sin(p.x * 1.5 + t * 0.3) * 0.3 + 0.5 + dist;
    let auroraColor = palette(colorT);
    
    // Dark sky background
    let sky = vec3<f32>(0.01, 0.01, 0.03);
    
    // Final color - reduced intensity
    let color = sky + auroraColor * glow * 0.7;

    return vec4<f32>(color, 1.0);
}
"""

/**
 * Builds the GLSL aurora shader for WebGL fallback.
 * Creates flowing aurora ribbons using sine waves - consistent with WGSL version.
 * Uses IQ palette formula for colors.
 */
private fun buildAuroraGLSLShader(palette: AuroraPalette): String = """
precision highp float;

uniform float time;
uniform vec2 resolution;
uniform vec2 mouse;

varying vec2 vUv;

// IQ Palette formula: a + b * cos(2*PI * (c * t + d))
vec3 palette(float t) {
    vec3 a = vec3(${palette.a.first}, ${palette.a.second}, ${palette.a.third});
    vec3 b = vec3(${palette.b.first}, ${palette.b.second}, ${palette.b.third});
    vec3 c = vec3(${palette.c.first}, ${palette.c.second}, ${palette.c.third});
    vec3 d = vec3(${palette.d.first}, ${palette.d.second}, ${palette.d.third});
    return a + b * cos(6.283185 * (c * t + d));
}

void main() {
    vec2 uv = vUv;
    
    // DON'T flip Y - vUv.y=0 at bottom, y=1 at top is fine for aurora at top
    
    float aspect = resolution.x / resolution.y;
    vec2 p = uv;
    p.x *= aspect;
    
    float t = time * 0.3;
    
    // Wave distortion for aurora ribbon shape
    float wave = sin(p.x * 2.0 + t) * 0.05 
               + sin(p.x * 1.5 - t * 0.7) * 0.07
               + sin(p.x * 4.0 + t * 1.3) * 0.03;
    
    // Aurora band at y=0.7 (upper area since y=1 is top)
    float auroraY = 0.7 + wave;
    float dist = uv.y - auroraY;
    
    // Soft glow - Gaussian falloff (wide spread)
    float glow = exp(-dist * dist * 15.0);
    
    // Add glow that extends downward
    float lowerGlow = exp(-pow(dist + 0.2, 2.0) * 6.0) * 0.5;
    glow += lowerGlow;
    
    // Vertical rays (curtain effect)
    float rays = sin(p.x * 15.0 + t * 2.0) * 0.15 + 0.85;
    rays *= sin(p.x * 8.0 - t) * 0.1 + 0.9;
    glow *= rays;
    
    // Gentle fade only at very edges
    glow *= smoothstep(0.0, 0.2, uv.y);   // Fade near bottom
    glow *= smoothstep(1.0, 0.9, uv.y);   // Slight fade at very top
    
    // Color from palette
    float colorT = sin(p.x * 1.5 + t * 0.3) * 0.3 + 0.5 + dist;
    vec3 auroraColor = palette(colorT);
    
    // Dark sky background
    vec3 sky = vec3(0.01, 0.01, 0.03);
    
    // Final color - reduced intensity
    vec3 color = sky + auroraColor * glow * 0.7;
    
    gl_FragColor = vec4(color, 1.0);
}
"""
