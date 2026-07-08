(function () {
    'use strict';

    var containerSelector = '#fifth-wall-scene-container';
    var dragThresholdPx = 10;
    var suppressWindowMs = 450;
    var activeDrag = null;
    var suppressClicksUntil = 0;

    function sceneContainer() {
        return document.querySelector(containerSelector);
    }

    function isInsideScene(target) {
        var container = sceneContainer();
        return !!(container && target && container.contains(target));
    }

    function distanceSquared(x1, y1, x2, y2) {
        var dx = x2 - x1;
        var dy = y2 - y1;
        return (dx * dx) + (dy * dy);
    }

    document.addEventListener('mousedown', function (event) {
        if (!isInsideScene(event.target)) {
            activeDrag = null;
            return;
        }

        activeDrag = {
            x: event.clientX,
            y: event.clientY,
            moved: false
        };
    }, true);

    document.addEventListener('mousemove', function (event) {
        if (!activeDrag) return;

        if (distanceSquared(activeDrag.x, activeDrag.y, event.clientX, event.clientY) >= dragThresholdPx * dragThresholdPx) {
            activeDrag.moved = true;
        }
    }, true);

    document.addEventListener('mouseup', function () {
        if (activeDrag && activeDrag.moved) {
            suppressClicksUntil = Date.now() + suppressWindowMs;
        }
        activeDrag = null;
    }, true);

    document.addEventListener('click', function (event) {
        if (Date.now() > suppressClicksUntil || !isInsideScene(event.target)) {
            return;
        }

        event.preventDefault();
        event.stopImmediatePropagation();
        suppressClicksUntil = 0;
    }, true);
})();
