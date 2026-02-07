/**
 * Konami Code Detection
 * Pattern: Up, Up, Down, Down, Left, Right, Left, Right, B, A
 * Navigates to /scratchpad when successfully entered
 */
(function () {
    'use strict';

    const KONAMI_CODE = [
        'ArrowUp', 'ArrowUp',
        'ArrowDown', 'ArrowDown',
        'ArrowLeft', 'ArrowRight',
        'ArrowLeft', 'ArrowRight',
        'KeyB', 'KeyA'
    ];

    let currentIndex = 0;
    let timeout = null;

    function resetSequence() {
        currentIndex = 0;
    }

    function onKeyDown(e) {
        // Clear any existing timeout
        if (timeout) {
            clearTimeout(timeout);
        }

        // Set a timeout to reset if user takes too long
        timeout = setTimeout(resetSequence, 2000);

        // Check if the key matches the expected key in the sequence
        if (e.code === KONAMI_CODE[currentIndex]) {
            currentIndex++;

            // Check if the full sequence is complete
            if (currentIndex === KONAMI_CODE.length) {
                resetSequence();
                clearTimeout(timeout);

                // Trigger the secret!
                activateSecret();
            }
        } else {
            // Wrong key - reset the sequence
            resetSequence();
        }
    }

    function activateSecret() {
        // Add a fun transition effect
        const overlay = document.createElement('div');
        overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: #0a0a0a;
            z-index: 99999;
            display: flex;
            align-items: center;
            justify-content: center;
            opacity: 0;
            transition: opacity 0.3s ease;
        `;
        overlay.innerHTML = `
            <div style="
                font-family: monospace;
                color: #00ff00;
                font-size: 24px;
                text-align: center;
            ">
                <div>// ACCESS GRANTED</div>
                <div style="margin-top: 10px; font-size: 14px; color: #006600;">
                    Entering the void...
                </div>
            </div>
        `;
        document.body.appendChild(overlay);

        // Trigger animation
        requestAnimationFrame(() => {
            overlay.style.opacity = '1';
        });

        // Navigate after a brief delay
        setTimeout(() => {
            window.location.href = '/experiments/scratchpad';
        }, 800);
    }

    // Initialize
    document.addEventListener('keydown', onKeyDown);

    // Log a hint in console for curious developers
    console.log('%c// Looking for secrets? Try the Konami code.', 'color: #00ff00; font-family: monospace;');
})();
