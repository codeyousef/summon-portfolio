/**
 * Scratchpad Terminal
 * A simple command-line interface with fun commands
 */
(function () {
    'use strict';

    let input = null;
    let output = null;
    let bsod = null;
    let commandHistory = [];
    let historyIndex = -1;

    const COMMANDS = {
        help: {
            description: 'Show available commands',
            execute: () => {
                const lines = [
                    'Available commands:',
                    '',
                    '  help          - Show this help message',
                    '  clear         - Clear the terminal',
                    '  whoami        - Who are you?',
                    '  spawn [n]     - Spawn physics balls (default: 10)',
                    '  echo [text]   - Echo text back',
                    '  date          - Show current date/time',
                    '  matrix        - Enter the matrix',
                    '  exit          - Return to the main site',
                    '  sudo rm -rf / - Do NOT run this command',
                    '',
                    'Try typing something...'
                ];
                return lines.join('\n');
            }
        },
        clear: {
            description: 'Clear the terminal',
            execute: () => {
                output.innerHTML = '';
                return null;
            }
        },
        whoami: {
            description: 'Identity check',
            execute: () => {
                return 'guest\n\nYou are a wanderer in the void. A seeker of dead projects and hot takes.\nYour IP has been logged. Just kidding. Or am I?';
            }
        },
        spawn: {
            description: 'Spawn physics balls',
            execute: (args) => {
                const count = parseInt(args[0]) || 10;
                const maxCount = Math.min(count, 50);

                if (window.scratchpadPhysics) {
                    const centerX = window.innerWidth / 2;
                    const centerY = window.innerHeight / 3;

                    for (let i = 0; i < maxCount; i++) {
                        setTimeout(() => {
                            window.scratchpadPhysics.spawn(
                                centerX + (Math.random() - 0.5) * 200,
                                centerY + (Math.random() - 0.5) * 100
                            );
                        }, i * 50);
                    }

                    return `Spawning ${maxCount} balls...`;
                } else {
                    return 'Error: Physics engine not initialized';
                }
            }
        },
        echo: {
            description: 'Echo text',
            execute: (args) => {
                return args.join(' ') || '';
            }
        },
        date: {
            description: 'Show date/time',
            execute: () => {
                return new Date().toLocaleString();
            }
        },
        matrix: {
            description: 'Enter the matrix',
            execute: () => {
                // Add matrix effect temporarily
                document.body.style.filter = 'hue-rotate(90deg)';
                setTimeout(() => {
                    document.body.style.filter = '';
                }, 3000);
                return 'Wake up, Neo...\n\nThe Matrix has you...\n\nFollow the white rabbit.';
            }
        },
        exit: {
            description: 'Return to main site',
            execute: () => {
                setTimeout(() => {
                    window.location.href = '/';
                }, 500);
                return 'Exiting the void...';
            }
        },
        'sudo': {
            description: 'Superuser command',
            execute: (args) => {
                const fullCommand = args.join(' ');

                if (fullCommand === 'rm -rf /' || fullCommand === 'rm -rf /*') {
                    // Trigger BSOD
                    setTimeout(() => {
                        triggerBSOD();
                    }, 500);
                    return 'rm: removing everything... please wait...';
                } else if (fullCommand.startsWith('rm')) {
                    return `rm: cannot remove '${args.slice(1).join(' ')}': Permission denied (this is a good thing)`;
                } else {
                    return `sudo: ${args[0]}: command not found`;
                }
            }
        },
        ls: {
            description: 'List directory contents',
            execute: () => {
                return 'dead_projects/\nhot_takes/\nbroken_dreams.txt\nTODO.md (last modified: never)\nnode_modules/ (warning: 2.3GB)';
            }
        },
        cat: {
            description: 'Print file contents',
            execute: (args) => {
                const file = args[0];
                if (!file) return 'cat: missing operand';

                const files = {
                    'TODO.md': '# TODO\n\n- [ ] Finish this project\n- [ ] Actually finish this project\n- [ ] Stop adding to the todo list\n- [ ] ...',
                    'broken_dreams.txt': 'Error: File too large to display\n(Just kidding, it\'s empty now)',
                    '.secret': 'You found a secret! But it\'s just this message. Sorry.'
                };

                return files[file] || `cat: ${file}: No such file or directory`;
            }
        },
        ping: {
            description: 'Ping a host',
            execute: (args) => {
                const host = args[0] || 'localhost';
                return `PING ${host}: 64 bytes from ${host}: icmp_seq=1 ttl=64 time=0.042ms\n` +
                    `PING ${host}: 64 bytes from ${host}: icmp_seq=2 ttl=64 time=0.039ms\n` +
                    `PING ${host}: 64 bytes from ${host}: icmp_seq=3 ttl=64 time=0.041ms\n` +
                    `\n--- ${host} ping statistics ---\n` +
                    `3 packets transmitted, 3 received, 0% packet loss`;
            }
        },
        neofetch: {
            description: 'System info',
            execute: () => {
                return `
       ████████████       guest@scratchpad
     ██            ██     -----------------
   ██    ██████    ██     OS: VoidOS 0.0.1
   ██  ██████████  ██     Host: The Scratchpad
   ██  ██████████  ██     Kernel: chaos-1.0
   ██  ██████████  ██     Uptime: since you got here
   ██    ██████    ██     Shell: scratchpad-term
     ██            ██     Theme: Brutalist Dark
       ████████████       CPU: Existential Dread @ 100%
                          Memory: Full of regrets
                `;
            }
        },
        coffee: {
            description: 'Brew coffee',
            execute: () => {
                return `
   ( (
    ) )
  ........
  |      |]
  \\      /
   \`----'

418: I'm a teapot. Try 'tea' instead.
                `;
            }
        },
        tea: {
            description: 'Brew tea',
            execute: () => {
                return 'Here\'s your tea: 🍵\n\nRemember to take breaks.';
            }
        }
    };

    function init() {
        input = document.getElementById('terminal-input');
        output = document.getElementById('terminal-output');
        bsod = document.getElementById('bsod-overlay');

        if (!input || !output) {
            console.warn('Terminal elements not found');
            return;
        }

        // Handle input
        input.addEventListener('keydown', handleKeyDown);

        // Focus input when clicking on terminal
        document.getElementById('terminal-overlay')?.addEventListener('click', () => {
            input.focus();
        });

        // BSOD click to dismiss
        bsod?.addEventListener('click', dismissBSOD);

        console.log('%c[scratchpad] Terminal initialized', 'color: #00ff00;');
    }

    function handleKeyDown(e) {
        if (e.key === 'Enter') {
            const command = input.value.trim();
            if (command) {
                executeCommand(command);
                commandHistory.push(command);
                historyIndex = commandHistory.length;
            }
            input.value = '';
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (historyIndex > 0) {
                historyIndex--;
                input.value = commandHistory[historyIndex];
            }
        } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (historyIndex < commandHistory.length - 1) {
                historyIndex++;
                input.value = commandHistory[historyIndex];
            } else {
                historyIndex = commandHistory.length;
                input.value = '';
            }
        } else if (e.key === 'Tab') {
            e.preventDefault();
            autocomplete();
        } else if (e.ctrlKey && e.key === 'l') {
            e.preventDefault();
            COMMANDS.clear.execute();
        }
    }

    function executeCommand(commandString) {
        const parts = commandString.split(/\s+/);
        const cmd = parts[0].toLowerCase();
        const args = parts.slice(1);

        // Add command to output
        addLine(`<span class="terminal-prompt">guest@scratchpad:~$</span> <span class="terminal-command">${escapeHtml(commandString)}</span>`);

        // Execute command
        if (COMMANDS[cmd]) {
            const result = COMMANDS[cmd].execute(args);
            if (result !== null) {
                addLine(`<span class="terminal-output">${escapeHtml(result)}</span>`);
            }
        } else if (cmd) {
            addLine(`<span class="terminal-error">Command not found: ${escapeHtml(cmd)}</span>`);
            addLine(`<span class="terminal-output">Type 'help' for available commands</span>`);
        }

        // Scroll to bottom
        output.scrollTop = output.scrollHeight;
    }

    function addLine(html) {
        const line = document.createElement('div');
        line.className = 'terminal-line';
        line.innerHTML = html;
        output.appendChild(line);
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function autocomplete() {
        const currentInput = input.value.toLowerCase();
        if (!currentInput) return;

        const matches = Object.keys(COMMANDS).filter(cmd => cmd.startsWith(currentInput));

        if (matches.length === 1) {
            input.value = matches[0] + ' ';
        } else if (matches.length > 1) {
            addLine(`<span class="terminal-output">${matches.join('  ')}</span>`);
        }
    }

    function triggerBSOD() {
        if (bsod) {
            bsod.classList.add('active');

            // Simulate progress
            const progress = document.getElementById('bsod-progress');
            if (progress) {
                let percent = 0;
                const interval = setInterval(() => {
                    percent += Math.random() * 15;
                    if (percent >= 100) {
                        percent = 100;
                        clearInterval(interval);
                        progress.textContent = 'Physical memory dump complete. System halted.';
                    } else {
                        progress.textContent = `Physical memory dump: ${Math.floor(percent)}% complete...`;
                    }
                }, 200);
            }
        }
    }

    function dismissBSOD() {
        if (bsod) {
            bsod.classList.remove('active');

            // Add a fun message after the scare
            addLine('<span class="terminal-success">// System recovered. That was close.</span>');
            addLine('<span class="terminal-output">Pro tip: Maybe don\'t run rm -rf / next time?</span>');
        }
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
