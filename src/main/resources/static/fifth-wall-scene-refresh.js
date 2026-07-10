(function () {
    'use strict';

    var canvasId = 'fifth-wall-scene';
    var telemetryId = 'fw-telemetry';
    var refreshAction = 'scene-refresh';
    var refreshing = false;

    function copyTelemetry(nextDocument) {
        var current = document.getElementById(telemetryId);
        var next = nextDocument.getElementById(telemetryId);
        if (!current || !next) {
            return;
        }

        current.setAttribute('data-session', next.getAttribute('data-session') || '');
        current.setAttribute('data-revision', next.getAttribute('data-revision') || '0');
        current.textContent = next.textContent || '';
    }

    function runHydrationScript(sourceScript, container) {
        var script = document.createElement('script');
        script.type = 'module';
        script.textContent = sourceScript.textContent || '';
        script.setAttribute('data-fifth-wall-scene-hydration', 'true');
        container.appendChild(script);
    }

    function copySceneData(nextContainer, currentContainer) {
        ['fifth-wall-scene-data', 'fifth-wall-scene-actions'].forEach(function (id) {
            var current = currentContainer.querySelector('#' + id);
            var next = nextContainer.querySelector('#' + id);
            if (current && next) {
                current.textContent = next.textContent || '';
            }
        });
    }

    function refreshScene() {
        if (refreshing) {
            return;
        }

        var currentCanvas = document.getElementById(canvasId);
        if (!currentCanvas || !currentCanvas.parentElement) {
            return;
        }

        refreshing = true;
        currentCanvas.setAttribute('aria-busy', 'true');

        fetch('/fifth-wall?canvas-refresh=1', {
            method: 'GET',
            credentials: 'same-origin',
            cache: 'no-store',
            headers: {
                'X-Requested-With': 'XMLHttpRequest'
            }
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('Scene refresh failed with status ' + response.status);
            }
            return response.text();
        }).then(function (html) {
            var nextDocument = new DOMParser().parseFromString(html, 'text/html');
            var nextCanvas = nextDocument.getElementById(canvasId);
            if (!nextCanvas || !nextCanvas.parentElement) {
                throw new Error('Scene refresh response did not contain the canvas');
            }

            var nextHydrationScript = nextCanvas.parentElement.querySelector('script[type="module"]');
            if (!nextHydrationScript) {
                throw new Error('Scene refresh response did not contain hydration data');
            }

            var hydrator = window.SigilHydrator || (window.Sigil && window.Sigil.Hydrator);
            if (hydrator && typeof hydrator.dispose === 'function') {
                hydrator.dispose(canvasId);
            }

            var container = currentCanvas.parentElement;
            var importedCanvas = document.importNode(nextCanvas, true);
            currentCanvas.replaceWith(importedCanvas);
            copySceneData(nextCanvas.parentElement, container);
            container.querySelectorAll('script[data-fifth-wall-scene-hydration]').forEach(function (oldScript) {
                oldScript.remove();
            });
            copyTelemetry(nextDocument);
            runHydrationScript(nextHydrationScript, container);
        }).catch(function (error) {
            console.error('Fifth Wall: unable to refresh the canvas scene', error);
            var canvas = document.getElementById(canvasId);
            if (canvas) {
                canvas.removeAttribute('aria-busy');
            }
        }).finally(function () {
            refreshing = false;
        });
    }

    window.addEventListener('sigil:scene-action-response', function (event) {
        var detail = event && event.detail;
        if (!detail || detail.canvasId !== canvasId || detail.action !== refreshAction) {
            return;
        }
        refreshScene();
    });
})();
