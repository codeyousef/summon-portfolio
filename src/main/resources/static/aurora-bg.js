/* ================== Aurora BG (WebGL) ================== */
const canvas = document.getElementById('gl');
let gl = canvas.getContext('webgl', {antialias: false, alpha: false});
let dpr = Math.min(2.5, window.devicePixelRatio || 1);
const mouse = {x: 0, y: 0};
let startTime = performance.now();
let paletteIndex = 0;

const CANVAS_HEIGHT = 3500;

const palettes = [
    {a: [0.09, 0.06, 0.08], b: [0.55, 0.08, 0.19], c: [0.9, 0.22, 0.39], d: [0.98, 0.72, 0.83]},
    {a: [0.05, 0.06, 0.08], b: [0.1, 0.4, 0.6], c: [0.9, 0.2, 0.8], d: [0.2, 0.9, 0.8]},
    {a: [0.08, 0.08, 0.12], b: [0.1, 0.2, 0.7], c: [0.8, 0.4, 0.9], d: [0.2, 0.5, 0.9]}
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

function compile(type, src) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, src);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
        throw new Error(gl.getShaderInfoLog(shader));
    }
    return shader;
}

function program(vs, fs) {
    const prog = gl.createProgram();
    gl.attachShader(prog, compile(gl.VERTEX_SHADER, vs));
    gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, fs));
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
        throw new Error(gl.getProgramInfoLog(prog));
    }
    return prog;
}

let prog, buf, ur, ut, um, uA, uB, uC, uD;

function resize() {
    if (!gl) return;
    const w = Math.floor(window.innerWidth * dpr);
    const h = Math.floor(CANVAS_HEIGHT * dpr);
    canvas.width = w;
    canvas.height = h;
    gl.viewport(0, 0, w, h);
    gl.uniform2f(ur, w, h);
}

function init() {
    if (!gl) {
        console.warn('WebGL disabled; static gradient only');
        return;
    }
    prog = program(vert, frag);
    gl.useProgram(prog);
    buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, -1, 1, 1, -1, 1, 1]), gl.STATIC_DRAW);
    const loc = gl.getAttribLocation(prog, 'p');
    gl.enableVertexAttribArray(loc);
    gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
    ur = gl.getUniformLocation(prog, 'r');
    ut = gl.getUniformLocation(prog, 't');
    um = gl.getUniformLocation(prog, 'm');
    uA = gl.getUniformLocation(prog, 'A');
    uB = gl.getUniformLocation(prog, 'B');
    uC = gl.getUniformLocation(prog, 'C');
    uD = gl.getUniformLocation(prog, 'D');
    resize();
    setPalette(0);
    requestAnimationFrame(loop);
}

function setPalette(i) {
    paletteIndex = (i + palettes.length) % palettes.length;
    const P = palettes[paletteIndex];
    gl.useProgram(prog);
    gl.uniform3fv(uA, P.a);
    gl.uniform3fv(uB, P.b);
    gl.uniform3fv(uC, P.c);
    gl.uniform3fv(uD, P.d);
    const pill = document.getElementById('palette');
    if (pill) {
        pill.textContent = `Palette ▸ ${['Maroon', 'Neon', 'Royal'][paletteIndex]}`;
    }
}

function loop(now) {
    if (!gl) return;
    const t = (now - startTime) / 1000;
    gl.uniform1f(ut, t);
    gl.uniform2f(um, mouse.x || window.innerWidth * dpr * 0.5, mouse.y || window.innerHeight * dpr * 0.5);
    gl.drawArrays(gl.TRIANGLES, 0, 6);
    requestAnimationFrame(loop);
}

window.addEventListener('resize', resize, {passive: true});
window.addEventListener('mousemove', (e) => {
    mouse.x = e.clientX * dpr;
    mouse.y = (window.innerHeight - e.clientY) * dpr;
}, {passive: true});
document.getElementById('palette')?.addEventListener('click', () => setPalette(paletteIndex + 1));
window.addEventListener('keydown', (e) => {
    if (e.code !== 'Space') {
        return;
    }

    const active = document.activeElement;
    const activeTag = typeof active?.tagName === 'string' ? active.tagName.toLowerCase() : '';
    const targetTag = typeof e.target?.tagName === 'string' ? e.target.tagName.toLowerCase() : '';
    const typingContext =
        activeTag === 'input' ||
        activeTag === 'textarea' ||
        activeTag === 'button' ||
        targetTag === 'input' ||
        targetTag === 'textarea' ||
        targetTag === 'button' ||
        active?.isContentEditable === true ||
        e.target?.isContentEditable === true;

    if (typingContext || window.location.pathname.startsWith('/admin')) {
        // Allow native spacebar behavior (typing, scrolling, etc.)
        return;
    }

    e.preventDefault();
    setPalette(paletteIndex + 1);
});

init();

/* ================== Shared page interactions ================== */
const setCurrentYear = () => {
    const year = document.getElementById('year');
    if (year) {
        year.textContent = new Date().getFullYear().toString();
    }
};

const wireDataHrefLinks = () => {
    document.querySelectorAll('[data-href]').forEach((element) => {
        // Ignore buttons to prevent conflict with interactive elements like HamburgerMenu
        if (element.tagName === 'BUTTON') return;

        element.addEventListener('click', (event) => {
            if (event.defaultPrevented) return;
            
            const target = event.currentTarget;
            const rawHref = target.getAttribute('data-href') || target.getAttribute('href');
            if (!rawHref) return;

            let hashTarget = null;
            if (rawHref.startsWith('#')) {
                hashTarget = rawHref;
            } else {
                const hashIndex = rawHref.indexOf('#');
                if (hashIndex >= 0) {
                    hashTarget = rawHref.slice(hashIndex);
                }
            }

            if (target.tagName === 'A') {
                event.preventDefault();
            }

            if (hashTarget) {
                const section = document.querySelector(hashTarget);
                if (section) {
                    section.scrollIntoView({behavior: 'smooth', block: 'start'});
                    return;
                }
            }

            window.location.href = rawHref;
        });
    });
};

const adoptLinkLabels = () => {
    document.querySelectorAll('a').forEach((link) => {
        if (link.textContent.trim().length > 0) return;
        const sibling = link.nextElementSibling;
        if (!sibling || sibling.tagName !== 'SPAN') return;
        if (!sibling.textContent.trim().length) return;
        link.appendChild(sibling);
    });
};

const wireCopyButtons = () => {
    document.querySelectorAll('[data-copy]').forEach((button) => {
        button.addEventListener('click', () => {
            const pre = button.parentElement;
            const text = pre?.innerText?.trim();
            if (!text) return;
            navigator.clipboard.writeText(text).then(() => {
                button.textContent = 'Copied';
                setTimeout(() => (button.textContent = 'Copy'), 1200);
            });
        });
    });
};

const initPageInteractions = () => {
    setCurrentYear();
    wireDataHrefLinks();
    adoptLinkLabels();
    wireCopyButtons();
};

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initPageInteractions, {once: true});
} else {
    initPageInteractions();
}
