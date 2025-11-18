// Polyfill for Summon 0.4.8.9 WASM process.release bug
window.process = window.process || {};
window.process.release = window.process.release || {name: 'browser'};
