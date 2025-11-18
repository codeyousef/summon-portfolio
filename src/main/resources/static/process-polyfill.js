// Polyfill for Summon 0.4.8.9 WASM process.release bug
// This MUST execute synchronously BEFORE hydration script loads
(function () {
    'use strict';

    // Polyfill Node.js process global that WASM bundle expects
    window.process = window.process || {};
    window.process.release = window.process.release || {name: 'browser'};
    window.process.env = window.process.env || {};
    window.process.version = window.process.version || 'v18.0.0';
    window.process.versions = window.process.versions || {node: '18.0.0'};
    window.process.platform = window.process.platform || 'browser';
    window.process.arch = window.process.arch || 'x64';

    console.log('[Polyfill] Process global initialized:', window.process.release);
})();
