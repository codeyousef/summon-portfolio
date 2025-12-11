package code.yousef.portfolio.ui.aurora

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.runtime.LocalPlatformRenderer

/**
 * Hydration script for the Aurora WebGPU effect.
 * 
 * This script runs on the client-side after the page loads and initializes
 * the WebGPU aurora effect from serialized configuration in data attributes.
 * 
 * The script:
 * 1. Finds canvas elements with data-aurora-effect attributes
 * 2. Parses the effect configuration (palettes, interaction settings)
 * 3. Initializes WebGPU with the aurora shader
 * 4. Sets up mouse/keyboard interaction handlers
 * 5. Runs the render loop
 */
@Composable
fun AuroraHydrationScript() {
    val renderer = runCatching { LocalPlatformRenderer.current }.getOrNull() ?: return
    
    val hydrationScript = buildAuroraHydrationScript()
    
    renderer.renderHeadElements {
        script(
            src = null,
            content = hydrationScript,
            type = "module",
            async = false,
            defer = false,
            crossorigin = null
        )
    }
}

/**
 * Builds the client-side JavaScript for aurora effect hydration.
 */
private fun buildAuroraHydrationScript(): String = """
(async function initAuroraEffect() {
    // Wait for DOM to be ready
    if (document.readyState === 'loading') {
        await new Promise(resolve => document.addEventListener('DOMContentLoaded', resolve));
    }

    // Find all aurora effect canvases
    const canvases = document.querySelectorAll('canvas[data-aurora-effect]');
    if (canvases.length === 0) {
        console.log('Aurora: No effect canvases found');
        return;
    }

    // Check WebGPU support
    if (!navigator.gpu) {
        console.warn('Aurora: WebGPU not supported, effects will not render');
        canvases.forEach(canvas => canvas.style.display = 'none');
        return;
    }

    // Initialize each canvas
    for (const canvas of canvases) {
        try {
            const effectJson = canvas.getAttribute('data-aurora-effect');
            if (!effectJson) continue;

            const effectData = JSON.parse(effectJson);
            await initializeAurora(canvas, effectData);
            console.log('Aurora: Effect initialized for', canvas.id || 'canvas');
        } catch (error) {
            console.error('Aurora: Failed to initialize effect:', error);
            canvas.style.display = 'none';
        }
    }
})();

async function initializeAurora(canvas, config) {
    // Request WebGPU adapter and device
    const adapter = await navigator.gpu.requestAdapter({
        powerPreference: 'high-performance'
    });
    if (!adapter) throw new Error('No WebGPU adapter available');

    const device = await adapter.requestDevice();
    const context = canvas.getContext('webgpu');
    const format = navigator.gpu.getPreferredCanvasFormat();

    context.configure({
        device: device,
        format: format,
        alphaMode: 'premultiplied'
    });

    // Set canvas size with device pixel ratio
    const updateCanvasSize = () => {
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        canvas.width = canvas.clientWidth * dpr;
        canvas.height = canvas.clientHeight * dpr;
    };
    updateCanvasSize();
    window.addEventListener('resize', updateCanvasSize);

    // Aurora WGSL shader
    const shaderCode = `
        struct Uniforms {
            time: f32,
            noiseScale: f32,
            resolution: vec2<f32>,
            mouse: vec2<f32>,
            paletteA: vec3<f32>,
            _pad1: f32,
            paletteB: vec3<f32>,
            _pad2: f32,
            paletteC: vec3<f32>,
            _pad3: f32,
            paletteD: vec3<f32>,
            _pad4: f32,
        }

        @group(0) @binding(0) var<uniform> u: Uniforms;

        struct VertexOutput {
            @builtin(position) position: vec4<f32>,
            @location(0) uv: vec2<f32>,
        }

        @vertex
        fn vs_main(@builtin(vertex_index) vertexIndex: u32) -> VertexOutput {
            let x = f32((vertexIndex << 1u) & 2u);
            let y = f32(vertexIndex & 2u);
            var output: VertexOutput;
            output.position = vec4<f32>(x * 2.0 - 1.0, 1.0 - y * 2.0, 0.0, 1.0);
            output.uv = vec2<f32>(x, y);
            return output;
        }

        // Simplex noise helper functions
        fn mod289_3(x: vec3<f32>) -> vec3<f32> { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        fn mod289_4(x: vec4<f32>) -> vec4<f32> { return x - floor(x * (1.0 / 289.0)) * 289.0; }
        fn permute(x: vec4<f32>) -> vec4<f32> { return mod289_4(((x * 34.0) + 1.0) * x); }
        fn taylorInvSqrt(r: vec4<f32>) -> vec4<f32> { return 1.79284291400159 - 0.85373472095314 * r; }

        fn snoise(v: vec3<f32>) -> f32 {
            let C = vec2<f32>(1.0 / 6.0, 1.0 / 3.0);
            let D = vec4<f32>(0.0, 0.5, 1.0, 2.0);

            var i = floor(v + dot(v, vec3<f32>(C.y, C.y, C.y)));
            let x0 = v - i + dot(i, vec3<f32>(C.x, C.x, C.x));

            let g = step(x0.yzx, x0.xyz);
            let l = 1.0 - g;
            let i1 = min(g.xyz, l.zxy);
            let i2 = max(g.xyz, l.zxy);

            let x1 = x0 - i1 + C.x;
            let x2 = x0 - i2 + C.y;
            let x3 = x0 - D.yyy;

            i = mod289_3(i);
            let p = permute(permute(permute(
                i.z + vec4<f32>(0.0, i1.z, i2.z, 1.0))
                + i.y + vec4<f32>(0.0, i1.y, i2.y, 1.0))
                + i.x + vec4<f32>(0.0, i1.x, i2.x, 1.0));

            let n_ = 0.142857142857;
            let ns = n_ * D.wyz - D.xzx;

            let j = p - 49.0 * floor(p * ns.z * ns.z);

            let x_ = floor(j * ns.z);
            let y_ = floor(j - 7.0 * x_);

            let x = x_ * ns.x + ns.yyyy.x;
            let y = y_ * ns.x + ns.yyyy.x;
            let h = 1.0 - abs(x) - abs(y);

            let b0 = vec4<f32>(x.xy, y.xy);
            let b1 = vec4<f32>(x.zw, y.zw);

            let s0 = floor(b0) * 2.0 + 1.0;
            let s1 = floor(b1) * 2.0 + 1.0;
            let sh = -step(h, vec4<f32>(0.0, 0.0, 0.0, 0.0));

            let a0 = b0.xzyw + s0.xzyw * sh.xxyy;
            let a1 = b1.xzyw + s1.xzyw * sh.zzww;

            var p0 = vec3<f32>(a0.xy, h.x);
            var p1 = vec3<f32>(a0.zw, h.y);
            var p2 = vec3<f32>(a1.xy, h.z);
            var p3 = vec3<f32>(a1.zw, h.w);

            let norm = taylorInvSqrt(vec4<f32>(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
            p0 *= norm.x;
            p1 *= norm.y;
            p2 *= norm.z;
            p3 *= norm.w;

            var m = max(0.6 - vec4<f32>(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), vec4<f32>(0.0));
            m = m * m;
            return 42.0 * dot(m * m, vec4<f32>(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
        }

        // IQ's palette function
        fn palette(t: f32) -> vec3<f32> {
            return u.paletteA + u.paletteB * cos(6.28318 * (u.paletteC * t + u.paletteD));
        }

        fn fbm(p: vec3<f32>) -> f32 {
            var f = 0.0;
            var scale = u.noiseScale;
            var amp = 0.5;
            for (var i = 0; i < 5; i++) {
                f += amp * snoise(p * scale);
                scale *= 2.0;
                amp *= 0.5;
            }
            return f;
        }

        @fragment
        fn fs_main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
            let aspect = u.resolution.x / u.resolution.y;
            var p = uv;
            p.x *= aspect;

            let t = u.time * 0.1;
            
            // Create flowing aurora bands
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
            let mouseDist = length(uv - u.mouse);
            let mouseGlow = exp(-mouseDist * 3.0) * 0.15;
            color += palette(u.time * 0.05) * mouseGlow;

            // Soft fade at edges
            let edgeFade = smoothstep(0.0, 0.1, uv.y) * smoothstep(1.0, 0.9, uv.y);
            color *= edgeFade;

            let alpha = max(max(aurora1, aurora2), aurora3) * 0.8;
            return vec4<f32>(color, alpha * edgeFade);
        }
    `;

    // Create uniform buffer (96 bytes = 24 floats aligned)
    const uniformBufferSize = 96;
    const uniformBuffer = device.createBuffer({
        size: uniformBufferSize,
        usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST
    });

    // Create shader module
    const shaderModule = device.createShaderModule({ code: shaderCode });

    // Create bind group layout
    const bindGroupLayout = device.createBindGroupLayout({
        entries: [{
            binding: 0,
            visibility: GPUShaderStage.VERTEX | GPUShaderStage.FRAGMENT,
            buffer: { type: 'uniform' }
        }]
    });

    // Create pipeline
    const pipeline = device.createRenderPipeline({
        layout: device.createPipelineLayout({ bindGroupLayouts: [bindGroupLayout] }),
        vertex: { module: shaderModule, entryPoint: 'vs_main' },
        fragment: {
            module: shaderModule,
            entryPoint: 'fs_main',
            targets: [{
                format,
                blend: {
                    color: { srcFactor: 'src-alpha', dstFactor: 'one-minus-src-alpha' },
                    alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha' }
                }
            }]
        },
        primitive: { topology: 'triangle-list' }
    });

    // Create bind group
    const bindGroup = device.createBindGroup({
        layout: bindGroupLayout,
        entries: [{ binding: 0, resource: { buffer: uniformBuffer } }]
    });

    // State
    const startTime = performance.now();
    let mouseX = 0.5, mouseY = 0.5;
    let currentPaletteIndex = config.initialPaletteIndex || 0;
    const palettes = config.palettes || [];
    const timeScale = config.timeScale || 1.0;
    const noiseScale = config.noiseScale || 1.0;

    // Get current palette
    const getPalette = () => palettes[currentPaletteIndex] || palettes[0] || {
        a: [0.5, 0.5, 0.5], b: [0.5, 0.5, 0.5], c: [1.0, 1.0, 1.0], d: [0.0, 0.33, 0.67]
    };

    // Interaction handlers
    if (config.enableMouse) {
        document.addEventListener('mousemove', (e) => {
            mouseX = e.clientX / window.innerWidth;
            mouseY = e.clientY / window.innerHeight;
        });
    }

    if (config.enableKeyboard) {
        document.addEventListener('keydown', (e) => {
            const key = parseInt(e.key);
            if (key >= 1 && key <= palettes.length) {
                currentPaletteIndex = key - 1;
            }
        });
    }

    if (config.enableClick) {
        canvas.style.pointerEvents = 'auto';
        canvas.addEventListener('click', () => {
            currentPaletteIndex = (currentPaletteIndex + 1) % palettes.length;
        });
    }

    // Animation loop
    const render = () => {
        const currentTime = (performance.now() - startTime) / 1000.0 * timeScale;
        const palette = getPalette();

        // Update uniforms (must match struct layout in shader)
        const uniformData = new Float32Array([
            currentTime, noiseScale, 0.0, 0.0,           // time, noiseScale, padding
            canvas.width, canvas.height, 0.0, 0.0,       // resolution + padding (to align vec2)
            mouseX, mouseY, 0.0, 0.0,                    // mouse + padding
            ...palette.a, 0.0,                            // paletteA
            ...palette.b, 0.0,                            // paletteB
            ...palette.c, 0.0,                            // paletteC
            ...palette.d, 0.0                             // paletteD
        ]);
        
        // Fix uniform layout to match shader struct
        const fixedData = new Float32Array([
            currentTime, noiseScale,                      // time, noiseScale
            canvas.width, canvas.height,                  // resolution
            mouseX, mouseY, 0.0, 0.0,                    // mouse + padding to align vec3
            ...palette.a, 0.0,                            // paletteA + pad
            ...palette.b, 0.0,                            // paletteB + pad
            ...palette.c, 0.0,                            // paletteC + pad
            ...palette.d, 0.0                             // paletteD + pad
        ]);
        device.queue.writeBuffer(uniformBuffer, 0, fixedData);

        // Render
        const commandEncoder = device.createCommandEncoder();
        const renderPass = commandEncoder.beginRenderPass({
            colorAttachments: [{
                view: context.getCurrentTexture().createView(),
                clearValue: { r: 0, g: 0, b: 0, a: 0 },
                loadOp: 'clear',
                storeOp: 'store'
            }]
        });

        renderPass.setPipeline(pipeline);
        renderPass.setBindGroup(0, bindGroup);
        renderPass.draw(3);
        renderPass.end();

        device.queue.submit([commandEncoder.finish()]);
        requestAnimationFrame(render);
    };

    render();
}
""".trimIndent()
