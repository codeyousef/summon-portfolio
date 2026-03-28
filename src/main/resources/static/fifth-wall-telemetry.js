(function () {
    'use strict';

    var endpoint = '/api/fifth-wall/telemetry';
    var elementId = 'fw-telemetry';
    var flushTimer = null;

    function storageKey(sessionId) {
        return 'fifth-wall-telemetry:' + sessionId;
    }

    function loadTransportState(sessionId) {
        try {
            var raw = sessionStorage.getItem(storageKey(sessionId));
            if (!raw) {
                return { sentEventIds: [], lastRevision: 0 };
            }
            var parsed = JSON.parse(raw);
            return {
                sentEventIds: Array.isArray(parsed.sentEventIds) ? parsed.sentEventIds : [],
                lastRevision: typeof parsed.lastRevision === 'number' ? parsed.lastRevision : 0
            };
        } catch (error) {
            return { sentEventIds: [], lastRevision: 0 };
        }
    }

    function saveTransportState(sessionId, state) {
        try {
            sessionStorage.setItem(storageKey(sessionId), JSON.stringify({
                sentEventIds: (state.sentEventIds || []).slice(-800),
                lastRevision: state.lastRevision || 0
            }));
        } catch (error) {
            // Ignore quota or privacy-mode failures.
        }
    }

    function parsePayload(element) {
        if (!element) {
            return null;
        }

        var raw = (element.textContent || '').trim();
        if (!raw) {
            return null;
        }

        try {
            return JSON.parse(raw);
        } catch (error) {
            return null;
        }
    }

    function postPayload(body, keepalive) {
        return fetch(endpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(body),
            keepalive: !!keepalive
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('Telemetry request failed with status ' + response.status);
            }
            return response;
        });
    }

    function beaconPayload(body) {
        if (!navigator.sendBeacon) {
            return postPayload(body, true);
        }

        var blob = new Blob([JSON.stringify(body)], { type: 'application/json' });
        var sent = navigator.sendBeacon(endpoint, blob);
        return sent ? Promise.resolve() : postPayload(body, true);
    }

    function flush(force) {
        var element = document.getElementById(elementId);
        var payload = parsePayload(element);
        if (!payload || !payload.sessionId || !Array.isArray(payload.events)) {
            return;
        }

        var transportState = loadTransportState(payload.sessionId);
        var sentIds = new Set(transportState.sentEventIds || []);
        var unsentEvents = payload.events.filter(function (event) {
            return event && event.id && !sentIds.has(event.id);
        });

        if (!unsentEvents.length && transportState.lastRevision === payload.revision) {
            return;
        }

        var body = {
            sessionId: payload.sessionId,
            revision: payload.revision,
            events: unsentEvents,
            summary: payload.summary
        };

        var send = force ? beaconPayload(body) : postPayload(body, false);
        Promise.resolve(send).then(function () {
            unsentEvents.forEach(function (event) {
                sentIds.add(event.id);
            });
            saveTransportState(payload.sessionId, {
                sentEventIds: Array.from(sentIds),
                lastRevision: payload.revision
            });
        }).catch(function () {
            // Ignore transient failures. The next mutation or page hide will retry.
        });
    }

    function scheduleFlush() {
        if (flushTimer !== null) {
            clearTimeout(flushTimer);
        }
        flushTimer = setTimeout(function () {
            flush(false);
        }, 120);
    }

    function start() {
        var element = document.getElementById(elementId);
        if (!element) {
            return;
        }

        scheduleFlush();

        var observer = new MutationObserver(function () {
            scheduleFlush();
        });

        observer.observe(element, {
            subtree: true,
            childList: true,
            characterData: true,
            attributes: true
        });

        window.addEventListener('beforeunload', function () {
            flush(true);
        });

        document.addEventListener('visibilitychange', function () {
            if (document.visibilityState === 'hidden') {
                flush(true);
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start, { once: true });
    } else {
        start();
    }
})();
