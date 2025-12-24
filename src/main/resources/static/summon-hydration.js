/**
 * Summon Hydration Bootloader
 * Handles client-side interactions for SSR-rendered Summon components.
 * This lightweight script handles data-action toggles without requiring full hydration.
 */
(function() {
    'use strict';

    // Handle data-action based toggles (HamburgerMenu, Dropdown, etc.)
    function handleDataAction(actionJson, triggerElement) {
        try {
            var action = JSON.parse(actionJson);
            if (action.type === 'toggle' && action.targetId) {
                var target = document.getElementById(action.targetId);
                if (target) {
                    var currentDisplay = getComputedStyle(target).display;
                    var isHidden = currentDisplay === 'none';

                    // Store original display value on first toggle if not already stored
                    if (!target.hasAttribute('data-original-display') && !isHidden) {
                        target.setAttribute('data-original-display', currentDisplay || 'block');
                    }

                    // Use stored original display value, or 'flex' for common layout containers, or 'block' as fallback
                    var showDisplay = target.getAttribute('data-original-display') || 'flex';
                    target.style.display = isHidden ? showDisplay : 'none';

                    // Update aria-expanded on trigger
                    if (triggerElement) {
                        triggerElement.setAttribute('aria-expanded', isHidden.toString());

                        // Update hamburger menu icon if applicable
                        if (triggerElement.getAttribute('data-hamburger-toggle') === 'true') {
                            triggerElement.setAttribute('aria-label', isHidden ? 'Close menu' : 'Open menu');
                            var iconSpan = triggerElement.querySelector('.material-icons');
                            if (iconSpan) {
                                iconSpan.textContent = isHidden ? 'close' : 'menu';
                            }
                        }

                        // Update +/- disclosure icon if present (non-hamburger toggles)
                        var disclosureIcon = triggerElement.querySelector('span:not(.material-icons)');
                        if (disclosureIcon) {
                            var iconText = disclosureIcon.textContent.trim();
                            if (iconText === '+' || iconText === '−' || iconText === '-') {
                                disclosureIcon.textContent = isHidden ? '−' : '+';
                            }
                        }
                    }
                    console.log('[Summon] Toggle:', action.targetId, '→', isHidden ? 'shown' : 'hidden');
                    return true;
                }
            }
        } catch (e) {
            console.error('[Summon] Error parsing data-action:', e);
        }
        return false;
    }

    // Click event delegation for data-action elements
    document.addEventListener('click', function(e) {
        var t = e.target;
        
        // Walk up the DOM to find an element with data-action
        while (t && !t.getAttribute('data-action')) {
            t = t.parentElement;
        }

        if (t) {
            var actionJson = t.getAttribute('data-action');
            if (actionJson && handleDataAction(actionJson, t)) {
                e.preventDefault();
                e.stopPropagation();
                return;
            }
        }
    }, true);

    // Close dropdowns/menus when clicking outside
    document.addEventListener('click', function(e) {
        // Find all open menus (elements with data-original-display that are visible)
        var openMenus = document.querySelectorAll('[id^="hamburger-menu-"], [id^="dropdown-menu-"]');
        openMenus.forEach(function(menu) {
            if (menu.style.display !== 'none') {
                // Check if click was outside the menu and its trigger
                var trigger = document.querySelector('[aria-controls="' + menu.id + '"]');
                if (trigger && !menu.contains(e.target) && !trigger.contains(e.target)) {
                    menu.style.display = 'none';
                    trigger.setAttribute('aria-expanded', 'false');
                    if (trigger.getAttribute('data-hamburger-toggle') === 'true') {
                        trigger.setAttribute('aria-label', 'Open menu');
                        var iconSpan = trigger.querySelector('.material-icons');
                        if (iconSpan) {
                            iconSpan.textContent = 'menu';
                        }
                    }
                }
            }
        });
    }, false);

    console.log('[Summon] Hydration bootloader initialized');
})();
