(function () {
    'use strict';

    var containerSelector = '#fifth-wall-scene-container';
    var elementId = 'fifth-wall-fps';
    var sampleMs = 500;
    var started = false;

    function rendererLabel() {
        var hydrator = window.SigilEffectHydrator;
        if (!hydrator || typeof hydrator.getAvailableRenderer !== 'function') {
            return '';
        }
        var renderer = hydrator.getAvailableRenderer();
        return renderer ? ' / ' + String(renderer).toUpperCase() : '';
    }

    function startCounter(element) {
        if (started) {
            return;
        }
        started = true;

        var frames = 0;
        var lastSample = performance.now();

        function tick(now) {
            frames += 1;
            var elapsed = now - lastSample;
            if (elapsed >= sampleMs) {
                var fps = Math.max(0, Math.round((frames * 1000) / elapsed));
                element.textContent = 'FPS ' + fps + rendererLabel();
                element.dataset.fps = String(fps);
                frames = 0;
                lastSample = now;
            }
            requestAnimationFrame(tick);
        }

        requestAnimationFrame(tick);
    }

    function mountCounter() {
        var container = document.querySelector(containerSelector);
        if (!container) {
            return false;
        }

        var element = document.getElementById(elementId);
        if (!element) {
            element = document.createElement('div');
            element.id = elementId;
            element.textContent = 'FPS --';
            element.setAttribute('aria-label', 'Frame rate');
            element.setAttribute('role', 'status');
            container.appendChild(element);
        }

        startCounter(element);
        return true;
    }

    function boot() {
        var attempts = 0;

        function tryMount() {
            if (mountCounter()) {
                return;
            }
            attempts += 1;
            if (attempts < 120) {
                window.setTimeout(tryMount, 100);
            }
        }

        tryMount();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot, { once: true });
    } else {
        boot();
    }
})();
