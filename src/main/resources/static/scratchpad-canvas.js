/**
 * Scratchpad Infinite Canvas
 * Handles pan (drag) and zoom (scroll wheel) functionality
 */
(function () {
    'use strict';

    let canvas = null;
    let wrapper = null;

    // Transform state
    let scale = 1;
    let translateX = -4500; // Start centered on content
    let translateY = -4500;

    // Drag state
    let isDragging = false;
    let startX = 0;
    let startY = 0;
    let startTranslateX = 0;
    let startTranslateY = 0;

    // Limits
    const MIN_SCALE = 0.25;
    const MAX_SCALE = 2;
    const ZOOM_SENSITIVITY = 0.001;

    function init() {
        canvas = document.getElementById('infinite-canvas');
        wrapper = document.getElementById('canvas-wrapper');

        if (!canvas || !wrapper) {
            console.warn('Scratchpad canvas elements not found');
            return;
        }

        // Apply initial transform
        updateTransform();

        // Event listeners
        wrapper.addEventListener('mousedown', onMouseDown);
        window.addEventListener('mousemove', onMouseMove);
        window.addEventListener('mouseup', onMouseUp);
        wrapper.addEventListener('wheel', onWheel, {passive: false});

        // Touch support
        wrapper.addEventListener('touchstart', onTouchStart, {passive: false});
        wrapper.addEventListener('touchmove', onTouchMove, {passive: false});
        wrapper.addEventListener('touchend', onTouchEnd);

        // Prevent context menu on canvas
        wrapper.addEventListener('contextmenu', (e) => e.preventDefault());

        console.log('%c[scratchpad] Canvas initialized', 'color: #00ff00;');
    }

    function updateTransform() {
        if (!canvas) return;
        canvas.style.transform = `translate(${translateX}px, ${translateY}px) scale(${scale})`;
    }

    // Mouse events
    function onMouseDown(e) {
        // Only left click
        if (e.button !== 0) return;

        // Don't drag if clicking on interactive elements
        if (e.target.closest('button, a, input, .sticky-note, .tombstone, .dont-click-btn')) {
            return;
        }

        isDragging = true;
        startX = e.clientX;
        startY = e.clientY;
        startTranslateX = translateX;
        startTranslateY = translateY;

        wrapper.style.cursor = 'grabbing';
    }

    function onMouseMove(e) {
        if (!isDragging) return;

        const dx = e.clientX - startX;
        const dy = e.clientY - startY;

        translateX = startTranslateX + dx;
        translateY = startTranslateY + dy;

        updateTransform();
    }

    function onMouseUp() {
        if (isDragging) {
            isDragging = false;
            wrapper.style.cursor = 'grab';
        }
    }

    // Wheel zoom
    function onWheel(e) {
        e.preventDefault();

        const delta = -e.deltaY * ZOOM_SENSITIVITY;
        const newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale + delta));

        if (newScale !== scale) {
            // Get mouse position relative to wrapper
            const rect = wrapper.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;

            // Calculate the point on canvas under the mouse
            const canvasX = (mouseX - translateX) / scale;
            const canvasY = (mouseY - translateY) / scale;

            // Update scale
            scale = newScale;

            // Adjust translate to keep the same point under the mouse
            translateX = mouseX - canvasX * scale;
            translateY = mouseY - canvasY * scale;

            updateTransform();
        }
    }

    // Touch events
    let lastTouchDistance = 0;
    let lastTouchCenter = {x: 0, y: 0};

    function onTouchStart(e) {
        if (e.touches.length === 1) {
            // Single touch - pan
            const touch = e.touches[0];
            startX = touch.clientX;
            startY = touch.clientY;
            startTranslateX = translateX;
            startTranslateY = translateY;
            isDragging = true;
        } else if (e.touches.length === 2) {
            // Two touches - pinch zoom
            e.preventDefault();
            isDragging = false;
            lastTouchDistance = getTouchDistance(e.touches);
            lastTouchCenter = getTouchCenter(e.touches);
        }
    }

    function onTouchMove(e) {
        if (e.touches.length === 1 && isDragging) {
            const touch = e.touches[0];
            const dx = touch.clientX - startX;
            const dy = touch.clientY - startY;

            translateX = startTranslateX + dx;
            translateY = startTranslateY + dy;

            updateTransform();
        } else if (e.touches.length === 2) {
            e.preventDefault();

            const distance = getTouchDistance(e.touches);
            const center = getTouchCenter(e.touches);

            // Calculate zoom
            const zoomDelta = (distance - lastTouchDistance) * 0.005;
            const newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale + zoomDelta));

            if (newScale !== scale) {
                // Zoom toward the center of the two touches
                const rect = wrapper.getBoundingClientRect();
                const centerX = center.x - rect.left;
                const centerY = center.y - rect.top;

                const canvasX = (centerX - translateX) / scale;
                const canvasY = (centerY - translateY) / scale;

                scale = newScale;

                translateX = centerX - canvasX * scale;
                translateY = centerY - canvasY * scale;

                updateTransform();
            }

            lastTouchDistance = distance;
            lastTouchCenter = center;
        }
    }

    function onTouchEnd(e) {
        if (e.touches.length === 0) {
            isDragging = false;
        } else if (e.touches.length === 1) {
            // Switched from pinch to pan
            const touch = e.touches[0];
            startX = touch.clientX;
            startY = touch.clientY;
            startTranslateX = translateX;
            startTranslateY = translateY;
            isDragging = true;
        }
    }

    function getTouchDistance(touches) {
        const dx = touches[0].clientX - touches[1].clientX;
        const dy = touches[0].clientY - touches[1].clientY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    function getTouchCenter(touches) {
        return {
            x: (touches[0].clientX + touches[1].clientX) / 2,
            y: (touches[0].clientY + touches[1].clientY) / 2
        };
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
