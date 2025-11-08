package code.yousef.portfolio.ui.foundation

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.foundation.Canvas
import code.yousef.summon.components.foundation.ScriptTag
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.minHeight

@Composable
fun PageScaffold(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    content: () -> Unit
) {
    val scaffoldModifier = modifier
        .minHeight("100vh")
        .backgroundColor(PortfolioTheme.Colors.BACKGROUND)
        .style(
            "background",
            "radial-gradient(1200px 900px at 25% 12%, #15161c 0%, ${PortfolioTheme.Colors.BACKGROUND} 55%), ${PortfolioTheme.Colors.BACKGROUND_ALT}"
        )
        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
        .fontFamily(PortfolioTheme.Typography.FONT_SANS)
        .position(Position.Relative)
        .overflow(Overflow.Hidden)
        .attribute("lang", locale.code)
        .attribute("dir", locale.direction)

    Box(modifier = scaffoldModifier) {
        WebGlCanvas()
        GlowLayer()
        GrainLayer()
        Column(
            modifier = Modifier()
                .position(Position.Relative)
                .zIndex(2)
                .padding(PortfolioTheme.Spacing.xl)
                .gap(PortfolioTheme.Spacing.xl)
        ) {
            content()
        }
        WebGlScript()
    }
}

@Composable
private fun WebGlCanvas() {
    Canvas(
        id = "gl",
        width = 1920,
        height = 1080,
        modifier = Modifier()
            .position(Position.Fixed)
            .inset("0")
            .style("width", "100%")
            .style("height", "100%")
            .pointerEvents(PointerEvents.None)
            .zIndex(0)
    )
}

@Composable
private fun GlowLayer() {
    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .inset("0")
            .backgroundLayers {
                radialGradient {
                    shape(RadialGradientShape.Circle)
                    size("1200px", "900px")
                    position("25%", "20%")
                    colorStop("rgba(255,59,106,0.28)", "0%")
                    colorStop("transparent", "55%")
                }
                radialGradient {
                    shape(RadialGradientShape.Circle)
                    size("1200px", "900px")
                    position("80%", "90%")
                    colorStop("rgba(176,18,53,0.22)", "0%")
                    colorStop("transparent", "48%")
                }
            }
            .filter {
                blur(20)
                saturate(1.2)
            }
            .mixBlendMode(BlendMode.Screen)
            .opacity(0.9F)
            .zIndex(0)
            .pointerEvents(PointerEvents.None)
    ) {}
}

@Composable
private fun GrainLayer() {
    Box(
        modifier = Modifier()
            .position(Position.Fixed)
            .inset("-20vmax")
            .opacity(0.06F)
            .className("grain")
            .pointerEvents(PointerEvents.None)
            .mixBlendMode(BlendMode.Multiply)
            .backgroundLayers {
                image("repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(0,0,0,0.35) 3px, transparent 4px)")
            }
            .zIndex(1)
    ) {}
}

@Composable
private fun WebGlScript() {
    ScriptTag(
        id = "aurora-gl-script",
        inlineContent = AURORA_SCRIPT,
        modifier = Modifier()
            .style("display", "none")
    )
}

