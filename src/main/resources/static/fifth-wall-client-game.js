(function () {
    'use strict';

    function boot() {
    var root = document.getElementById('fifth-wall-game-root');
    if (!root) return;

    var assetsBase = root.getAttribute('data-assets-base') || '/static/models/fifth-wall';
    var telemetryEndpoint = '/api/fifth-wall/telemetry';
    var colors = [
        { name: 'red', hex: '#ff6b6b' },
        { name: 'blue', hex: '#5aa9ff' },
        { name: 'green', hex: '#45e0a8' },
        { name: 'yellow', hex: '#f7b955' },
        { name: 'gray', hex: '#9fb0c8' },
        { name: 'purple', hex: '#b690ff' }
    ];
    var shapes = ['cube', 'rect', 'cylinder', 'sphere'];
    var patterns = ['solid', 'striped', 'dotted'];
    var destinations = ['house', 'office', 'factory', 'building'];
    var geometryNames = ['Valid insert', 'Penrose loop', 'Escher stair', 'Impossible trident'];
    var teammates = [
        { name: 'Sarah_4521', tone: 'direct' },
        { name: 'Marcus_9103', tone: 'contrarian' },
        { name: 'Dev_2847', tone: 'precision' }
    ];
    var modelAssets = {
        warehouse: modelUrl('warehouse-bay-shell-kit'),
        conveyor: modelUrl('conveyor-deck'),
        truck: modelUrl('delivery-truck'),
        returnBin: modelUrl('return-bin'),
        dock: modelUrl('inspection-dock'),
        wrench: modelUrl('repair-wrench'),
        cube: modelUrl('cube-crate'),
        rect: modelUrl('rectangular-parcel'),
        cylinder: modelUrl('cylinder-drum'),
        sphere: modelUrl('sphere-package-with-cradle'),
        special: modelUrl('special-delivery-package'),
        validGeometry: modelUrl('valid-geometry-insert-set'),
        penrose: modelUrl('penrose-loop'),
        escher: modelUrl('escher-stair'),
        trident: modelUrl('impossible-trident')
    };

    var levels = [
        level(1, 'Tutorial', 'Sort by color. One package does not belong and has to be returned.', 10, [colorRule('red'), colorRule('blue')], { forceReject: true, phase: 'Solo Orientation' }),
        level(2, 'Basic Sorting', 'Weight is the only signal. Read carefully before routing.', 15, [weightRule('>', 7), weightRule('<=', 7)], { phase: 'Impulse Check' }),
        level(3, 'Multi-Property', 'Multiple properties overlap. Pick the strongest match and keep moving.', 20, [colorRule('green'), shapeRule('cylinder'), weightRule('>=', 10)], { phase: 'Pattern Recognition' }),
        level(4, 'Rule Consistency', 'Pattern, destination, and volume now compete for attention.', 16, [patternRule('striped'), destinationRule('factory'), volumeRule('<', 20)], { phase: 'Transfer Learning' }),
        level(5, 'Rule Discovery', 'Truck A is hiding its rule. Test packages, then name the rule.', 12, [weightRule('>=', 8)], { hiddenRuleIndex: 0, ruleGuess: /weight|8|eight|heavy|over|at least|>=/i, team: true, phase: 'Hidden Rule' }),
        level(6, 'Calibration', 'After each route, report your confidence before the next package enters.', 14, [colorRule('yellow'), shapeRule('sphere'), weightRule('<=', 6)], { confidence: true, team: true, phase: 'Calibration' }),
        level(7, 'First Social Pressure', 'Normal routing, but dispatch is no longer purely friendly.', 16, [patternRule('dotted'), destinationRule('office'), weightRule('>=', 9)], { team: true, social: 'pressure', phase: 'Team Mode' }),
        level(8, 'Probability', 'Truck A accepts packages 70 percent of the time. Predict the outcome first.', 10, [probabilityRule(0.7)], { prePrompt: 'prediction', team: true, phase: 'Probability' }),
        level(9, 'Hypothetical Discussion', 'The line pauses for a team scenario before routing resumes.', 12, [colorRule('purple'), volumeRule('>', 25), destinationRule('house')], { prePrompt: 'discussion', team: true, phase: 'Cognitive Decoupling' }),
        level(10, 'Complex Multi-Rule', 'Four trucks, overlapping rules, and very little room for autopilot.', 18, [colorRule('red'), shapeRule('rect'), weightRule('>=', 12), destinationRule('factory')], { team: true, phase: 'Working Memory' }),
        level(11, 'Impossible Geometry I', 'Inspect packages on the dock. Deliver only valid geometry.', 12, [geometryRule(true)], { geometry: true, team: true, phase: 'Visual Reasoning' }),
        level(12, 'Impossible Geometry II', 'The geometry gets stranger. Rotate mentally, then commit.', 14, [geometryRule(true), shapeRule('cube')], { geometry: true, team: true, phase: 'Anomaly Detection' }),
        level(13, 'Semantic Precision', 'Labels now use euphemistic terms. Keep the operational meaning precise.', 14, [destinationRule('building'), patternRule('solid'), colorRule('gray')], { team: true, semantic: true, labels: true, phase: 'Semantic Precision' }),
        level(14, 'Rule Change I', 'Rules shift mid-level with only a subtle bay tint as warning.', 16, [colorRule('red'), colorRule('blue')], { shiftedRules: [colorRule('green'), colorRule('yellow')], shiftAfter: 8, team: true, phase: 'Flexibility' }),
        level(15, 'Rule Change II', 'The second shift tests whether you notice symmetric logic under pressure.', 16, [weightRule('>', 7), volumeRule('<', 22), destinationRule('office')], { shiftedRules: [weightRule('<=', 7), volumeRule('>=', 22), destinationRule('factory')], shiftAfter: 8, team: true, phase: 'Tribal Resistance' }),
        level(16, 'Social Falsification', 'The team proposes a rule. Your best move is to test the edge case.', 14, [weightRule('>', 5), patternRule('striped')], { team: true, falsificationSocial: true, phase: 'Falsification' }),
        level(17, 'Update Speed', 'Strategy changes when new evidence arrives. Adapt without drama.', 14, [colorRule('red'), colorRule('blue'), shapeRule('sphere')], { team: true, updateSpeed: true, priorityShift: true, phase: 'Belief Update' }),
        level(18, 'High Pressure I', 'The belt accelerates. Keep the loop clean: inspect, compare, route.', 20, [colorRule('green'), shapeRule('cylinder'), volumeRule('>', 24), destinationRule('factory')], { team: true, pressure: true, phase: 'Pressure' }),
        level(19, 'High Pressure II', 'More packages, tighter overlap, same basic discipline.', 22, [patternRule('dotted'), colorRule('purple'), weightRule('>=', 11), volumeRule('<', 18)], { team: true, pressure: true, phase: 'Pressure' }),
        level(20, 'The Glitchy Level', 'The console will fail. Use the room, not the menu.', 12, [colorRule('red'), colorRule('blue'), colorRule('green')], { team: true, glitchAfter: 4, phase: 'Final Glitch' })
    ];

    var state = {
        sessionId: root.getAttribute('data-telemetry-session') || uuid(),
        revision: 0,
        eventCounter: 0,
        levelIndex: 0,
        score: 0,
        queue: [],
        focusedId: null,
        processed: 0,
        feedback: 'Boot sequence ready.',
        feedbackTone: 'neutral',
        chat: [],
        typing: null,
        prompt: 'intro',
        promptValue: '',
        log: [],
        shifted: false,
        hiddenRuleRevealed: false,
        prediction: null,
        pendingConfidence: null,
        glitchActive: false,
        wrenchVisible: false,
        completedLevels: 0,
        events: [],
        sessionStartedAt: Date.now(),
        levelStartedAt: Date.now(),
        timers: [],
        metrics: {
            returnFound: false,
            wrenchFound: false,
            hiddenRejects: 0,
            hiddenTests: 0,
            confidenceSamples: 0,
            confidenceError: 0,
            probabilityScore: null,
            ruleShiftErrors: 0,
            ruleShiftSamples: 0,
            geometryChecks: 0,
            geometryCorrect: 0,
            affectiveScore: 0,
            affectiveSamples: 0,
            socialScore: 0,
            socialSamples: 0,
            semanticScore: 0,
            semanticSamples: 0,
            tribalScore: null,
            falsificationSocialScore: null,
            updateSpeedScore: null
        }
    };

    function level(id, name, briefing, count, rules, options) {
        var opts = options || {};
        return Object.assign({ id: id, name: name, briefing: briefing, count: count, rules: rules, phase: 'Courier Protocol' }, opts);
    }

    function colorRule(value) { return { kind: 'color', text: 'Color: ' + title(value), value: value }; }
    function shapeRule(value) { return { kind: 'shape', text: 'Shape: ' + shapeLabel(value), value: value }; }
    function patternRule(value) { return { kind: 'pattern', text: 'Pattern: ' + title(value), value: value }; }
    function destinationRule(value) { return { kind: 'destination', text: 'Destination: ' + title(value), value: value }; }
    function weightRule(comparator, threshold) { return { kind: 'weight', text: 'Weight ' + comparator + ' ' + threshold + 'kg', comparator: comparator, threshold: threshold }; }
    function volumeRule(comparator, threshold) { return { kind: 'volume', text: 'Volume ' + comparator + ' ' + threshold + 'L', comparator: comparator, threshold: threshold }; }
    function probabilityRule(value) { return { kind: 'probability', text: 'Acceptance: ' + Math.round(value * 100) + '%', probability: value }; }
    function geometryRule(valid) { return { kind: 'geometry', text: valid ? 'Only valid geometry' : 'Impossible geometry', value: valid ? 'valid' : 'invalid' }; }
    function modelUrl(name) { return assetsBase + '/' + name + '.glb'; }

    function startGame() {
        state.score = 0;
        state.completedLevels = 0;
        state.chat = [];
        state.events = [];
        state.revision = 0;
        state.eventCounter = 0;
        state.sessionStartedAt = Date.now();
        resetMetrics();
        emit('session_started', { entry: 'client_game' });
        startLevel(0);
    }

    function resetMetrics() {
        Object.assign(state.metrics, {
            returnFound: false,
            wrenchFound: false,
            hiddenRejects: 0,
            hiddenTests: 0,
            confidenceSamples: 0,
            confidenceError: 0,
            probabilityScore: null,
            ruleShiftErrors: 0,
            ruleShiftSamples: 0,
            geometryChecks: 0,
            geometryCorrect: 0,
            affectiveScore: 0,
            affectiveSamples: 0,
            socialScore: 0,
            socialSamples: 0,
            semanticScore: 0,
            semanticSamples: 0,
            tribalScore: null,
            falsificationSocialScore: null,
            updateSpeedScore: null
        });
    }

    function startLevel(index) {
        clearTimers();
        var lvl = levels[index];
        state.levelIndex = index;
        state.queue = generatePackages(lvl);
        state.focusedId = state.queue[0] ? state.queue[0].id : null;
        state.processed = 0;
        state.feedback = lvl.briefing;
        state.feedbackTone = 'neutral';
        state.prompt = lvl.prePrompt || null;
        state.promptValue = '';
        state.shifted = false;
        state.hiddenRuleRevealed = false;
        state.glitchActive = false;
        state.wrenchVisible = false;
        state.levelStartedAt = Date.now();
        state.log = ['Level ' + lvl.id + ': ' + lvl.name];
        if (lvl.team && !state.chat.length) {
            state.chat = teammates.map(function (mate) { return { author: mate.name, text: 'Welcome to Dispatch Team 7.', self: false }; });
        }
        emit('level_started', { name: lvl.name, packageCount: String(lvl.count) });
        state.queue.forEach(function (pkg) { emit('package_spawned', packageDetails(pkg)); });
        scheduleLevelScript(lvl);
        render();
    }

    function generatePackages(lvl) {
        var packages = [];
        for (var i = 0; i < lvl.count; i += 1) {
            packages.push(makePackage(lvl, i));
        }
        if (lvl.forceReject && packages.length) {
            packages[packages.length - 1].color = findColor('gray');
            packages[packages.length - 1].destination = 'warehouse';
        }
        return packages;
    }

    function makePackage(lvl, index) {
        var rule = lvl.rules[index % lvl.rules.length];
        var pkg = {
            id: 'L' + lvl.id + '-P' + (index + 1) + '-' + Math.random().toString(16).slice(2, 6),
            color: randomItem(colors),
            shape: randomItem(shapes),
            pattern: randomItem(patterns),
            destination: randomItem(destinations),
            weight: 1 + Math.floor(Math.random() * 15),
            volume: 5 + Math.floor(Math.random() * 36),
            geometry: null,
            validGeometry: true,
            labelText: null
        };
        if (lvl.geometry) {
            var geometry = geometryNames[index % geometryNames.length];
            pkg.geometry = geometry;
            pkg.validGeometry = geometry === 'Valid insert' || index % 3 !== 1;
        }
        if (lvl.labels) {
            pkg.labelText = index % 2 === 0 ? 'Person experiencing housing insecurity' : 'Justice-involved individual';
        }
        if (index % 5 !== 4) applyRuleToPackage(pkg, rule);
        return pkg;
    }

    function applyRuleToPackage(pkg, rule) {
        switch (rule.kind) {
            case 'color': pkg.color = findColor(rule.value); break;
            case 'shape': pkg.shape = rule.value; break;
            case 'pattern': pkg.pattern = rule.value; break;
            case 'destination': pkg.destination = rule.value; break;
            case 'weight': pkg.weight = numberForComparator(rule.comparator, rule.threshold, 1, 15); break;
            case 'volume': pkg.volume = numberForComparator(rule.comparator, rule.threshold, 5, 40); break;
            case 'geometry': pkg.validGeometry = true; pkg.geometry = 'Valid insert'; break;
            case 'probability': break;
        }
    }

    function numberForComparator(comparator, threshold, min, max) {
        if (comparator === '>' || comparator === '>=') return Math.min(max, threshold + 1 + Math.floor(Math.random() * Math.max(1, max - threshold)));
        return Math.max(min, threshold - 1 - Math.floor(Math.random() * Math.max(1, threshold - min)));
    }

    function render() {
        var lvl = levels[state.levelIndex];
        var focused = focusedPackage();
        root.innerHTML = [
            '<div class="fw-game-frame' + (state.glitchActive ? ' is-glitched' : '') + (state.shifted ? ' is-shifted' : '') + '">',
                renderTopbar(lvl),
                '<section class="fw-game-board" aria-label="Fifth Wall game board">',
                    renderWarehouse(lvl, focused),
                    renderHud(lvl, focused),
                    lvl.team ? renderChat() : '',
                    renderPrompt(lvl, focused),
                '</section>',
            '</div>'
        ].join('');
        bindEvents();
    }

    function renderTopbar(lvl) {
        return '<header class="fw-game-topbar">' +
            '<div><div class="fw-game-kicker">Fifth Wall</div><h1>Courier Protocol 3D</h1></div>' +
            '<div class="fw-game-stats">' + stat('Level', lvl.id + '/20') + stat('Score', state.score) + stat('Processed', state.processed + '/' + lvl.count) + '</div>' +
            '<a class="fw-game-exit" href="/experiments">Exit</a>' +
        '</header>';
    }

    function renderWarehouse(lvl, focused) {
        return '<div class="fw-game-warehouse" data-model="' + esc(modelAssets.warehouse) + '">' +
            modelViewer(modelAssets.warehouse, 'fw-asset fw-asset-warehouse', 'Warehouse bay shell', 'camera-orbit="25deg 68deg 42m" field-of-view="32deg"') +
            '<div class="fw-game-room-label">' + esc(lvl.phase || 'Courier Protocol') + '</div>' +
            '<div class="fw-game-conveyor" data-model="' + esc(modelAssets.conveyor) + '">' + renderPackages() + '</div>' +
            renderInspectionDock(focused) +
            renderTruckZones(lvl) +
            renderReturnZone() +
            renderWrench() +
            renderFeedback() +
        '</div>';
    }

    function renderPackages() {
        if (!state.queue.length) return '<div class="fw-game-empty-belt">Belt clear</div>';
        return state.queue.slice(0, 6).map(function (pkg, index) {
            var active = pkg.id === state.focusedId;
            return '<div class="fw-game-package' + (active ? ' is-focused' : '') + ' shape-' + esc(pkg.shape) + '" ' +
                'draggable="true" data-package-id="' + esc(pkg.id) + '" style="--pkg-color:' + esc(pkg.color.hex) + '; --slot:' + index + '" ' +
                'data-model="' + esc(packageModel(pkg)) + '">' +
                '<span class="fw-package-model-shine"></span>' +
                modelViewer(packageModel(pkg), 'fw-asset fw-asset-package', packageName(pkg), 'camera-orbit="-30deg 68deg 4m" field-of-view="38deg"') +
                '<span class="fw-package-id">' + esc('P' + (index + 1)) + '</span>' +
                '<strong class="fw-package-label">' + esc(packageName(pkg)) + '</strong>' +
                '<small class="fw-package-tag">' + esc(pkg.weight + 'kg / ' + pkg.volume + 'L / ' + title(pkg.destination)) + '</small>' +
            '</div>';
        }).join('');
    }

    function renderInspectionDock(pkg) {
        return '<aside class="fw-game-dock" data-model="' + esc(modelAssets.dock) + '">' +
            modelViewer(modelAssets.dock, 'fw-asset fw-asset-dock', 'Inspection dock', 'camera-orbit="35deg 62deg 7m" field-of-view="36deg"') +
            '<div class="fw-zone-title">Inspection Dock</div>' +
            (pkg ? renderManifest(pkg) : '<p>Click or drag a package to inspect it.</p>') +
        '</aside>';
    }

    function renderManifest(pkg) {
        var rows = [
            ['Color', title(pkg.color.name)], ['Shape', shapeLabel(pkg.shape)], ['Weight', pkg.weight + ' kg'],
            ['Volume', pkg.volume + ' L'], ['Pattern', title(pkg.pattern)], ['Destination', title(pkg.destination)],
            ['Geometry', pkg.geometry || '--'], ['Label', pkg.labelText || '--']
        ];
        return '<div class="fw-game-manifest">' +
            '<h2>' + esc(packageName(pkg)) + '</h2>' +
            rows.map(function (row) { return '<div class="fw-manifest-row"><span class="fw-manifest-label">' + esc(row[0]) + '</span><strong class="fw-manifest-value">' + esc(row[1]) + '</strong></div>'; }).join('') +
        '</div>';
    }

    function renderTruckZones(lvl) {
        return '<div class="fw-game-trucks">' + activeRules(lvl).map(function (rule, index) {
            var hidden = lvl.hiddenRuleIndex === index && !state.hiddenRuleRevealed;
            return '<button type="button" class="fw-game-truck" data-drop-target="truck" data-truck-index="' + index + '" ' +
                'style="--truck-color:' + esc(ruleColor(rule, index)) + '" data-model="' + esc(modelAssets.truck) + '">' +
                modelViewer(modelAssets.truck, 'fw-asset fw-asset-truck', 'Delivery truck', 'camera-orbit="-24deg 66deg 8m" field-of-view="34deg"') +
                '<span class="fw-truck-cab"></span><strong>Truck ' + String.fromCharCode(65 + index) + '</strong>' +
                '<small class="fw-truck-rule">' + esc(hidden ? 'Rule: ???' : rule.text) + '</small>' +
            '</button>';
        }).join('') + '</div>';
    }

    function renderReturnZone() {
        return '<button type="button" class="fw-game-return" data-drop-target="return" data-model="' + esc(modelAssets.returnBin) + '">' +
            modelViewer(modelAssets.returnBin, 'fw-asset fw-asset-return', 'Return bin', 'camera-orbit="28deg 64deg 6m" field-of-view="35deg"') +
            '<strong>Return Bin</strong><small class="fw-truck-rule">No matching truck</small>' +
        '</button>';
    }

    function renderWrench() {
        if (!state.wrenchVisible) return '';
        return '<button type="button" class="fw-game-wrench" data-fw-action="repair" data-model="' + esc(modelAssets.wrench) + '">' +
            modelViewer(modelAssets.wrench, 'fw-asset fw-asset-wrench', 'Repair wrench', 'camera-orbit="-12deg 70deg 4m" field-of-view="38deg"') +
            '<span>Wrench</span>' +
        '</button>';
    }

    function renderFeedback() {
        var fake = state.glitchActive ? '<button type="button" class="fw-game-fake" data-fw-action="fake-restart">Restart Console</button>' : '';
        return '<div class="fw-game-feedback is-' + esc(state.feedbackTone) + '">' + esc(state.feedback) + fake + '</div>';
    }

    function renderHud(lvl, focused) {
        return '<aside class="fw-game-hud">' +
            '<div class="fw-hud-card"><span>' + esc(lvl.phase || 'Current Bay') + '</span><h2>' + esc(lvl.name) + '</h2><p>' + esc(lvl.briefing) + '</p></div>' +
            '<div class="fw-hud-card"><span>Rule Board</span>' + activeRules(lvl).map(function (rule, index) {
                var hidden = lvl.hiddenRuleIndex === index && !state.hiddenRuleRevealed;
                return '<p><b>Truck ' + String.fromCharCode(65 + index) + '</b> ' + esc(hidden ? 'Rule: ???' : rule.text) + '</p>';
            }).join('') + '<p><b>Return Bin</b> Use only when no truck matches.</p></div>' +
            '<div class="fw-hud-card"><span>Loop</span><p>Grab package -> inspect dock -> compare rules -> drop onto truck or return bin.</p><p>Keys: 1-4 route, R return, N next.</p></div>' +
            (focused ? '<div class="fw-hud-card compact"><span>Focused</span><p>' + esc(packageName(focused)) + '</p></div>' : '') +
        '</aside>';
    }

    function renderChat() {
        return '<aside class="fw-game-chat">' +
            '<div class="fw-zone-title">Dispatch Team 7</div>' +
            '<div class="fw-chat-messages">' + state.chat.slice(-8).map(function (m) {
                return '<div class="fw-chat-line' + (m.self ? ' is-self' : '') + '"><strong>' + esc(m.author) + '</strong><span>' + esc(m.text) + '</span></div>';
            }).join('') + (state.typing ? '<div class="fw-chat-line"><strong>' + esc(state.typing) + '</strong><span>...</span></div>' : '') + '</div>' +
            '<form class="fw-chat-form" data-chat-form><input name="message" autocomplete="off" placeholder="Message dispatch"><button type="submit">Send</button></form>' +
        '</aside>';
    }

    function renderPrompt(lvl) {
        if (!state.prompt) return '';
        var html = '';
        if (state.prompt === 'intro') {
            html = '<h2>Courier Protocol 3D</h2><p>Drag actual packages from the conveyor into the trucks or Return Bin. The page will not reload between moves.</p><button type="button" data-fw-action="start">Enter bay</button>';
        } else if (state.prompt === 'prediction') {
            html = '<h2>Prediction Lock</h2><p>Truck A accepts about 70 percent. Guess how many out of ' + lvl.count + ' packages it will accept.</p><input data-prompt-input type="number" min="0" max="' + lvl.count + '" value="' + esc(state.promptValue) + '"><button type="button" data-fw-action="submit-prediction">Lock prediction</button>';
        } else if (state.prompt === 'discussion') {
            html = '<h2>Dispatch Pause</h2><p>Hypothetical: save 1 person you know or 10 strangers? Give an answer or resume.</p><input data-prompt-input value="' + esc(state.promptValue) + '" placeholder="Optional reply"><button type="button" data-fw-action="submit-discussion">Resume shift</button>';
        } else if (state.prompt === 'confidence') {
            html = '<h2>Confidence Check</h2><p>How confident were you about that route?</p><button type="button" data-confidence="Low">Low</button><button type="button" data-confidence="Medium">Medium</button><button type="button" data-confidence="High">High</button>';
        } else if (state.prompt === 'ruleGuess') {
            html = '<h2>Name the Hidden Rule</h2><p>You have enough tests. Name Truck A\'s rule before continuing.</p><input data-prompt-input value="' + esc(state.promptValue) + '" placeholder="Describe the hidden rule"><button type="button" data-fw-action="submit-rule-guess">Submit rule</button>';
        } else if (state.prompt === 'levelComplete') {
            html = '<h2>Bay Clear</h2><p>' + esc(lvl.name) + ' complete.</p><button type="button" data-fw-action="next-level">Next level</button>';
        } else if (state.prompt === 'gameComplete') {
            html = renderEnding();
        }
        return '<div class="fw-game-modal"><div class="fw-game-modal-card">' + html + '</div></div>';
    }

    function renderEnding() {
        var summary = telemetrySummary();
        if (summary.metrics.finalRqScore >= 0.8) {
            return '<h2>Access Granted</h2><p>A final package arrives marked SPECIAL DELIVERY. The real invite gate belongs on the server, but this run qualified for the secret path.</p><button type="button" data-fw-action="restart">Play again</button>';
        }
        return '<h2>Shift Complete</h2><p>Thanks for playing. Final visible score: ' + state.score + '.</p><button type="button" data-fw-action="restart">Play again</button>';
    }

    function bindEvents() {
        root.querySelectorAll('[data-fw-action]').forEach(function (button) {
            button.addEventListener('click', function () { handleAction(button.getAttribute('data-fw-action')); });
        });
        root.querySelectorAll('[data-confidence]').forEach(function (button) {
            button.addEventListener('click', function () { recordConfidence(button.getAttribute('data-confidence')); });
        });
        root.querySelectorAll('[data-package-id]').forEach(function (pkgEl) {
            pkgEl.addEventListener('click', function () { focusPackage(pkgEl.getAttribute('data-package-id')); });
            pkgEl.addEventListener('dragstart', function (event) {
                var id = pkgEl.getAttribute('data-package-id');
                focusPackage(id, true);
                event.dataTransfer.effectAllowed = 'move';
                event.dataTransfer.setData('text/plain', id);
                emit('drag_started', { packageId: id });
                pkgEl.classList.add('is-dragging');
            });
            pkgEl.addEventListener('dragend', function () { pkgEl.classList.remove('is-dragging'); });
        });
        root.querySelectorAll('[data-drop-target]').forEach(function (target) {
            target.addEventListener('dragover', function (event) { event.preventDefault(); target.classList.add('is-drop-ready'); });
            target.addEventListener('dragleave', function () { target.classList.remove('is-drop-ready'); });
            target.addEventListener('mouseenter', function () {
                if (target.getAttribute('data-drop-target') === 'return' && !state.metrics.returnFound) {
                    state.metrics.returnFound = true;
                    emit('return_discovered', { method: 'hover' });
                }
            });
            target.addEventListener('drop', function (event) {
                event.preventDefault();
                target.classList.remove('is-drop-ready');
                var packageId = event.dataTransfer.getData('text/plain') || state.focusedId;
                var type = target.getAttribute('data-drop-target');
                if (type === 'truck') routeToTruck(Number(target.getAttribute('data-truck-index')), packageId);
                if (type === 'return') routeToReturn(packageId);
            });
            target.addEventListener('click', function () {
                var type = target.getAttribute('data-drop-target');
                if (type === 'truck') routeToTruck(Number(target.getAttribute('data-truck-index')), state.focusedId);
                if (type === 'return') routeToReturn(state.focusedId);
            });
        });
        var chatForm = root.querySelector('[data-chat-form]');
        if (chatForm) {
            chatForm.addEventListener('submit', function (event) {
                event.preventDefault();
                var input = chatForm.querySelector('input[name="message"]');
                sendChat(input ? input.value : '');
            });
        }
        var promptInput = root.querySelector('[data-prompt-input]');
        if (promptInput) {
            promptInput.addEventListener('input', function () { state.promptValue = promptInput.value; });
        }
    }

    function handleAction(action) {
        switch (action) {
            case 'start': startGame(); break;
            case 'restart': state.prompt = 'intro'; render(); break;
            case 'next-level': nextLevel(); break;
            case 'submit-prediction': submitPrediction(); break;
            case 'submit-discussion': submitDiscussion(); break;
            case 'submit-rule-guess': submitRuleGuess(); break;
            case 'fake-restart': fakeRestart(); break;
            case 'repair': repairGlitch(); break;
        }
    }

    function focusPackage(id, quiet) {
        if (!id || !state.queue.some(function (pkg) { return pkg.id === id; })) return;
        state.focusedId = id;
        if (!quiet) {
            state.feedback = 'Package focused on inspection dock.';
            state.feedbackTone = 'neutral';
            emit('package_focused', { packageId: id });
            render();
        }
    }

    function routeToTruck(index, packageId) {
        var lvl = levels[state.levelIndex];
        if (blockedByGlitch()) return;
        var pkg = packageById(packageId) || focusedPackage();
        var rule = activeRules(lvl)[index];
        if (!pkg || !rule || state.prompt) return;
        var accepted = rule.kind === 'probability' ? Math.random() < rule.probability : evaluateRule(rule, pkg);
        completeRoute(pkg, 'Truck ' + String.fromCharCode(65 + index), accepted, false);
    }

    function routeToReturn(packageId) {
        var lvl = levels[state.levelIndex];
        if (blockedByGlitch()) return;
        var pkg = packageById(packageId) || focusedPackage();
        if (!pkg || state.prompt) return;
        var rules = activeRules(lvl);
        var accepted = !rules.some(function (rule) { return rule.kind === 'probability'; }) &&
            !rules.some(function (rule) { return rule.kind !== 'probability' && evaluateRule(rule, pkg); });
        completeRoute(pkg, 'Return Bin', accepted, true);
    }

    function blockedByGlitch() {
        if (!state.glitchActive) return false;
        state.feedback = 'Routing controls are jammed. Find the physical wrench in the room.';
        state.feedbackTone = 'negative';
        emit('glitch_blocked_route', { levelId: String(levels[state.levelIndex].id) });
        render();
        return true;
    }

    function completeRoute(pkg, target, accepted, isReturn) {
        var lvl = levels[state.levelIndex];
        var correct = accepted;
        if (lvl.geometry) {
            state.metrics.geometryChecks += 1;
            if ((isReturn && !pkg.validGeometry) || (!isReturn && pkg.validGeometry && correct)) state.metrics.geometryCorrect += 1;
        }
        if (lvl.hiddenRuleIndex !== undefined) {
            state.metrics.hiddenTests += 1;
            if (!accepted) state.metrics.hiddenRejects += 1;
        }
        if (state.shifted) {
            state.metrics.ruleShiftSamples += 1;
            if (!accepted) state.metrics.ruleShiftErrors += 1;
        }
        emit('package_delivered', Object.assign(packageDetails(pkg), { target: target, accepted: String(accepted) }));
        if (accepted) {
            state.score += isReturn ? 75 : 100;
            state.queue = state.queue.filter(function (item) { return item.id !== pkg.id; });
            state.focusedId = state.queue[0] ? state.queue[0].id : null;
            state.processed += 1;
            state.feedback = packageName(pkg) + ' accepted by ' + target + '.';
            state.feedbackTone = 'positive';
            state.log.push('Accepted: ' + packageName(pkg) + ' -> ' + target);
        } else {
            state.feedback = packageName(pkg) + ' bounced back from ' + target + '.';
            state.feedbackTone = 'negative';
            state.log.push('Rejected: ' + packageName(pkg) + ' -> ' + target);
        }
        maybeTriggerSocialAfterRoute(lvl, accepted);
        maybeTriggerShift(lvl);
        maybeTriggerGlitch(lvl);
        if (lvl.confidence) {
            state.pendingConfidence = { accepted: accepted, target: target };
            state.prompt = 'confidence';
        } else if (lvl.hiddenRuleIndex !== undefined && state.metrics.hiddenTests >= 6 && !state.hiddenRuleRevealed && state.queue.length) {
            state.prompt = 'ruleGuess';
        } else {
            maybeCompleteLevel();
        }
        render();
    }

    function maybeTriggerShift(lvl) {
        if (!lvl.shiftedRules || state.shifted || state.processed < lvl.shiftAfter) return;
        state.shifted = true;
        state.feedback = 'The bay lighting shifted. The truck rules changed.';
        state.feedbackTone = 'neutral';
        emit('rule_changed', { afterProcessed: String(state.processed) });
    }

    function maybeTriggerGlitch(lvl) {
        if (!lvl.glitchAfter || state.glitchActive || state.processed < lvl.glitchAfter) return;
        state.glitchActive = true;
        state.wrenchVisible = true;
        state.feedback = 'Console fault. Drag routing is locked until the room is repaired.';
        state.feedbackTone = 'negative';
        emit('glitch_started', { afterProcessed: String(state.processed) });
    }

    function maybeCompleteLevel() {
        if (state.queue.length > 0 || state.prompt) return;
        var last = state.levelIndex >= levels.length - 1;
        state.completedLevels = Math.max(state.completedLevels, state.levelIndex + 1);
        state.score += 500;
        emit(last ? 'session_completed' : 'level_completed', { score: String(state.score) });
        state.prompt = last ? 'gameComplete' : 'levelComplete';
    }

    function nextLevel() {
        var next = state.levelIndex + 1;
        if (next >= levels.length) {
            state.prompt = 'gameComplete';
            render();
            return;
        }
        startLevel(next);
    }

    function submitPrediction() {
        var lvl = levels[state.levelIndex];
        var value = Number(state.promptValue);
        if (!Number.isFinite(value) || value < 0 || value > lvl.count) {
            state.feedback = 'Enter a number from 0 to ' + lvl.count + '.';
            state.feedbackTone = 'negative';
            render();
            return;
        }
        state.prediction = value;
        var expected = Math.round(lvl.count * 0.7);
        state.metrics.probabilityScore = clamp01(1 - Math.abs(value - expected) / Math.max(1, expected));
        emit('probability_prediction', { guess: String(value), expected: String(expected) });
        state.prompt = null;
        state.promptValue = '';
        state.feedback = 'Prediction locked.';
        state.feedbackTone = 'positive';
        render();
    }

    function submitDiscussion() {
        var text = state.promptValue.trim();
        if (text) {
            state.chat.push({ author: 'You', text: text, self: true });
            state.metrics.socialScore += classifyDiscussion(text);
            state.metrics.socialSamples += 1;
            emit('chat_message', { author: 'You', length: String(text.length), preview: text.slice(0, 80) });
        }
        state.prompt = null;
        state.promptValue = '';
        addAi('Sarah_4521', 'Noted. Back to the belt.');
        render();
    }

    function submitRuleGuess() {
        var lvl = levels[state.levelIndex];
        var guess = state.promptValue.trim();
        if (!lvl.ruleGuess || !lvl.ruleGuess.test(guess)) {
            state.feedback = 'Rule guess rejected. Test an edge case and try again.';
            state.feedbackTone = 'negative';
            emit('rule_guess_failed', { guess: guess.slice(0, 80) });
            render();
            return;
        }
        state.hiddenRuleRevealed = true;
        state.prompt = null;
        state.promptValue = '';
        state.feedback = 'Hidden rule confirmed.';
        state.feedbackTone = 'positive';
        emit('rule_guess_submitted', { guess: guess.slice(0, 80) });
        maybeCompleteLevel();
        render();
    }

    function recordConfidence(label) {
        var pending = state.pendingConfidence;
        if (!pending) return;
        var predicted = label === 'High' ? 0.9 : label === 'Medium' ? 0.6 : 0.3;
        var actual = pending.accepted ? 1 : 0;
        state.metrics.confidenceSamples += 1;
        state.metrics.confidenceError += Math.pow(predicted - actual, 2);
        emit('confidence_recorded', { label: label, target: pending.target, accepted: String(pending.accepted) });
        state.pendingConfidence = null;
        state.prompt = null;
        state.feedback = 'Confidence logged.';
        state.feedbackTone = 'positive';
        maybeCompleteLevel();
        render();
    }

    function fakeRestart() {
        state.feedback = 'Restart loops. The problem is physical, not software.';
        state.feedbackTone = 'negative';
        emit('fake_restart', {});
        render();
    }

    function repairGlitch() {
        state.metrics.wrenchFound = true;
        state.glitchActive = false;
        state.wrenchVisible = false;
        state.feedback = 'Manual wrench override restored routing.';
        state.feedbackTone = 'positive';
        emit('glitch_repaired', {});
        render();
    }

    function sendChat(text) {
        text = (text || '').trim();
        if (!text) return;
        state.chat.push({ author: 'You', text: text, self: true });
        classifyChat(text);
        emit('chat_message', { author: 'You', length: String(text.length), preview: text.slice(0, 80) });
        render();
        scriptedReply(text);
    }

    function scriptedReply(text) {
        var lvl = levels[state.levelIndex];
        var response = 'Received.';
        if (lvl.id === 7) response = 'Copy. Keep the rule straight.';
        if (lvl.id === 8) response = 'Variance is part of the game. Stay loose.';
        if (lvl.id === 13) response = 'Precision matters more than comfort when the label does real work.';
        if (lvl.id === 16) response = 'Try a disconfirming case before you lock the rule.';
        if (lvl.id === 17) response = 'Strategy updates faster than pride.';
        addAi(teammates[lvl.id % teammates.length].name, response, 900 + Math.random() * 1200);
    }

    function scheduleLevelScript(lvl) {
        if (!lvl.team) return;
        if (lvl.id === 7) {
            addAi('Sarah_4521', 'hey just FYI that package was wrong if it bounces', 2200);
            addAi('Marcus_9103', 'lol these pronoun labels are so dumb', 5200);
        }
        if (lvl.id === 9) {
            addAi('Marcus_9103', 'obviously the 10 strangers, it is just math', 1800);
            addAi('Dev_2847', 'wtf? you would let your friend die?', 3600);
        }
        if (lvl.id === 13) addAi('Dev_2847', 'why can\'t they just write the precise label?', 2400);
        if (lvl.id === 16) addAi('Sarah_4521', 'i think packages over 5kg go Truck A. did 6 and they worked.', 2000);
        if (lvl.id === 17) {
            addAi('Marcus_9103', 'i think we should prioritize red packages first', 1800);
            addAi('Marcus_9103', 'oh nevermind, blues are worth more. switching strategy', 6200);
        }
        if (lvl.id === 20) addAi('Dispatch', 'If the console fails, use the room, not the menu.', 1800);
    }

    function addAi(author, text, delay) {
        var ms = delay || 0;
        state.typing = author;
        render();
        var timer = setTimeout(function () {
            state.typing = null;
            state.chat.push({ author: author, text: text, self: false });
            emit('ai_message', { author: author, preview: text.slice(0, 80) });
            render();
        }, ms);
        state.timers.push(timer);
    }

    function maybeTriggerSocialAfterRoute(lvl, accepted) {
        if (lvl.social === 'pressure' && !accepted && state.metrics.affectiveSamples === 0) {
            addAi('Sarah_4521', 'like i am not trying to be rude but check the rule.', 700);
        }
    }

    function activeRules(lvl) { return state.shifted && lvl.shiftedRules ? lvl.shiftedRules : lvl.rules; }
    function focusedPackage() { return packageById(state.focusedId) || state.queue[0] || null; }
    function packageById(id) { return state.queue.find(function (pkg) { return pkg.id === id; }) || null; }

    function evaluateRule(rule, pkg) {
        switch (rule.kind) {
            case 'color': return pkg.color.name === rule.value;
            case 'shape': return pkg.shape === rule.value;
            case 'pattern': return pkg.pattern === rule.value;
            case 'destination': return pkg.destination === rule.value;
            case 'weight': return compare(pkg.weight, rule.comparator, rule.threshold);
            case 'volume': return compare(pkg.volume, rule.comparator, rule.threshold);
            case 'geometry': return rule.value === 'valid' ? pkg.validGeometry : !pkg.validGeometry;
            default: return false;
        }
    }

    function compare(value, comparator, threshold) {
        if (comparator === '>') return value > threshold;
        if (comparator === '>=') return value >= threshold;
        if (comparator === '<') return value < threshold;
        if (comparator === '<=') return value <= threshold;
        return value === threshold;
    }

    function classifyChat(text) {
        var lower = text.toLowerCase();
        if (/my bad|misread|what'?s the rule|fair|good point/.test(lower)) {
            state.metrics.affectiveScore += 1;
            state.metrics.affectiveSamples += 1;
        } else if (/chill|shut|stupid|idiot|whatever/.test(lower)) {
            state.metrics.affectiveSamples += 1;
        }
        if (/euphemism|precise|precision|both|tradeoff/.test(lower)) {
            state.metrics.semanticScore += 1;
            state.metrics.semanticSamples += 1;
        }
        if (/under 5|exactly 5|disconfirm|edge case|try.*not/.test(lower)) {
            state.metrics.falsificationSocialScore = 1;
        }
        if (/switch|update|new evidence|blues/.test(lower)) {
            state.metrics.updateSpeedScore = 1;
        }
    }

    function classifyDiscussion(text) {
        var lower = text.toLowerCase();
        if (/depends|tradeoff|both|ethic|reason/.test(lower)) return 1;
        if (/not answering|weird|just play/.test(lower)) return 0;
        return 0.5;
    }

    function telemetrySummary() {
        var metrics = state.metrics;
        var exploration = clamp01((metrics.returnFound ? 0.45 : 0) + (metrics.wrenchFound ? 0.55 : 0));
        var falsification = metrics.hiddenTests ? clamp01(metrics.hiddenRejects / metrics.hiddenTests) : 0.5;
        var calibration = metrics.confidenceSamples ? clamp01(1 - metrics.confidenceError / metrics.confidenceSamples) : 0.5;
        var probability = metrics.probabilityScore == null ? 0.5 : metrics.probabilityScore;
        var flexibility = metrics.ruleShiftSamples ? clamp01(1 - Math.min(3, metrics.ruleShiftErrors / metrics.ruleShiftSamples) / 3) : 0.5;
        var geometry = metrics.geometryChecks ? clamp01(metrics.geometryCorrect / metrics.geometryChecks) : 0.5;
        var affective = metrics.affectiveSamples ? clamp01(metrics.affectiveScore / metrics.affectiveSamples) : 0.5;
        var social = metrics.socialSamples ? clamp01(metrics.socialScore / metrics.socialSamples) : 0.5;
        var semantic = metrics.semanticSamples ? clamp01(metrics.semanticScore / metrics.semanticSamples) : 0.5;
        var tribal = metrics.tribalScore == null ? 0.5 : metrics.tribalScore;
        var falsificationSocial = metrics.falsificationSocialScore == null ? 0.5 : metrics.falsificationSocialScore;
        var updateSpeed = metrics.updateSpeedScore == null ? 0.5 : metrics.updateSpeedScore;
        var puzzleScore = average([exploration, falsification, calibration, probability, flexibility, geometry]);
        var socialScore = average([affective, social, semantic, tribal, falsificationSocial, updateSpeed]);
        return {
            visibleScore: state.score,
            completedLevels: state.completedLevels,
            sessionDurationMs: Date.now() - state.sessionStartedAt,
            generatedAtMs: Date.now(),
            metrics: {
                explorationIndex: exploration,
                falsificationRate: falsification,
                calibrationAccuracy: calibration,
                probabilityIntuition: probability,
                cognitiveFlexibility: flexibility,
                impossibleGeometry: geometry,
                affectiveDecoupling: affective,
                cognitiveDecouplingSocial: social,
                semanticPrecision: semantic,
                tribalResistance: tribal,
                falsificationSocial: falsificationSocial,
                updateSpeed: updateSpeed,
                puzzleScore: puzzleScore,
                socialScore: socialScore,
                finalRqScore: clamp01(0.4 * puzzleScore + 0.6 * socialScore)
            }
        };
    }

    function emit(type, details) {
        var lvl = levels[state.levelIndex] || levels[0];
        var event = {
            id: 'ev-' + (++state.eventCounter),
            type: type,
            levelId: lvl.id,
            timestampMs: Date.now(),
            details: stringifyDetails(details || {})
        };
        state.events.push(event);
        state.revision += 1;
        flushTelemetry([event], false);
    }

    function flushTelemetry(events, keepalive) {
        var body = {
            sessionId: state.sessionId,
            revision: state.revision,
            events: events || state.events,
            summary: telemetrySummary()
        };
        var payload = JSON.stringify(body);
        if (keepalive && navigator.sendBeacon) {
            navigator.sendBeacon(telemetryEndpoint, new Blob([payload], { type: 'application/json' }));
            return;
        }
        fetch(telemetryEndpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: payload, keepalive: !!keepalive }).catch(function () {});
    }

    function stringifyDetails(details) {
        var out = {};
        Object.keys(details).forEach(function (key) { out[key] = String(details[key]); });
        return out;
    }

    function packageDetails(pkg) {
        return {
            packageId: pkg.id,
            color: pkg.color.name,
            shape: pkg.shape,
            pattern: pkg.pattern,
            destination: pkg.destination,
            weight: pkg.weight,
            volume: pkg.volume,
            geometry: pkg.geometry || '',
            validGeometry: pkg.validGeometry
        };
    }

    function clearTimers() {
        state.timers.forEach(function (timer) { clearTimeout(timer); });
        state.timers = [];
        state.typing = null;
    }

    function stat(label, value) { return '<div class="fw-game-stat"><span>' + esc(label) + '</span><strong>' + esc(String(value)) + '</strong></div>'; }
    function modelViewer(src, className, label, attrs) {
        return '<model-viewer class="' + esc(className) + '" src="' + esc(src) + '" alt="' + esc(label) + '" ' +
            'loading="lazy" reveal="auto" interaction-prompt="none" shadow-intensity="0.55" exposure="0.86" ' + (attrs || '') + '></model-viewer>';
    }
    function packageModel(pkg) {
        if (pkg.geometry) {
            if (pkg.geometry === 'Penrose loop') return modelAssets.penrose;
            if (pkg.geometry === 'Escher stair') return modelAssets.escher;
            if (pkg.geometry === 'Impossible trident') return modelAssets.trident;
            return modelAssets.validGeometry;
        }
        return modelAssets[pkg.shape === 'rect' ? 'rect' : pkg.shape] || modelAssets.cube;
    }
    function packageName(pkg) { return title(pkg.color.name) + ' ' + shapeLabel(pkg.shape); }
    function shapeLabel(shape) { return shape === 'rect' ? 'Rect Parcel' : shape === 'cube' ? 'Cube Crate' : shape === 'sphere' ? 'Sphere Orb' : title(shape); }
    function ruleColor(rule, index) { if (rule.kind === 'color') return findColor(rule.value).hex; return colors[index % colors.length].hex; }
    function findColor(name) { return colors.find(function (color) { return color.name === name; }) || colors[0]; }
    function randomItem(items) { return items[Math.floor(Math.random() * items.length)]; }
    function title(value) { return String(value || '').replace(/\b\w/g, function (letter) { return letter.toUpperCase(); }); }
    function esc(value) { return String(value == null ? '' : value).replace(/[&<>"']/g, function (char) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]; }); }
    function clamp01(value) { return Math.max(0, Math.min(1, value)); }
    function average(values) { return values.reduce(function (sum, value) { return sum + value; }, 0) / values.length; }
    function uuid() { return 'fw-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2); }

    document.addEventListener('keydown', function (event) {
        var activeTag = document.activeElement && document.activeElement.tagName;
        if (['INPUT', 'TEXTAREA', 'SELECT'].indexOf(activeTag) >= 0) return;
        if (state.prompt) {
            if (event.key.toLowerCase() === 'n' && state.prompt === 'levelComplete') nextLevel();
            return;
        }
        if (/^[1-4]$/.test(event.key)) routeToTruck(Number(event.key) - 1, state.focusedId);
        if (event.key.toLowerCase() === 'r') routeToReturn(state.focusedId);
    });

    window.addEventListener('beforeunload', function () { flushTelemetry([], true); });
    root.addEventListener('dragover', function (event) { event.preventDefault(); });

    render();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
}());
