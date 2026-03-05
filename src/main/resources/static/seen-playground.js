// Seen Playground - Monaco Editor + API integration
(function () {
    'use strict';

    let editor = null;
    let isRunning = false;

    // Example programs
    const EXAMPLES = {
        'Hello World': `fun main() {
    println("Hello, World!")
}`,
        'Fibonacci': `fun fib(n: Int): Int {
    if n <= 1 {
        return n
    }
    return fib(n - 1) + fib(n - 2)
}

fun main() {
    var i = 0
    while i < 15 {
        println(fib(i))
        i = i + 1
    }
}`,
        'FizzBuzz': `fun main() {
    var i = 1
    while i <= 30 {
        if i % 15 == 0 {
            println("FizzBuzz")
        } else {
            if i % 3 == 0 {
                println("Fizz")
            } else {
                if i % 5 == 0 {
                    println("Buzz")
                } else {
                    println(i)
                }
            }
        }
        i = i + 1
    }
}`,
        'Classes': `class Point {
    var x: Int
    var y: Int

    fun new(x: Int, y: Int): Point {
        this.x = x
        this.y = y
        return this
    }

    fun distanceTo(other: Point): Float {
        let dx = this.x - other.x
        let dy = this.y - other.y
        return sqrt(toFloat(dx * dx + dy * dy))
    }

    fun toString(): String {
        return "(" + toStr(this.x) + ", " + toStr(this.y) + ")"
    }
}

fun main() {
    let a = Point.new(0, 0)
    let b = Point.new(3, 4)
    println("Point A: " + a.toString())
    println("Point B: " + b.toString())
    println("Distance: " + toStr(a.distanceTo(b)))
}`,
        'Arabic': `// Seen supports multi-language keywords!
fun main() {
    println("مرحبا بالعالم - Hello World!")
    println("Seen يدعم اللغة العربية")

    var sum = 0
    var i = 1
    while i <= 100 {
        sum = sum + i
        i = i + 1
    }
    println("Sum 1..100 = " + toStr(sum))
}`,
        'Factorial': `fun factorial(n: Int): Int {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}

fun main() {
    var i = 0
    while i <= 12 {
        println(toStr(i) + "! = " + toStr(factorial(i)))
        i = i + 1
    }
}`
    };

    // Seen language definition for Monaco
    const SEEN_LANG = {
        defaultToken: '',
        tokenPostfix: '.seen',

        keywords: [
            'fun', 'let', 'var', 'if', 'else', 'while', 'for', 'in', 'return',
            'class', 'this', 'new', 'import', 'from', 'as', 'match', 'when',
            'break', 'continue', 'true', 'false', 'null', 'enum', 'trait',
            'impl', 'pub', 'static', 'async', 'await', 'try', 'catch',
            'throw', 'defer', 'errdefer', 'unsafe', 'comptime', 'struct',
            'union', 'type', 'where', 'const', 'mut', 'override', 'data'
        ],

        typeKeywords: [
            'Int', 'Float', 'String', 'Bool', 'Char', 'Void', 'Array',
            'HashMap', 'Vec', 'Result', 'Option'
        ],

        operators: [
            '=', '>', '<', '!', '~', '?', ':', '==', '<=', '>=', '!=',
            '&&', '||', '++', '--', '+', '-', '*', '/', '&', '|', '^',
            '%', '<<', '>>', '+=', '-=', '*=', '/=', '&=', '|=', '^=',
            '<<=', '>>=', '->', '=>'
        ],

        symbols: /[=><!~?:&|+\-*\/\^%]+/,

        tokenizer: {
            root: [
                // Identifiers and keywords
                [/[a-zA-Z_]\w*/, {
                    cases: {
                        '@typeKeywords': 'type.identifier',
                        '@keywords': 'keyword',
                        '@default': 'identifier'
                    }
                }],

                // Whitespace
                {include: '@whitespace'},

                // Delimiters and operators
                [/[{}()\[\]]/, '@brackets'],
                [/@symbols/, {
                    cases: {
                        '@operators': 'operator',
                        '@default': ''
                    }
                }],

                // Numbers
                [/\d*\.\d+([eE][\-+]?\d+)?/, 'number.float'],
                [/0[xX][0-9a-fA-F]+/, 'number.hex'],
                [/0[bB][01]+/, 'number.binary'],
                [/\d+/, 'number'],

                // Delimiter
                [/[;,.]/, 'delimiter'],

                // Strings
                [/"([^"\\]|\\.)*$/, 'string.invalid'],
                [/"/, {token: 'string.quote', bracket: '@open', next: '@string'}],

                // Characters
                [/'[^\\']'/, 'string'],
                [/'(\\.)+'/, 'string'],
            ],

            string: [
                [/[^\\"]+/, 'string'],
                [/\\./, 'string.escape'],
                [/"/, {token: 'string.quote', bracket: '@close', next: '@pop'}]
            ],

            whitespace: [
                [/[ \t\r\n]+/, 'white'],
                [/\/\/.*$/, 'comment'],
                [/\/\*/, 'comment', '@comment'],
            ],

            comment: [
                [/[^\/*]+/, 'comment'],
                [/\/\*/, 'comment', '@push'],
                [/\*\//, 'comment', '@pop'],
                [/[\/*]/, 'comment']
            ],
        }
    };

    // Seen theme for Monaco (GitHub Dark style)
    const SEEN_THEME = {
        base: 'vs-dark',
        inherit: true,
        rules: [
            {token: 'keyword', foreground: 'ff7b72', fontStyle: 'bold'},
            {token: 'type.identifier', foreground: '79c0ff'},
            {token: 'identifier', foreground: 'e6edf3'},
            {token: 'number', foreground: '79c0ff'},
            {token: 'number.float', foreground: '79c0ff'},
            {token: 'number.hex', foreground: '79c0ff'},
            {token: 'string', foreground: 'a5d6ff'},
            {token: 'string.escape', foreground: '79c0ff'},
            {token: 'comment', foreground: '8b949e', fontStyle: 'italic'},
            {token: 'operator', foreground: 'ff7b72'},
            {token: 'delimiter', foreground: 'e6edf3'},
        ],
        colors: {
            'editor.background': '#0d1117',
            'editor.foreground': '#e6edf3',
            'editor.lineHighlightBackground': '#161b2299',
            'editorLineNumber.foreground': '#484f58',
            'editorLineNumber.activeForeground': '#e6edf3',
            'editor.selectionBackground': '#264f78',
            'editor.inactiveSelectionBackground': '#264f7840',
            'editorCursor.foreground': '#58a6ff',
            'editorIndentGuide.background': '#21262d',
            'editorIndentGuide.activeBackground': '#30363d',
        }
    };

    function initMonaco() {
        if (typeof require === 'undefined' || typeof require.config === 'undefined') {
            setTimeout(initMonaco, 100);
            return;
        }

        require.config({
            paths: {vs: 'https://cdn.jsdelivr.net/npm/monaco-editor@0.45.0/min/vs'}
        });

        require(['vs/editor/editor.main'], function () {
            // Register Seen language
            monaco.languages.register({id: 'seen'});
            monaco.languages.setMonarchTokensProvider('seen', SEEN_LANG);
            monaco.editor.defineTheme('seen-dark', SEEN_THEME);

            // Create editor
            const container = document.getElementById('editor-container');
            if (!container) return;

            editor = monaco.editor.create(container, {
                value: EXAMPLES['Hello World'],
                language: 'seen',
                theme: 'seen-dark',
                fontSize: 14,
                fontFamily: '"JetBrains Mono", "Fira Code", "Cascadia Code", ui-monospace, monospace',
                minimap: {enabled: false},
                scrollBeyondLastLine: false,
                padding: {top: 8},
                lineNumbers: 'on',
                renderLineHighlight: 'line',
                automaticLayout: true,
                tabSize: 4,
                insertSpaces: true,
                bracketPairColorization: {enabled: true},
                guides: {indentation: true},
                smoothScrolling: true,
                cursorBlinking: 'smooth',
            });

            // Ctrl+Enter to run
            editor.addAction({
                id: 'run-code',
                label: 'Run Code',
                keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter],
                run: function () {
                    runCode();
                }
            });

            // Handle resize
            window.addEventListener('resize', function () {
                editor.layout();
            });
        });
    }

    function buildExampleSelector() {
        const container = document.getElementById('example-selector');
        if (!container) return;

        const select = document.createElement('select');
        select.id = 'example-select';
        select.className = 'example-select';

        const defaultOpt = document.createElement('option');
        defaultOpt.value = '';
        defaultOpt.textContent = 'Load Example...';
        defaultOpt.disabled = true;
        defaultOpt.selected = true;
        select.appendChild(defaultOpt);

        Object.keys(EXAMPLES).forEach(function (name) {
            const opt = document.createElement('option');
            opt.value = name;
            opt.textContent = name;
            select.appendChild(opt);
        });

        select.addEventListener('change', function () {
            const name = select.value;
            if (name && EXAMPLES[name] && editor) {
                editor.setValue(EXAMPLES[name]);
                select.selectedIndex = 0;
                clearOutput();
            }
        });

        container.appendChild(select);
    }

    function clearOutput() {
        const output = document.getElementById('output');
        if (output) {
            output.textContent = 'Click "Run" or press Ctrl+Enter to execute your code.';
            output.style.color = '#8b949e';
        }
        const timing = document.getElementById('timing-bar');
        if (timing) timing.style.display = 'none';
        setStatus('Ready', '#8b949e');
    }

    function setStatus(text, color) {
        const el = document.getElementById('status-indicator');
        if (el) {
            el.textContent = text;
            el.style.color = color || '#8b949e';
        }
    }

    function setOutput(text, isError) {
        const output = document.getElementById('output');
        if (!output) return;
        output.textContent = text || '(no output)';
        output.style.color = isError ? '#f85149' : '#e6edf3';
    }

    function setTiming(compileMs, runMs) {
        const bar = document.getElementById('timing-bar');
        if (!bar) return;
        bar.style.display = 'flex';
        bar.textContent = 'Compile: ' + compileMs + 'ms | Run: ' + runMs + 'ms';
    }

    async function runCode() {
        if (isRunning || !editor) return;
        isRunning = true;

        const code = editor.getValue();
        const runBtn = document.getElementById('run-btn');
        if (runBtn) {
            runBtn.style.opacity = '0.6';
            runBtn.style.pointerEvents = 'none';
        }

        setStatus('Compiling...', '#d29922');
        setOutput('Compiling...', false);

        try {
            const resp = await fetch('/api/seen/run', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({code: code})
            });

            const result = await resp.json();

            if (result.error && result.error.length > 0) {
                if (result.output && result.output.length > 0) {
                    setOutput(result.output + '\n\n--- Errors ---\n' + result.error, true);
                } else {
                    setOutput(result.error, true);
                }
                setStatus('Error (exit ' + result.exitCode + ')', '#f85149');
            } else {
                setOutput(result.output || '(no output)', false);
                setStatus('Success', '#3fb950');
            }

            if (result.compileTimeMs !== undefined) {
                setTiming(result.compileTimeMs, result.runTimeMs);
            }
        } catch (err) {
            setOutput('Failed to connect to server: ' + err.message, true);
            setStatus('Error', '#f85149');
        } finally {
            isRunning = false;
            if (runBtn) {
                runBtn.style.opacity = '1';
                runBtn.style.pointerEvents = 'auto';
            }
        }
    }

    // Initialize
    document.addEventListener('DOMContentLoaded', function () {
        initMonaco();
        buildExampleSelector();

        // Run button click
        const runBtn = document.getElementById('run-btn');
        if (runBtn) {
            runBtn.addEventListener('click', runCode);
        }
    });
})();