private const val AURORA_SCRIPT = """
/* ================== Aurora BG (WebGL) ================== */
const canvas = document.getElementById('gl');
let gl = canvas?.getContext('webgl', {antialias:false, alpha:false});
let dpr = Math.min(2.5, window.devicePixelRatio || 1);
const mouse = {x:0, y:0};
let startTime = performance.now();
let paletteIndex = 0;

const palettes = [
  { a:[0.09,0.06,0.08], b:[0.55,0.08,0.19], c:[0.9,0.22,0.39], d:[0.98,0.72,0.83] },
  { a:[0.05,0.06,0.08], b:[0.1,0.4,0.6],  c:[0.9,0.2,0.8],   d:[0.2,0.9,0.8]   },
  { a:[0.08,0.08,0.12], b:[0.1,0.2,0.7],  c:[0.8,0.4,0.9],   d:[0.2,0.5,0.9]   }
];

const vert = `attribute vec2 p; void main(){ gl_Position = vec4(p,0.0,1.0); }`;
const frag = `
precision highp float; uniform vec2 r; uniform float t; uniform vec2 m; uniform vec3 A; uniform vec3 B; uniform vec3 C; uniform vec3 D;
vec3 pal(float x, vec3 a, vec3 b, vec3 c, vec3 d){ return a + b*cos(6.28318*(c*x+d)); }
float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7)))*43758.5453); }
float noise(in vec2 p){ vec2 i=floor(p), f=fract(p); vec2 u=f*f*(3.0-2.0*f); return mix(mix(hash(i+vec2(0,0)), hash(i+vec2(1,0)), u.x), mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), u.x), u.y); }
float fbm(vec2 p){ float s=0.0, a=0.5; for(int i=0;i<5;i++){ s+=a*noise(p); p*=2.02; a*=0.5; } return s; }
void main(){ vec2 uv = (gl_FragCoord.xy - 0.5*r.xy)/r.y; vec2 bend = (m - r*0.5)/r.y; uv += bend*0.25; float l = length(uv); float ang = atan(uv.y, uv.x);
  float bands = fbm(uv*2.0 + vec2(sin(t*0.1+ang*0.7), cos(t*0.13-ang*0.5))*0.4);
  float glow = smoothstep(0.9, 0.2, l) * 0.9 + 0.2*bands; float x = bands*1.3 + l*0.2 + sin(t*0.07)*0.05;
  vec3 col = pal(x, A, B, C, D); col *= mix(1.0, 0.3, smoothstep(0.6, 1.2, l)); col += pow(glow, 3.0)*0.12; gl_FragColor = vec4(col,1.0); }`;

function compile(type, src){ const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); if(!gl.getShaderParameter(s, gl.COMPILE_STATUS)){ throw new Error(gl.getShaderInfoLog(s)); } return s; }
function program(vs, fs){ const p = gl.createProgram(); gl.attachShader(p, compile(gl.VERTEX_SHADER, vs)); gl.attachShader(p, compile(gl.FRAGMENT_SHADER, fs)); gl.linkProgram(p); if(!gl.getProgramParameter(p, gl.LINK_STATUS)){ throw new Error(gl.getProgramInfoLog(p)); } return p; }

let prog, buf, ur, ut, um, uA, uB, uC, uD;
function resize(){ if(!gl || !canvas) return; const w = Math.floor(window.innerWidth * dpr), h = Math.floor(window.innerHeight * dpr); canvas.width = w; canvas.height = h; gl.viewport(0,0,w,h); gl.uniform2f(ur, w, h); }
function init(){ if(!gl || !canvas){ console.warn('WebGL disabled; static gradient only'); return; }
  prog = program(vert, frag); gl.useProgram(prog);
  buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 1,-1, -1,1,  -1,1, 1,-1, 1,1]), gl.STATIC_DRAW);
  const loc = gl.getAttribLocation(prog, 'p'); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
  ur = gl.getUniformLocation(prog, 'r'); ut = gl.getUniformLocation(prog, 't'); um = gl.getUniformLocation(prog, 'm');
  uA = gl.getUniformLocation(prog, 'A'); uB = gl.getUniformLocation(prog, 'B'); uC = gl.getUniformLocation(prog, 'C'); uD = gl.getUniformLocation(prog, 'D');
  resize(); setPalette(0); requestAnimationFrame(loop);
}
function setPalette(i){ paletteIndex = (i+palettes.length)%palettes.length; const P = palettes[paletteIndex]; gl.useProgram(prog); gl.uniform3fv(uA, P.a); gl.uniform3fv(uB, P.b); gl.uniform3fv(uC, P.c); gl.uniform3fv(uD, P.d); const pill = document.getElementById('palette'); if(pill){ pill.textContent = `Palette ▸ ${'$'}{['Maroon','Neon','Royal'][paletteIndex]}`; } }
function loop(now){ if(!gl) return; const t = (now - startTime)/1000; gl.uniform1f(ut, t); gl.uniform2f(um, mouse.x || window.innerWidth*dpr*0.5, mouse.y || window.innerHeight*dpr*0.5); gl.drawArrays(gl.TRIANGLES, 0, 6); requestAnimationFrame(loop); }

window.addEventListener('resize', resize, {passive:true});
window.addEventListener('mousemove', (e)=>{ mouse.x = e.clientX * dpr; mouse.y = (window.innerHeight - e.clientY) * dpr; }, {passive:true});
document.getElementById('palette')?.addEventListener('click', ()=> setPalette(paletteIndex+1));
window.addEventListener('keydown', (e)=>{ if(e.code==='Space'){ e.preventDefault(); setPalette(paletteIndex+1); }});

init();

/* ================== Page interactions ================== */
const year = document.getElementById('year'); if(year) year.textContent = new Date().getFullYear();
for(const b of document.querySelectorAll('[data-href]')) b.addEventListener('click', e=>{ const href = e.currentTarget.getAttribute('data-href'); if(href?.startsWith('#')){ document.querySelector(href)?.scrollIntoView({behavior:'smooth'}); } else if(href){ window.open(href, '_blank'); }});

for(const btn of document.querySelectorAll('[data-copy]')){
  btn.addEventListener('click', ()=>{
    const pre = btn.parentElement; const text = pre.innerText.trim();
    navigator.clipboard.writeText(text).then(()=>{ btn.textContent='Copied'; setTimeout(()=> btn.textContent='Copy', 1200); });
  });
}

document.getElementById('copyEmail')?.addEventListener('click', ()=>{
  const email = 'yousef@example.com';
  navigator.clipboard.writeText(email).then(()=>{
    const b = document.getElementById('copyEmail'); if(b){ b.textContent='Email copied'; setTimeout(()=> b.textContent='Copy Email', 1200); }
  });
});
"""
