// Seen Playground - Monaco Editor + API integration
(function () {
    'use strict';

    let editor = null;
    let isRunning = false;
    let currentLanguage = 'en';

    // Per-language example programs
    const EXAMPLES = {
        en: {
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
        },
        ar: {
            '\u0645\u0631\u062D\u0628\u0627 \u0628\u0627\u0644\u0639\u0627\u0644\u0645': `\u062F\u0627\u0644\u0629 \u0631\u0626\u064A\u0633\u064A\u0629() {
    \u0627\u0637\u0628\u0639("\u0645\u0631\u062D\u0628\u0627 \u0628\u0627\u0644\u0639\u0627\u0644\u0645!")
}`,
            '\u0641\u064A\u0628\u0648\u0646\u0627\u062A\u0634\u064A': `\u062F\u0627\u0644\u0629 \u0641\u064A\u0628(\u0646: \u0639\u062F\u062F): \u0639\u062F\u062F {
    \u0625\u0630\u0627 \u0646 <= 1 {
        \u0631\u062C\u0639 \u0646
    }
    \u0631\u062C\u0639 \u0641\u064A\u0628(\u0646 - 1) + \u0641\u064A\u0628(\u0646 - 2)
}

\u062F\u0627\u0644\u0629 \u0631\u0626\u064A\u0633\u064A\u0629() {
    \u0645\u062A\u063A\u064A\u0631 \u0639 = 0
    \u0628\u064A\u0646\u0645\u0627 \u0639 < 15 {
        \u0627\u0637\u0628\u0639(\u0641\u064A\u0628(\u0639))
        \u0639 = \u0639 + 1
    }
}`,
            '\u0641\u0626\u0627\u062A': `\u0641\u0626\u0629 \u0646\u0642\u0637\u0629 {
    \u0645\u062A\u063A\u064A\u0631 \u0633: \u0639\u062F\u062F
    \u0645\u062A\u063A\u064A\u0631 \u0635: \u0639\u062F\u062F

    \u062F\u0627\u0644\u0629 \u062C\u062F\u064A\u062F(\u0633: \u0639\u062F\u062F, \u0635: \u0639\u062F\u062F): \u0646\u0642\u0637\u0629 {
        \u0647\u0630\u0627.\u0633 = \u0633
        \u0647\u0630\u0627.\u0635 = \u0635
        \u0631\u062C\u0639 \u0647\u0630\u0627
    }

    \u062F\u0627\u0644\u0629 \u0625\u0644\u0649_\u0646\u0635(): \u0646\u0635 {
        \u0631\u062C\u0639 "(" + \u0625\u0644\u0649_\u0646\u0635(\u0647\u0630\u0627.\u0633) + ", " + \u0625\u0644\u0649_\u0646\u0635(\u0647\u0630\u0627.\u0635) + ")"
    }
}

\u062F\u0627\u0644\u0629 \u0631\u0626\u064A\u0633\u064A\u0629() {
    \u0627\u062C\u0639\u0644 \u0623 = \u0646\u0642\u0637\u0629.\u062C\u062F\u064A\u062F(3, 4)
    \u0627\u0637\u0628\u0639("\u0627\u0644\u0646\u0642\u0637\u0629: " + \u0623.\u0625\u0644\u0649_\u0646\u0635())
}`
        },
        es: {
            'Hola Mundo': `funci\u00F3n principal() {
    imprimir("Hola, Mundo!")
}`,
            'Fibonacci': `funci\u00F3n fib(n: entero): entero {
    si n <= 1 {
        retornar n
    }
    retornar fib(n - 1) + fib(n - 2)
}

funci\u00F3n principal() {
    variable i = 0
    mientras i < 15 {
        imprimir(fib(i))
        i = i + 1
    }
}`,
            'Clases': `clase Punto {
    variable x: entero
    variable y: entero

    funci\u00F3n nuevo(x: entero, y: entero): Punto {
        esto.x = x
        esto.y = y
        retornar esto
    }

    funci\u00F3n aTexto(): cadena {
        retornar "(" + aTexto(esto.x) + ", " + aTexto(esto.y) + ")"
    }
}

funci\u00F3n principal() {
    sea a = Punto.nuevo(3, 4)
    imprimir("Punto: " + a.aTexto())
}`
        },
        ru: {
            '\u041F\u0440\u0438\u0432\u0435\u0442 \u041C\u0438\u0440': `\u0444\u0443\u043D\u043A\u0446\u0438\u044F \u0433\u043B\u0430\u0432\u043D\u0430\u044F() {
    \u043F\u0435\u0447\u0430\u0442\u044C("\u041F\u0440\u0438\u0432\u0435\u0442, \u041C\u0438\u0440!")
}`,
            '\u0424\u0438\u0431\u043E\u043D\u0430\u0447\u0447\u0438': `\u0444\u0443\u043D\u043A\u0446\u0438\u044F \u0444\u0438\u0431(\u043D: \u0446\u0435\u043B\u043E\u0435): \u0446\u0435\u043B\u043E\u0435 {
    \u0435\u0441\u043B\u0438 \u043D <= 1 {
        \u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043D
    }
    \u0432\u0435\u0440\u043D\u0443\u0442\u044C \u0444\u0438\u0431(\u043D - 1) + \u0444\u0438\u0431(\u043D - 2)
}

\u0444\u0443\u043D\u043A\u0446\u0438\u044F \u0433\u043B\u0430\u0432\u043D\u0430\u044F() {
    \u043F\u0435\u0440\u0435\u043C\u0435\u043D\u043D\u0430\u044F \u0438 = 0
    \u043F\u043E\u043A\u0430 \u0438 < 15 {
        \u043F\u0435\u0447\u0430\u0442\u044C(\u0444\u0438\u0431(\u0438))
        \u0438 = \u0438 + 1
    }
}`,
            '\u041A\u043B\u0430\u0441\u0441\u044B': `\u043A\u043B\u0430\u0441\u0441 \u0422\u043E\u0447\u043A\u0430 {
    \u043F\u0435\u0440\u0435\u043C\u0435\u043D\u043D\u0430\u044F \u0445: \u0446\u0435\u043B\u043E\u0435
    \u043F\u0435\u0440\u0435\u043C\u0435\u043D\u043D\u0430\u044F \u0443: \u0446\u0435\u043B\u043E\u0435

    \u0444\u0443\u043D\u043A\u0446\u0438\u044F \u043D\u043E\u0432\u044B\u0439(\u0445: \u0446\u0435\u043B\u043E\u0435, \u0443: \u0446\u0435\u043B\u043E\u0435): \u0422\u043E\u0447\u043A\u0430 {
        \u044D\u0442\u043E.\u0445 = \u0445
        \u044D\u0442\u043E.\u0443 = \u0443
        \u0432\u0435\u0440\u043D\u0443\u0442\u044C \u044D\u0442\u043E
    }

    \u0444\u0443\u043D\u043A\u0446\u0438\u044F \u0432_\u0441\u0442\u0440\u043E\u043A\u0443(): \u0441\u0442\u0440\u043E\u043A\u0430 {
        \u0432\u0435\u0440\u043D\u0443\u0442\u044C "(" + \u0432_\u0441\u0442\u0440\u043E\u043A\u0443(\u044D\u0442\u043E.\u0445) + ", " + \u0432_\u0441\u0442\u0440\u043E\u043A\u0443(\u044D\u0442\u043E.\u0443) + ")"
    }
}

\u0444\u0443\u043D\u043A\u0446\u0438\u044F \u0433\u043B\u0430\u0432\u043D\u0430\u044F() {
    \u043F\u0443\u0441\u0442\u044C \u0430 = \u0422\u043E\u0447\u043A\u0430.\u043D\u043E\u0432\u044B\u0439(3, 4)
    \u043F\u0435\u0447\u0430\u0442\u044C("\u0422\u043E\u0447\u043A\u0430: " + \u0430.\u0432_\u0441\u0442\u0440\u043E\u043A\u0443())
}`
        },
        zh: {
            '\u4F60\u597D\u4E16\u754C': `\u51FD\u6570 \u4E3B\u51FD\u6570() {
    \u6253\u5370("\u4F60\u597D\uFF0C\u4E16\u754C\uFF01")
}`,
            '\u6590\u6CE2\u90A3\u5951': `\u51FD\u6570 \u6590\u6CE2(\u6570: \u6574\u6570): \u6574\u6570 {
    \u5982\u679C \u6570 <= 1 {
        \u8FD4\u56DE \u6570
    }
    \u8FD4\u56DE \u6590\u6CE2(\u6570 - 1) + \u6590\u6CE2(\u6570 - 2)
}

\u51FD\u6570 \u4E3B\u51FD\u6570() {
    \u53D8\u91CF \u6211 = 0
    \u5F53 \u6211 < 15 {
        \u6253\u5370(\u6590\u6CE2(\u6211))
        \u6211 = \u6211 + 1
    }
}`,
            '\u7C7B': `\u7C7B \u70B9 {
    \u53D8\u91CF \u6A2A: \u6574\u6570
    \u53D8\u91CF \u7EB5: \u6574\u6570

    \u51FD\u6570 \u65B0\u5EFA(\u6A2A: \u6574\u6570, \u7EB5: \u6574\u6570): \u70B9 {
        \u6B64.\u6A2A = \u6A2A
        \u6B64.\u7EB5 = \u7EB5
        \u8FD4\u56DE \u6B64
    }

    \u51FD\u6570 \u8F6C\u6587\u672C(): \u5B57\u7B26\u4E32 {
        \u8FD4\u56DE "(" + \u8F6C\u6587\u672C(\u6B64.\u6A2A) + ", " + \u8F6C\u6587\u672C(\u6B64.\u7EB5) + ")"
    }
}

\u51FD\u6570 \u4E3B\u51FD\u6570() {
    \u8BA9 \u7532 = \u70B9.\u65B0\u5EFA(3, 4)
    \u6253\u5370("\u70B9: " + \u7532.\u8F6C\u6587\u672C())
}`
        },
        ja: {
            '\u3053\u3093\u306B\u3061\u306F\u4E16\u754C': `\u95A2\u6570 \u30E1\u30A4\u30F3() {
    \u8868\u793A("\u3053\u3093\u306B\u3061\u306F\u3001\u4E16\u754C\uFF01")
}`,
            '\u30D5\u30A3\u30DC\u30CA\u30C3\u30C1': `\u95A2\u6570 \u30D5\u30A3\u30DC(\u6570: \u6574\u6570): \u6574\u6570 {
    \u3082\u3057 \u6570 <= 1 {
        \u623B\u308B \u6570
    }
    \u623B\u308B \u30D5\u30A3\u30DC(\u6570 - 1) + \u30D5\u30A3\u30DC(\u6570 - 2)
}

\u95A2\u6570 \u30E1\u30A4\u30F3() {
    \u5909\u6570 \u6211 = 0
    \u9593 \u6211 < 15 {
        \u8868\u793A(\u30D5\u30A3\u30DC(\u6211))
        \u6211 = \u6211 + 1
    }
}`,
            '\u30AF\u30E9\u30B9': `\u30AF\u30E9\u30B9 \u70B9 {
    \u5909\u6570 \u6A2A: \u6574\u6570
    \u5909\u6570 \u7E26: \u6574\u6570

    \u95A2\u6570 \u65B0\u898F(\u6A2A: \u6574\u6570, \u7E26: \u6574\u6570): \u70B9 {
        \u3053\u308C.\u6A2A = \u6A2A
        \u3053\u308C.\u7E26 = \u7E26
        \u623B\u308B \u3053\u308C
    }

    \u95A2\u6570 \u6587\u5B57\u5217\u5316(): \u6587\u5B57\u5217 {
        \u623B\u308B "(" + \u6587\u5B57\u5217\u5316(\u3053\u308C.\u6A2A) + ", " + \u6587\u5B57\u5217\u5316(\u3053\u308C.\u7E26) + ")"
    }
}

\u95A2\u6570 \u30E1\u30A4\u30F3() {
    \u5B9A\u6570 \u7532 = \u70B9.\u65B0\u898F(3, 4)
    \u8868\u793A("\u70B9: " + \u7532.\u6587\u5B57\u5217\u5316())
}`
        }
    };

    // Per-language keywords for Monaco syntax highlighting
    const LANG_KEYWORDS = {
        en: ['fun', 'let', 'var', 'if', 'else', 'while', 'for', 'in', 'return',
            'class', 'this', 'new', 'import', 'from', 'as', 'match', 'when',
            'break', 'continue', 'true', 'false', 'null', 'enum', 'trait',
            'impl', 'pub', 'static', 'main'],
        ar: ['\u062F\u0627\u0644\u0629', '\u0627\u062C\u0639\u0644', '\u0645\u062A\u063A\u064A\u0631', '\u0625\u0630\u0627', '\u0648\u0625\u0644\u0627', '\u0628\u064A\u0646\u0645\u0627', '\u0644\u0643\u0644', '\u0641\u064A', '\u0631\u062C\u0639',
            '\u0641\u0626\u0629', '\u0647\u0630\u0627', '\u062C\u062F\u064A\u062F', '\u0635\u062D\u064A\u062D', '\u062E\u0637\u0623', '\u0631\u0626\u064A\u0633\u064A\u0629', '\u0627\u0637\u0628\u0639'],
        es: ['funci\u00F3n', 'sea', 'variable', 'si', 'sino', 'mientras', 'para', 'en', 'retornar',
            'clase', 'esto', 'nuevo', 'verdadero', 'falso', 'principal', 'imprimir'],
        ru: ['\u0444\u0443\u043D\u043A\u0446\u0438\u044F', '\u043F\u0443\u0441\u0442\u044C', '\u043F\u0435\u0440\u0435\u043C\u0435\u043D\u043D\u0430\u044F', '\u0435\u0441\u043B\u0438', '\u0438\u043D\u0430\u0447\u0435', '\u043F\u043E\u043A\u0430', '\u0434\u043B\u044F', '\u0432', '\u0432\u0435\u0440\u043D\u0443\u0442\u044C',
            '\u043A\u043B\u0430\u0441\u0441', '\u044D\u0442\u043E', '\u043D\u043E\u0432\u044B\u0439', '\u0438\u0441\u0442\u0438\u043D\u0430', '\u043B\u043E\u0436\u044C', '\u0433\u043B\u0430\u0432\u043D\u0430\u044F', '\u043F\u0435\u0447\u0430\u0442\u044C'],
        zh: ['\u51FD\u6570', '\u8BA9', '\u53D8\u91CF', '\u5982\u679C', '\u5426\u5219', '\u5F53', '\u5BF9\u4E8E', '\u5728', '\u8FD4\u56DE',
            '\u7C7B', '\u6B64', '\u65B0\u5EFA', '\u771F', '\u5047', '\u4E3B\u51FD\u6570', '\u6253\u5370'],
        ja: ['\u95A2\u6570', '\u5B9A\u6570', '\u5909\u6570', '\u3082\u3057', '\u305D\u3046\u3067\u306A\u3051\u308C\u3070', '\u9593', '\u306E\u305F\u3081\u306B', '\u3067', '\u623B\u308B',
            '\u30AF\u30E9\u30B9', '\u3053\u308C', '\u65B0\u898F', '\u771F', '\u507D', '\u30E1\u30A4\u30F3', '\u8868\u793A']
    };

    const LANG_TYPE_KEYWORDS = {
        en: ['Int', 'Float', 'String', 'Bool', 'Char', 'Void', 'Array'],
        ar: ['\u0639\u062F\u062F', '\u0639\u0634\u0631\u064A', '\u0646\u0635'],
        es: ['entero', 'decimal', 'cadena'],
        ru: ['\u0446\u0435\u043B\u043E\u0435', '\u0434\u0440\u043E\u0431\u043D\u043E\u0435', '\u0441\u0442\u0440\u043E\u043A\u0430'],
        zh: ['\u6574\u6570', '\u6D6E\u70B9', '\u5B57\u7B26\u4E32'],
        ja: ['\u6574\u6570', '\u6D6E\u52D5\u5C0F\u6570', '\u6587\u5B57\u5217']
    };

    const LANGUAGE_NAMES = {
        en: 'English',
        ar: '\u0627\u0644\u0639\u0631\u0628\u064A\u0629',
        es: 'Espa\u00F1ol',
        ru: '\u0420\u0443\u0441\u0441\u043A\u0438\u0439',
        zh: '\u4E2D\u6587',
        ja: '\u65E5\u672C\u8A9E'
    };

    // Build Seen language definition for Monaco with given keyword set
    function buildSeenLangDef(langCode) {
        const keywords = LANG_KEYWORDS[langCode] || LANG_KEYWORDS.en;
        const typeKeywords = LANG_TYPE_KEYWORDS[langCode] || LANG_TYPE_KEYWORDS.en;
        // For non-Latin languages, we need a broader identifier regex
        const usesLatinOnly = (langCode === 'en' || langCode === 'es');

        return {
            defaultToken: '',
            tokenPostfix: '.seen',
            keywords: keywords,
            typeKeywords: typeKeywords,
            operators: [
                '=', '>', '<', '!', '~', '?', ':', '==', '<=', '>=', '!=',
                '&&', '||', '++', '--', '+', '-', '*', '/', '&', '|', '^',
                '%', '<<', '>>', '+=', '-=', '*=', '/=', '&=', '|=', '^=',
                '<<=', '>>=', '->', '=>'
            ],
            symbols: /[=><!~?:&|+\-*\/\^%]+/,
            tokenizer: {
                root: [
                    // Identifiers and keywords — broad Unicode range for non-Latin scripts
                    [usesLatinOnly ? /[a-zA-Z_]\w*/ : /[\p{L}_][\p{L}\p{N}_]*/u, {
                        cases: {
                            '@typeKeywords': 'type.identifier',
                            '@keywords': 'keyword',
                            '@default': 'identifier'
                        }
                    }],
                    {include: '@whitespace'},
                    [/[{}()\[\]]/, '@brackets'],
                    [/@symbols/, {
                        cases: {
                            '@operators': 'operator',
                            '@default': ''
                        }
                    }],
                    [/\d*\.\d+([eE][\-+]?\d+)?/, 'number.float'],
                    [/0[xX][0-9a-fA-F]+/, 'number.hex'],
                    [/0[bB][01]+/, 'number.binary'],
                    [/\d+/, 'number'],
                    [/[;,.]/, 'delimiter'],
                    [/"([^"\\]|\\.)*$/, 'string.invalid'],
                    [/"/, {token: 'string.quote', bracket: '@open', next: '@string'}],
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
    }

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

    function updateMonacoLanguage(langCode) {
        if (typeof monaco === 'undefined') return;
        monaco.languages.setMonarchTokensProvider('seen', buildSeenLangDef(langCode));
        // Force re-tokenization by resetting the model's language
        if (editor) {
            const model = editor.getModel();
            if (model) {
                monaco.editor.setModelLanguage(model, 'plaintext');
                monaco.editor.setModelLanguage(model, 'seen');
            }
        }
    }

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
            monaco.languages.setMonarchTokensProvider('seen', buildSeenLangDef(currentLanguage));
            monaco.editor.defineTheme('seen-dark', SEEN_THEME);

            // Create editor
            const container = document.getElementById('editor-container');
            if (!container) return;

            editor = monaco.editor.create(container, {
                value: getFirstExample(),
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

    function getFirstExample() {
        const examples = EXAMPLES[currentLanguage] || EXAMPLES.en;
        const keys = Object.keys(examples);
        return keys.length > 0 ? examples[keys[0]] : '';
    }

    function buildLanguageSelector() {
        const container = document.getElementById('language-selector');
        if (!container) return;

        container.innerHTML = '';

        const select = document.createElement('select');
        select.id = 'language-select';
        select.className = 'language-select';

        Object.keys(LANGUAGE_NAMES).forEach(function (code) {
            const opt = document.createElement('option');
            opt.value = code;
            opt.textContent = LANGUAGE_NAMES[code];
            if (code === currentLanguage) opt.selected = true;
            select.appendChild(opt);
        });

        select.addEventListener('change', function () {
            currentLanguage = select.value;
            buildExampleSelector();
            updateMonacoLanguage(currentLanguage);
            if (editor) {
                editor.setValue(getFirstExample());
            }
            clearOutput();
        });

        container.appendChild(select);
    }

    function buildExampleSelector() {
        const container = document.getElementById('example-selector');
        if (!container) return;

        container.innerHTML = '';

        const select = document.createElement('select');
        select.id = 'example-select';
        select.className = 'example-select';

        const defaultOpt = document.createElement('option');
        defaultOpt.value = '';
        defaultOpt.textContent = 'Load Example...';
        defaultOpt.disabled = true;
        defaultOpt.selected = true;
        select.appendChild(defaultOpt);

        const examples = EXAMPLES[currentLanguage] || EXAMPLES.en;
        Object.keys(examples).forEach(function (name) {
            const opt = document.createElement('option');
            opt.value = name;
            opt.textContent = name;
            select.appendChild(opt);
        });

        select.addEventListener('change', function () {
            const name = select.value;
            if (name && examples[name] && editor) {
                editor.setValue(examples[name]);
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
                body: JSON.stringify({code: code, language: currentLanguage})
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
        buildLanguageSelector();
        buildExampleSelector();

        // Run button click
        const runBtn = document.getElementById('run-btn');
        if (runBtn) {
            runBtn.addEventListener('click', runCode);
        }
    });
})();
