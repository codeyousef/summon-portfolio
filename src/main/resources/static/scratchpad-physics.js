/**
 * Scratchpad Physics Engine
 * Spawns colorful bouncing balls when the "DON'T CLICK" button is clicked
 */
(function () {
    'use strict';

    let canvas = null;
    let ctx = null;
    let balls = [];
    let animationId = null;
    let isInitialized = false;

    const COLORS = [
        '#ff0000', // Red
        '#00ff00', // Green
        '#0000ff', // Blue
        '#ffff00', // Yellow
        '#ff00ff', // Magenta
        '#00ffff', // Cyan
        '#ff6600', // Orange
        '#9900ff', // Purple
        '#ff0066', // Pink
        '#00ff66'  // Lime
    ];

    const GRAVITY = 0.3;
    const FRICTION = 0.99;
    const BOUNCE = 0.8;
    const MIN_RADIUS = 10;
    const MAX_RADIUS = 30;
    const SPAWN_COUNT = 5;
    const MAX_BALLS = 100;

    class Ball {
        constructor(x, y) {
            this.x = x;
            this.y = y;
            this.vx = (Math.random() - 0.5) * 20;
            this.vy = (Math.random() - 0.5) * 20 - 10;
            this.radius = MIN_RADIUS + Math.random() * (MAX_RADIUS - MIN_RADIUS);
            this.color = COLORS[Math.floor(Math.random() * COLORS.length)];
            this.alpha = 1;
            this.decay = 0.001 + Math.random() * 0.002;
        }

        update(width, height) {
            // Apply gravity
            this.vy += GRAVITY;

            // Apply friction
            this.vx *= FRICTION;
            this.vy *= FRICTION;

            // Update position
            this.x += this.vx;
            this.y += this.vy;

            // Bounce off walls
            if (this.x - this.radius < 0) {
                this.x = this.radius;
                this.vx *= -BOUNCE;
            } else if (this.x + this.radius > width) {
                this.x = width - this.radius;
                this.vx *= -BOUNCE;
            }

            // Bounce off floor (leave space for terminal)
            const floorY = height - 200; // Terminal height
            if (this.y + this.radius > floorY) {
                this.y = floorY - this.radius;
                this.vy *= -BOUNCE;

                // Stop bouncing if velocity is very low
                if (Math.abs(this.vy) < 1) {
                    this.vy = 0;
                }
            }

            // Bounce off ceiling
            if (this.y - this.radius < 0) {
                this.y = this.radius;
                this.vy *= -BOUNCE;
            }

            // Decay alpha
            this.alpha -= this.decay;
        }

        draw(ctx) {
            ctx.beginPath();
            ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
            ctx.fillStyle = this.color;
            ctx.globalAlpha = this.alpha;
            ctx.fill();

            // Add a highlight
            ctx.beginPath();
            ctx.arc(
                this.x - this.radius * 0.3,
                this.y - this.radius * 0.3,
                this.radius * 0.3,
                0,
                Math.PI * 2
            );
            ctx.fillStyle = 'rgba(255, 255, 255, 0.3)';
            ctx.fill();

            ctx.globalAlpha = 1;
        }

        isDead() {
            return this.alpha <= 0;
        }
    }

    function init() {
        const container = document.getElementById('physics-canvas-container');
        if (!container) {
            console.warn('Physics canvas container not found');
            return;
        }

        // Create canvas
        canvas = document.createElement('canvas');
        canvas.id = 'physics-canvas';
        canvas.style.cssText = 'position: absolute; top: 0; left: 0; width: 100%; height: 100%;';
        container.appendChild(canvas);

        ctx = canvas.getContext('2d');

        // Handle resize
        function resize() {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        }

        resize();
        window.addEventListener('resize', resize);

        // Set up button click handler
        const button = document.getElementById('dont-click-btn');
        if (button) {
            button.addEventListener('click', (e) => {
                e.stopPropagation();
                spawnBalls(e.clientX, e.clientY);
            });
        }

        isInitialized = true;
        console.log('%c[scratchpad] Physics engine initialized', 'color: #00ff00;');
    }

    function spawnBalls(x, y) {
        // Limit total balls
        if (balls.length >= MAX_BALLS) {
            balls.splice(0, SPAWN_COUNT);
        }

        for (let i = 0; i < SPAWN_COUNT; i++) {
            balls.push(new Ball(x, y));
        }

        // Start animation if not running
        if (!animationId) {
            animate();
        }

        // Play a sound effect (optional visual feedback)
        flashScreen();
    }

    function flashScreen() {
        const flash = document.createElement('div');
        flash.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(255, 0, 0, 0.1);
            pointer-events: none;
            z-index: 8999;
            animation: flash-fade 0.2s ease-out forwards;
        `;

        // Add animation style if not exists
        if (!document.getElementById('flash-style')) {
            const style = document.createElement('style');
            style.id = 'flash-style';
            style.textContent = `
                @keyframes flash-fade {
                    from { opacity: 1; }
                    to { opacity: 0; }
                }
            `;
            document.head.appendChild(style);
        }

        document.body.appendChild(flash);
        setTimeout(() => flash.remove(), 200);
    }

    function animate() {
        if (!canvas || !ctx) return;

        // Clear canvas
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // Update and draw balls
        balls = balls.filter(ball => {
            ball.update(canvas.width, canvas.height);

            if (ball.isDead()) {
                return false;
            }

            ball.draw(ctx);
            return true;
        });

        // Continue animation if balls exist
        if (balls.length > 0) {
            animationId = requestAnimationFrame(animate);
        } else {
            animationId = null;
        }
    }

    // Expose for external triggering
    window.scratchpadPhysics = {
        spawn: spawnBalls,
        clear: () => {
            balls = [];
            if (ctx && canvas) {
                ctx.clearRect(0, 0, canvas.width, canvas.height);
            }
        }
    };

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
