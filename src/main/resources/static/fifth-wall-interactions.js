(function () {
    'use strict';

    var packageSelector = '[data-fw-package-id]';
    var targetSelector = '[data-fw-drop-target]';
    var draggingClass = 'is-dragging';
    var readyClass = 'is-drop-ready';

    function routeUrl(action, params) {
        var search = new URLSearchParams();
        search.set('action', action);
        Object.keys(params || {}).forEach(function (key) {
            if (params[key] !== null && params[key] !== undefined) {
                search.set(key, params[key]);
            }
        });
        return '/fifth-wall?' + search.toString();
    }

    function targetIsDisabled(target) {
        return target.getAttribute('data-fw-disabled') === 'true';
    }

    function clearReadyTargets() {
        document.querySelectorAll(targetSelector + '.' + readyClass).forEach(function (target) {
            target.classList.remove(readyClass);
        });
    }

    function start() {
        document.querySelectorAll(packageSelector).forEach(function (card) {
            card.addEventListener('dragstart', function (event) {
                var packageId = card.getAttribute('data-fw-package-id');
                if (!packageId || !event.dataTransfer) return;
                event.dataTransfer.effectAllowed = 'move';
                event.dataTransfer.setData('text/plain', packageId);
                card.classList.add(draggingClass);
            });

            card.addEventListener('dragend', function () {
                card.classList.remove(draggingClass);
                clearReadyTargets();
            });
        });

        document.querySelectorAll(targetSelector).forEach(function (target) {
            target.addEventListener('dragover', function (event) {
                if (targetIsDisabled(target)) return;
                event.preventDefault();
                if (event.dataTransfer) {
                    event.dataTransfer.dropEffect = 'move';
                }
                target.classList.add(readyClass);
            });

            target.addEventListener('dragleave', function () {
                target.classList.remove(readyClass);
            });

            target.addEventListener('drop', function (event) {
                if (targetIsDisabled(target)) return;
                event.preventDefault();
                var packageId = event.dataTransfer ? event.dataTransfer.getData('text/plain') : '';
                if (!packageId) return;

                var targetType = target.getAttribute('data-fw-drop-target');
                if (targetType === 'truck') {
                    window.location.href = routeUrl('drop-truck', {
                        truck: target.getAttribute('data-fw-truck-index') || '0',
                        package: packageId
                    });
                    return;
                }

                if (targetType === 'return') {
                    window.location.href = routeUrl('drop-return', {
                        package: packageId
                    });
                }
            });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start, { once: true });
    } else {
        start();
    }
})();
