/**
 * Scratchpad Sticky Note Drag-and-Drop
 * Allows sticky notes to be repositioned on the infinite canvas.
 * Positions are persisted to localStorage.
 */
(function () {
    'use strict';

    const STORAGE_KEY = 'scratchpad-sticky-positions';

    let dragState = null; // { el, startX, startY, origLeft, origTop }

    function getCanvasScale() {
        var canvas = document.getElementById('infinite-canvas');
        if (!canvas) return 1;
        var transform = canvas.style.transform || '';
        var match = transform.match(/scale\(([\d.]+)\)/);
        return match ? parseFloat(match[1]) : 1;
    }

    function loadPositions() {
        try {
            var raw = localStorage.getItem(STORAGE_KEY);
            return raw ? JSON.parse(raw) : {};
        } catch (_) {
            return {};
        }
    }

    function savePositions(positions) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(positions));
        } catch (_) {
            // Storage full or unavailable
        }
    }

    function getAllNotePositions() {
        var positions = {};
        var notes = document.querySelectorAll('.sticky-note[data-note-id]');
        notes.forEach(function (note) {
            var id = note.getAttribute('data-note-id');
            if (id) {
                positions[id] = {
                    left: note.style.left,
                    top: note.style.top
                };
            }
        });
        return positions;
    }

    function restorePositions() {
        var positions = loadPositions();
        var notes = document.querySelectorAll('.sticky-note[data-note-id]');
        notes.forEach(function (note) {
            var id = note.getAttribute('data-note-id');
            if (id && positions[id]) {
                note.style.left = positions[id].left;
                note.style.top = positions[id].top;
            }
        });
    }

    // Mouse events
    function onMouseDown(e) {
        var note = e.target.closest('.sticky-note[data-note-id]');
        if (!note) return;

        e.stopPropagation();
        e.preventDefault();

        dragState = {
            el: note,
            startX: e.clientX,
            startY: e.clientY,
            origLeft: parseInt(note.style.left, 10) || 0,
            origTop: parseInt(note.style.top, 10) || 0
        };
        note.style.cursor = 'grabbing';
        note.style.zIndex = '200';
    }

    function onMouseMove(e) {
        if (!dragState) return;

        var scale = getCanvasScale();
        var dx = (e.clientX - dragState.startX) / scale;
        var dy = (e.clientY - dragState.startY) / scale;

        dragState.el.style.left = (dragState.origLeft + dx) + 'px';
        dragState.el.style.top = (dragState.origTop + dy) + 'px';
    }

    function onMouseUp() {
        if (!dragState) return;

        dragState.el.style.cursor = '';
        dragState.el.style.zIndex = '';
        dragState = null;

        savePositions(getAllNotePositions());
    }

    // Touch events
    function onTouchStart(e) {
        var note = e.target.closest('.sticky-note[data-note-id]');
        if (!note) return;

        e.stopPropagation();

        var touch = e.touches[0];
        dragState = {
            el: note,
            startX: touch.clientX,
            startY: touch.clientY,
            origLeft: parseInt(note.style.left, 10) || 0,
            origTop: parseInt(note.style.top, 10) || 0
        };
        note.style.zIndex = '200';
    }

    function onTouchMove(e) {
        if (!dragState) return;

        e.preventDefault();
        var touch = e.touches[0];
        var scale = getCanvasScale();
        var dx = (touch.clientX - dragState.startX) / scale;
        var dy = (touch.clientY - dragState.startY) / scale;

        dragState.el.style.left = (dragState.origLeft + dx) + 'px';
        dragState.el.style.top = (dragState.origTop + dy) + 'px';
    }

    function onTouchEnd() {
        if (!dragState) return;

        dragState.el.style.zIndex = '';
        dragState = null;

        savePositions(getAllNotePositions());
    }

    function init() {
        var canvas = document.getElementById('infinite-canvas');
        if (!canvas) return;

        restorePositions();

        canvas.addEventListener('mousedown', onMouseDown);
        window.addEventListener('mousemove', onMouseMove);
        window.addEventListener('mouseup', onMouseUp);

        canvas.addEventListener('touchstart', onTouchStart, {passive: false});
        window.addEventListener('touchmove', onTouchMove, {passive: false});
        window.addEventListener('touchend', onTouchEnd);

        console.log('%c[scratchpad] Sticky note drag initialized', 'color: #ffeb3b;');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
