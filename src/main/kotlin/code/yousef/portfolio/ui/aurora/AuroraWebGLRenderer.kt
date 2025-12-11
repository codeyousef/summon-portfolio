package code.yousef.portfolio.ui.aurora

/**
 * Inline WebGL renderer for the aurora effect.
 * 
 * This generates a self-contained JavaScript WebGL renderer that doesn't depend
 * on any external JS bundles. It's embedded directly into the page and initializes
 * immediately when the canvas is available.
 * 
 * This bypasses Sigil's hydration system to ensure the aurora effect works on
 * browsers without WebGPU support (like Firefox) without requiring the Sigil JS bundle.
 */
object AuroraWebGLRenderer {
    
    /**
     * Generates the inline WebGL initialization script for the aurora effect.
     * 
     * @param canvasId The ID of the canvas element to render to
     * @param palette The aurora color palette to use
     * @param config The aurora configuration
     * @return JavaScript code that initializes the WebGL aurora renderer
     */
    fun generateScript(
        canvasId: String,
        palette: AuroraPalette,
        config: AuroraConfig
    ): String {
        val noiseScale = config.noiseScale
        val timeScale = config.timeScale
        
        // Inline the palette values
        val paletteA = "vec3(${palette.a.first}, ${palette.a.second}, ${palette.a.third})"
        val paletteB = "vec3(${palette.b.first}, ${palette.b.second}, ${palette.b.third})"
        val paletteC = "vec3(${palette.c.first}, ${palette.c.second}, ${palette.c.third})"
        val paletteD = "vec3(${palette.d.first}, ${palette.d.second}, ${palette.d.third})"
        
        return """
(function() {
    'use strict';
    
    var canvas = document.getElementById('$canvasId');
    if (!canvas) {
        console.error('Aurora: Canvas not found: $canvasId');
        return;
    }
    
    // Check if WebGPU is available - if so, let Sigil handle it
    if (navigator.gpu) {
        console.log('Aurora: WebGPU available, deferring to Sigil hydration');
        return;
    }
    
    var gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
    if (!gl) {
        console.warn('Aurora: WebGL not supported, keeping CSS fallback');
        return;
    }
    
    console.log('Aurora: Initializing WebGL fallback renderer');
    
    // Remove any CSS fallback background
    canvas.style.background = 'transparent';
    
    // Vertex shader - simple fullscreen quad
    var vertexShaderSource = `
        attribute vec2 a_position;
        varying vec2 vUv;
        void main() {
            vUv = a_position * 0.5 + 0.5;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
    `;
    
    // Fragment shader - aurora effect with inline simplex noise
    var fragmentShaderSource = `
        precision highp float;
        
        varying vec2 vUv;
        uniform float time;
        uniform vec2 resolution;
        uniform vec2 mouse;
        uniform float noiseScale;
        
        // Simplex 2D noise - self-contained implementation
        vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        vec2 mod289(vec2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        vec3 permute(vec3 x) { return mod289(((x*34.0)+1.0)*x); }
        
        float simplex2D(vec2 v) {
            const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
            vec2 i  = floor(v + dot(v, C.yy));
            vec2 x0 = v - i + dot(i, C.xx);
            vec2 i1;
            i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
            vec4 x12 = x0.xyxy + C.xxzz;
            x12.xy -= i1;
            i = mod289(i);
            vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
            vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
            m = m*m;
            m = m*m;
            vec3 x = 2.0 * fract(p * C.www) - 1.0;
            vec3 h = abs(x) - 0.5;
            vec3 ox = floor(x + 0.5);
            vec3 a0 = x - ox;
            m *= 1.79284291400159 - 0.85373472095314 * (a0*a0 + h*h);
            vec3 g;
            g.x = a0.x * x0.x + h.x * x0.y;
            g.yz = a0.yz * x12.xz + h.yz * x12.yw;
            return 130.0 * dot(m, g);
        }
        
        // IQ's cosine palette function with inlined palette values
        vec3 palette(float t) {
            vec3 a = $paletteA;
            vec3 b = $paletteB;
            vec3 c = $paletteC;
            vec3 d = $paletteD;
            return a + b * cos(6.28318 * (c * t + d));
        }
        
        // Fractal Brownian Motion
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
            float edgeFade = smoothstep(0.0, 0.1, vUv.y) * smoothstep(1.0, 0.9, vUv.y);
            color *= edgeFade;
            
            float alpha = max(max(aurora1, aurora2), aurora3) * 0.8;
            gl_FragColor = vec4(color, alpha * edgeFade);
        }
    `;
    
    function compileShader(source, type) {
        var shader = gl.createShader(type);
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
            console.error('Aurora shader error:', gl.getShaderInfoLog(shader));
            return null;
        }
        return shader;
    }
    
    var vertexShader = compileShader(vertexShaderSource, gl.VERTEX_SHADER);
    var fragmentShader = compileShader(fragmentShaderSource, gl.FRAGMENT_SHADER);
    
    if (!vertexShader || !fragmentShader) {
        console.error('Aurora: Failed to compile shaders');
        return;
    }
    
    var program = gl.createProgram();
    gl.attachShader(program, vertexShader);
    gl.attachShader(program, fragmentShader);
    gl.linkProgram(program);
    
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
        console.error('Aurora program error:', gl.getProgramInfoLog(program));
        return;
    }
    
    gl.useProgram(program);
    
    // Create fullscreen quad
    var positions = new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]);
    var buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, positions, gl.STATIC_DRAW);
    
    var positionLocation = gl.getAttribLocation(program, 'a_position');
    gl.enableVertexAttribArray(positionLocation);
    gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0);
    
    // Get uniform locations
    var timeLocation = gl.getUniformLocation(program, 'time');
    var resolutionLocation = gl.getUniformLocation(program, 'resolution');
    var mouseLocation = gl.getUniformLocation(program, 'mouse');
    var noiseScaleLocation = gl.getUniformLocation(program, 'noiseScale');
    
    // Set constant uniforms
    gl.uniform1f(noiseScaleLocation, ${noiseScale});
    
    // Mouse tracking
    var mouseX = 0.5, mouseY = 0.5;
    canvas.addEventListener('mousemove', function(e) {
        var rect = canvas.getBoundingClientRect();
        mouseX = (e.clientX - rect.left) / rect.width;
        mouseY = 1.0 - (e.clientY - rect.top) / rect.height;
    });
    
    // Enable blending for transparency
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    
    // Resize handler
    function resize() {
        var dpr = window.devicePixelRatio || 1;
        var width = canvas.clientWidth * dpr;
        var height = canvas.clientHeight * dpr;
        if (canvas.width !== width || canvas.height !== height) {
            canvas.width = width;
            canvas.height = height;
            gl.viewport(0, 0, width, height);
        }
    }
    
    var startTime = performance.now();
    var animationId;
    
    function render() {
        resize();
        
        var elapsed = (performance.now() - startTime) / 1000.0 * ${timeScale};
        
        gl.uniform1f(timeLocation, elapsed);
        gl.uniform2f(resolutionLocation, canvas.width, canvas.height);
        gl.uniform2f(mouseLocation, mouseX, mouseY);
        
        gl.clearColor(0, 0, 0, 0);
        gl.clear(gl.COLOR_BUFFER_BIT);
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
        
        animationId = requestAnimationFrame(render);
    }
    
    // Start rendering
    render();
    
    // Cleanup on page unload
    window.addEventListener('unload', function() {
        if (animationId) cancelAnimationFrame(animationId);
    });
    
    console.log('Aurora: WebGL renderer initialized successfully');
})();
"""
    }
}
