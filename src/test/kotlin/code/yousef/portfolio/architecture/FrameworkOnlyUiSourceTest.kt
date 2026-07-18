package code.yousef.portfolio.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class FrameworkOnlyUiSourceTest {
    @Test
    fun `guard detects representative raw markup style and script escape hatches`() {
        val fixtureRoot = Files.createTempDirectory("framework-only-source-guard")
        val fixture = fixtureRoot.resolve("RawUiFixture.kt")
        val tripleQuote = "\"\"\""
        try {
            Files.writeString(
                fixture,
                """
                    fun rawUiFixture() {
                        RawHtml("<main>raw markup</main>")
                        Modifier().style("color: red")
                        Modifier().display("flex")
                        Modifier().display(value = "grid")
                        Modifier().fontWeight("700")
                        Modifier().fontWeight(${tripleQuote}bold${tripleQuote})
                        Modifier().outline(OutlineStyle.None.value)
                        Modifier().border("1px", "solid", "#ffffff")
                        Modifier().border(width = "1px", style = "solid", color = "#ffffff")
                        Modifier().gridTemplateColumns(value = "repeat(2, 1fr)")
                        Modifier().gridTemplateRows(${tripleQuote}auto 1fr${tripleQuote})
                        Modifier().transition(value = "all 150ms ease")
                        Modifier().transition(${tripleQuote}opacity 200ms linear${tripleQuote})
                        js("window.alert('raw script')")
                    }
                """.trimIndent(),
            )

            val detectedRules = inspectKotlinSource(fixtureRoot, fixture).map(Violation::rule).toSet()

            assertTrue("RawHtml bypasses typed Summon components" in detectedRules)
            assertTrue("raw inline HTML bypasses typed components" in detectedRules)
            assertTrue("Modifier.style injects raw CSS" in detectedRules)
            assertTrue("enum-backed Summon modifiers require typed enum values" in detectedRules)
            assertTrue("Summon border shorthands require typed BorderStyle values" in detectedRules)
            assertTrue("Summon grid modifiers require GridTrack values" in detectedRules)
            assertTrue("simple Summon transitions require typed property and timing values" in detectedRules)
            assertTrue("Kotlin js() embeds raw JavaScript" in detectedRules)
        } finally {
            Files.deleteIfExists(fixture)
            Files.deleteIfExists(fixtureRoot)
        }
    }

    @Test
    fun `enum guard ignores unrelated functions without modifier receivers`() {
        val fixtureRoot = Files.createTempDirectory("typed-modifier-false-positive-guard")
        val fixture = fixtureRoot.resolve("UnrelatedFunctionsFixture.kt")
        try {
            Files.writeString(
                fixture,
                """
                    fun display(value: String): String = value

                    fun unrelatedFunctionCall() {
                        display("flex")
                    }
                """.trimIndent(),
            )

            val violations = inspectKotlinSource(fixtureRoot, fixture)

            assertTrue(violations.none { it.rule == "enum-backed Summon modifiers require typed enum values" })
        } finally {
            Files.deleteIfExists(fixture)
            Files.deleteIfExists(fixtureRoot)
        }
    }

    @Test
    fun `application UI is authored through the typed framework`() {
        val projectRoot = findProjectRoot()
        val violations = buildList {
            kotlinSourceRoots.forEach { relativeRoot ->
                val sourceRoot = projectRoot.resolve(relativeRoot)
                if (sourceRoot.isDirectory()) {
                    sourceRoot.filesWithExtension("kt").forEach { source ->
                        addAll(inspectKotlinSource(projectRoot, source))
                    }
                }
            }

            resourceRoots.forEach { relativeRoot ->
                val resourceRoot = projectRoot.resolve(relativeRoot)
                if (resourceRoot.isDirectory()) {
                    resourceRoot.regularFiles().forEach { resource ->
                        val lowerName = resource.name.lowercase()
                        if (authoredWebAssetSuffixes.any(lowerName::endsWith)) {
                            add(
                                Violation(
                                    path = projectRoot.relativize(resource).toString(),
                                    line = null,
                                    rule = "authored JavaScript, CSS, and HTML assets are not allowed",
                                    excerpt = resource.name,
                                )
                            )
                        }

                        if (resource.extension.lowercase() in textResourceExtensions) {
                            addAll(inspectTextResource(projectRoot, resource))
                        }
                    }
                }
            }
        }.sortedWith(compareBy(Violation::path, { it.line ?: 0 }, Violation::rule))

        if (violations.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("Application UI must use Summon/Aether/Sigil/Materia typed APIs only.")
                    appendLine("Move browser behavior or styling into the owning framework instead of adding raw UI code:")
                    violations.forEach { violation ->
                        append(" - ").append(violation.path)
                        violation.line?.let { append(':').append(it) }
                        append(": ").append(violation.rule)
                        if (violation.excerpt.isNotBlank()) {
                            append(" [").append(violation.excerpt).append(']')
                        }
                        appendLine()
                    }
                }
            )
        }
    }

    private fun inspectKotlinSource(projectRoot: Path, source: Path): List<Violation> {
        val original = source.readText()
        val commentFree = sanitizeKotlin(original, stripStrings = false)
        val codeOnly = sanitizeKotlin(original, stripStrings = true)

        return buildList {
            codeRules.forEach { rule ->
                rule.pattern.findAll(codeOnly).forEach { match ->
                    add(violation(projectRoot, source, original, match.range.first, rule.description))
                }
            }
            literalAwareRules.forEach { rule ->
                rule.pattern.findAll(commentFree).forEach { match ->
                    val startsInsideLiteral = codeOnly[match.range.first].isWhitespace()
                    if (!rule.requiresLiteralStart || startsInsideLiteral) {
                        add(violation(projectRoot, source, original, match.range.first, rule.description))
                    }
                }
            }
            enumBackedModifierLiterals.forEach { pattern ->
                pattern.findAll(commentFree).forEach { match ->
                    val startsInsideLiteral = codeOnly[match.range.first].isWhitespace()
                    val method = match.groupValues[1]
                    val value = match.groupValues[2]
                    if (!startsInsideLiteral && isRepresentedByModifierEnum(method, value)) {
                        add(
                            violation(
                                projectRoot,
                                source,
                                original,
                                match.range.first,
                                "enum-backed Summon modifiers require typed enum values",
                            )
                        )
                    }
                }
            }
            enumValueExtractionModifierCall.findAll(commentFree).forEach { match ->
                val startsInsideLiteral = codeOnly[match.range.first].isWhitespace()
                if (!startsInsideLiteral && match.groupValues[1] in enumBackedModifierValues) {
                    add(
                        violation(
                            projectRoot,
                            source,
                            original,
                            match.range.first,
                            "enum-backed Summon modifiers require typed enum values",
                        )
                    )
                }
            }
            borderStyleLiterals.forEach { pattern ->
                pattern.findAll(commentFree).forEach { match ->
                    val startsInsideLiteral = codeOnly[match.range.first].isWhitespace()
                    val style = match.groupValues[1]
                    if (!startsInsideLiteral && style in enumBackedModifierValues.getValue("borderStyle")) {
                        add(
                            violation(
                                projectRoot,
                                source,
                                original,
                                match.range.first,
                                "Summon border shorthands require typed BorderStyle values",
                            )
                        )
                    }
                }
            }
            gridModifierLiterals.forEach { pattern ->
                pattern.findAll(commentFree).forEach { match ->
                    if (!codeOnly[match.range.first].isWhitespace()) {
                        add(
                            violation(
                                projectRoot,
                                source,
                                original,
                                match.range.first,
                                "Summon grid modifiers require GridTrack values",
                            )
                        )
                    }
                }
            }
            typedTransitionLiterals.forEach { pattern ->
                pattern.findAll(commentFree).forEach { match ->
                    val startsInsideLiteral = codeOnly[match.range.first].isWhitespace()
                    val property = match.groupValues[1]
                    val timingFunction = match.groupValues[4]
                    if (
                        !startsInsideLiteral &&
                        property in enumBackedModifierValues.getValue("transitionProperty") &&
                        timingFunction in enumBackedModifierValues.getValue("transitionTimingFunction")
                    ) {
                        add(
                            violation(
                                projectRoot,
                                source,
                                original,
                                match.range.first,
                                "simple Summon transitions require typed property and timing values",
                            )
                        )
                    }
                }
            }
        }
    }

    private fun isRepresentedByModifierEnum(method: String, value: String): Boolean {
        val values = enumBackedModifierValues[method] ?: return false
        return when (method) {
            "textDecoration" -> value.trim().split(Regex("""\s+""")).all(values::contains)
            "backgroundBlendModes" -> value.split(',').map(String::trim).all(values::contains)
            else -> value in values
        }
    }

    private fun inspectTextResource(projectRoot: Path, resource: Path): List<Violation> {
        val content = resource.readText()
        return rawScriptOrStyleTag.findAll(content).map { match ->
            violation(
                projectRoot = projectRoot,
                source = resource,
                original = content,
                index = match.range.first,
                rule = "raw script/style markup is not allowed in application resources",
            )
        }.toList()
    }

    private fun violation(
        projectRoot: Path,
        source: Path,
        original: String,
        index: Int,
        rule: String,
    ): Violation {
        val line = original.lineNumberAt(index)
        val excerpt = original.lineSequence().elementAtOrNull(line - 1)
            ?.trim()
            ?.take(MAX_EXCERPT_LENGTH)
            .orEmpty()
        return Violation(
            path = projectRoot.relativize(source).toString(),
            line = line,
            rule = rule,
            excerpt = excerpt,
        )
    }

    private fun findProjectRoot(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { candidate ->
                candidate.resolve("settings.gradle.kts").isRegularFile() &&
                    candidate.resolve("src/main").isDirectory()
            }
            ?: error("Could not locate the portfolio project root from $workingDirectory")
    }

    private fun Path.filesWithExtension(extension: String): List<Path> =
        regularFiles().filter { it.extension.equals(extension, ignoreCase = true) }

    private fun Path.regularFiles(): List<Path> = Files.walk(this).use { paths ->
        paths.filter { Files.isRegularFile(it) }.sorted().toList()
    }

    /**
     * Replaces comments, and optionally string/character literals, with spaces while preserving
     * offsets and line breaks. Rules therefore ignore examples in comments and cannot be evaded by
     * spreading a call across lines, while diagnostics still point at the original source line.
     */
    private fun sanitizeKotlin(source: String, stripStrings: Boolean): String {
        val result = StringBuilder(source.length)
        var index = 0
        var state = LexerState.CODE
        var blockCommentDepth = 0

        fun appendMasked(character: Char) {
            result.append(if (character == '\n' || character == '\r') character else ' ')
        }

        while (index < source.length) {
            val current = source[index]
            val next = source.getOrNull(index + 1)
            val nextNext = source.getOrNull(index + 2)

            when (state) {
                LexerState.CODE -> when {
                    current == '/' && next == '/' -> {
                        appendMasked(current)
                        appendMasked(next)
                        index += 2
                        state = LexerState.LINE_COMMENT
                    }

                    current == '/' && next == '*' -> {
                        appendMasked(current)
                        appendMasked(next)
                        index += 2
                        blockCommentDepth = 1
                        state = LexerState.BLOCK_COMMENT
                    }

                    current == '"' && next == '"' && nextNext == '"' -> {
                        repeat(3) { offset ->
                            if (stripStrings) appendMasked(source[index + offset])
                            else result.append(source[index + offset])
                        }
                        index += 3
                        state = LexerState.TRIPLE_QUOTED_STRING
                    }

                    current == '"' -> {
                        if (stripStrings) appendMasked(current) else result.append(current)
                        index++
                        state = LexerState.QUOTED_STRING
                    }

                    current == '\'' -> {
                        if (stripStrings) appendMasked(current) else result.append(current)
                        index++
                        state = LexerState.CHARACTER_LITERAL
                    }

                    else -> {
                        result.append(current)
                        index++
                    }
                }

                LexerState.LINE_COMMENT -> {
                    appendMasked(current)
                    index++
                    if (current == '\n' || current == '\r') state = LexerState.CODE
                }

                LexerState.BLOCK_COMMENT -> when {
                    current == '/' && next == '*' -> {
                        appendMasked(current)
                        appendMasked(next)
                        index += 2
                        blockCommentDepth++
                    }

                    current == '*' && next == '/' -> {
                        appendMasked(current)
                        appendMasked(next)
                        index += 2
                        blockCommentDepth--
                        if (blockCommentDepth == 0) state = LexerState.CODE
                    }

                    else -> {
                        appendMasked(current)
                        index++
                    }
                }

                LexerState.QUOTED_STRING,
                LexerState.CHARACTER_LITERAL,
                -> {
                    val terminator = if (state == LexerState.QUOTED_STRING) '"' else '\''
                    if (stripStrings) appendMasked(current) else result.append(current)
                    index++
                    if (current == '\\' && index < source.length) {
                        if (stripStrings) appendMasked(source[index]) else result.append(source[index])
                        index++
                    } else if (current == terminator) {
                        state = LexerState.CODE
                    }
                }

                LexerState.TRIPLE_QUOTED_STRING -> {
                    val closesString = current == '"' && next == '"' && nextNext == '"'
                    if (closesString) {
                        repeat(3) { offset ->
                            if (stripStrings) appendMasked(source[index + offset])
                            else result.append(source[index + offset])
                        }
                        index += 3
                        state = LexerState.CODE
                    } else {
                        if (stripStrings) appendMasked(current) else result.append(current)
                        index++
                    }
                }
            }
        }

        return result.toString()
    }

    private fun String.lineNumberAt(index: Int): Int =
        take(index.coerceIn(0, length)).count { it == '\n' } + 1

    private data class Rule(
        val description: String,
        val pattern: Regex,
        val requiresLiteralStart: Boolean = false,
    )

    private data class Violation(
        val path: String,
        val line: Int?,
        val rule: String,
        val excerpt: String,
    )

    private enum class LexerState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        QUOTED_STRING,
        TRIPLE_QUOTED_STRING,
        CHARACTER_LITERAL,
    }

    private companion object {
        const val MAX_EXCERPT_LENGTH = 180

        val kotlinSourceRoots = listOf(
            "src/main/kotlin",
            "registry-service/src/main/kotlin",
        )
        val resourceRoots = listOf(
            "src/main/resources",
            "registry-service/src/main/resources",
        )

        val authoredWebAssetSuffixes = setOf(
            ".js",
            ".mjs",
            ".cjs",
            ".css",
            ".scss",
            ".sass",
            ".less",
            ".html",
            ".htm",
            ".xhtml",
        )
        val textResourceExtensions = setOf("md", "txt", "yaml", "yml", "xml", "json", "svg")

        val enumBackedModifierLiterals = listOf(
            Regex(
                """\.\s*([A-Za-z][A-Za-z0-9]*)\s*\(\s*(?:[A-Za-z][A-Za-z0-9]*\s*=\s*)?"([^"\\]*)"""
            ),
            Regex(
                "\\.\\s*([A-Za-z][A-Za-z0-9]*)\\s*\\(\\s*" +
                    "(?:[A-Za-z][A-Za-z0-9]*\\s*=\\s*)?\"\"\"([^\"]*)\"\"\""
            ),
        )
        val enumValueExtractionModifierCall = Regex(
            """\.\s*([A-Za-z][A-Za-z0-9]*)\s*\(\s*(?:[A-Za-z][A-Za-z0-9]*\s*=\s*)?""" +
                """[A-Za-z][A-Za-z0-9_.]*\.\s*value\b"""
        )
        val borderStyleLiterals = listOf(
            Regex("""\.\s*border\s*\(\s*[^,]+,\s*"([^"\\]*)"""),
            Regex("""\.\s*border\s*\([^)]*\bstyle\s*=\s*"([^"\\]*)"""),
        )
        val gridModifierLiterals = listOf(
            Regex(
                """\b(?:gridTemplateColumns|gridTemplateRows)\s*\(\s*""" +
                    """(?:[A-Za-z][A-Za-z0-9]*\s*=\s*)?"([^"\\]*)"""
            ),
            Regex(
                "\\b(?:gridTemplateColumns|gridTemplateRows)\\s*\\(\\s*" +
                    "(?:[A-Za-z][A-Za-z0-9]*\\s*=\\s*)?\"\"\"([^\"]*)\"\"\""
            ),
        )
        val typedTransitionLiterals = listOf(
            Regex(
                """\.\s*transition\s*\(\s*(?:[A-Za-z][A-Za-z0-9]*\s*=\s*)?""" +
                    """"([a-z-]+)\s+(\d+(?:\.\d+)?)(ms|s)\s+([a-z-]+)""" +
                    """(?:\s+\d+(?:\.\d+)?(?:ms|s))?"\s*\)"""
            ),
            Regex(
                "\\.\\s*transition\\s*\\(\\s*(?:[A-Za-z][A-Za-z0-9]*\\s*=\\s*)?" +
                    "\"\"\"([a-z-]+)\\s+(\\d+(?:\\.\\d+)?)(ms|s)\\s+([a-z-]+)" +
                    "(?:\\s+\\d+(?:\\.\\d+)?(?:ms|s))?\"\"\"\\s*\\)"
            ),
        )
        val enumBackedModifierValues = mapOf(
            "position" to setOf("static", "relative", "absolute", "fixed", "sticky"),
            "overflow" to setOf("visible", "hidden", "scroll", "auto"),
            "overflowX" to setOf("visible", "hidden", "scroll", "auto"),
            "overflowY" to setOf("visible", "hidden", "scroll", "auto"),
            "display" to setOf(
                "none", "block", "inline", "inline-block", "flex", "grid", "inline-flex", "inline-grid"
            ),
            "visibility" to setOf("visible", "hidden", "collapse"),
            "justifyContent" to setOf(
                "flex-start", "flex-end", "center", "space-between", "space-around", "space-evenly"
            ),
            "justifyItems" to setOf("start", "end", "center", "stretch", "baseline"),
            "justifySelf" to setOf("auto", "start", "end", "center", "stretch", "baseline"),
            "alignItems" to setOf("flex-start", "flex-end", "center", "baseline", "stretch"),
            "alignSelf" to setOf("auto", "flex-start", "flex-end", "center", "baseline", "stretch"),
            "alignContent" to setOf(
                "flex-start", "flex-end", "center", "space-between", "space-around", "stretch"
            ),
            "flexDirection" to setOf("row", "row-reverse", "column", "column-reverse"),
            "flexWrap" to setOf("nowrap", "wrap", "wrap-reverse"),
            "cursor" to setOf(
                "auto", "default", "none", "context-menu", "help", "pointer", "progress", "wait", "cell",
                "crosshair", "text", "vertical-text", "alias", "copy", "move", "no-drop", "not-allowed",
                "grab", "grabbing", "all-scroll", "col-resize", "row-resize", "n-resize", "e-resize",
                "s-resize", "w-resize", "ne-resize", "nw-resize", "se-resize", "sw-resize", "ew-resize",
                "ns-resize", "nesw-resize", "nwse-resize", "zoom-in", "zoom-out",
            ),
            "pointerEvents" to setOf(
                "auto", "none", "visiblePainted", "visibleFill", "visibleStroke", "visible", "painted", "fill",
                "stroke", "all",
            ),
            "backgroundClip" to setOf("border-box", "padding-box", "content-box", "text"),
            "borderStyle" to setOf(
                "none", "hidden", "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset"
            ),
            "fontStyle" to setOf("normal", "italic", "oblique"),
            "fontWeight" to setOf(
                "100", "200", "300", "400", "500", "600", "700", "800", "900", "normal", "bold"
            ),
            "textAlign" to setOf("left", "right", "center", "justify", "start", "end"),
            "textDecoration" to setOf("none", "underline", "line-through", "overline"),
            "whiteSpace" to setOf("normal", "nowrap", "pre", "pre-line", "pre-wrap", "break-spaces"),
            "textTransform" to setOf(
                "none", "capitalize", "uppercase", "lowercase", "full-width", "full-size-kana"
            ),
            "objectFit" to setOf("fill", "contain", "cover", "none", "scale-down"),
            "transitionProperty" to setOf(
                "all", "none", "transform", "opacity", "background", "background-color", "color", "height",
                "width", "margin", "padding", "border", "border-color", "border-radius", "box-shadow",
                "text-shadow", "font-size", "font-weight", "line-height", "letter-spacing", "visibility", "z-index",
            ),
            "transitionTimingFunction" to setOf(
                "ease", "linear", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end",
                "cubic-bezier(0.4, 0, 0.2, 1)",
            ),
            "mixBlendMode" to setOf(
                "normal", "multiply", "screen", "overlay", "darken", "lighten", "color-dodge", "color-burn",
                "hard-light", "soft-light", "difference", "exclusion",
            ),
            "backgroundBlendModes" to setOf(
                "normal", "multiply", "screen", "overlay", "darken", "lighten", "color-dodge", "color-burn",
                "hard-light", "soft-light", "difference", "exclusion",
            ),
            "wordBreak" to setOf("normal", "break-all", "keep-all", "break-word"),
            "overflowWrap" to setOf("normal", "break-word", "anywhere"),
            "textOverflow" to setOf("clip", "ellipsis"),
            "boxSizing" to setOf("content-box", "border-box"),
            "colorScheme" to setOf(
                "normal", "light", "dark", "light dark", "dark light", "only light", "only dark"
            ),
            "listStyle" to setOf(
                "none", "disc", "circle", "square", "decimal", "decimal-leading-zero", "lower-alpha", "upper-alpha",
                "lower-roman", "upper-roman", "disclosure-open", "disclosure-closed",
            ),
            "listStyleType" to setOf(
                "none", "disc", "circle", "square", "decimal", "decimal-leading-zero", "lower-alpha", "upper-alpha",
                "lower-roman", "upper-roman", "disclosure-open", "disclosure-closed",
            ),
            "scrollbarWidth" to setOf("auto", "thin", "none"),
            "scrollBehavior" to setOf("auto", "smooth"),
            "outline" to setOf(
                "auto", "none", "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset"
            ),
        )

        val codeRules = listOf(
            Rule("RawHtml bypasses typed Summon components", Regex("""\bRawHtml\b""")),
            Rule("RichText bypasses typed Summon components", Regex("""\bRichText\b""")),
            Rule("GlobalStyle injects raw CSS", Regex("""\bGlobalStyle\b""")),
            Rule("head.style injects raw CSS", Regex("""\bhead\s*\.\s*style\s*\(""")),
            Rule("Modifier.style injects raw CSS", Regex("""\.\s*style\s*\(""")),
            Rule("Kotlin js() embeds raw JavaScript", Regex("""\bjs\s*\(""")),
            Rule(
                "direct browser DOM imports belong behind a framework API",
                Regex("""\bimport\s+(?:kotlinx\.browser\.(?:document|window)|org\.w3c\.dom(?:\.|\b))"""),
            ),
            Rule(
                "raw HTML DOM mutation bypasses typed components",
                Regex("""(?i)(?:\.\s*(?:innerHTML|outerHTML|insertAdjacentHTML)\b|\bdocument\s*\.\s*(?:write|writeln)\s*\()"""),
            ),
            Rule(
                "raw CSS DOM mutation bypasses typed modifiers",
                Regex("""(?i)\.\s*cssText\b"""),
            ),
        )

        val rawScriptOrStyleTag = Regex("""(?i)<\s*/?\s*(?:script|style)\b""")
        val literalAwareRules = listOf(
            Rule(
                "raw inline HTML bypasses typed components",
                Regex(
                    """(?i)</?(?:!DOCTYPE\s+html|html|head|body|main|nav|section|article|aside|header|footer|div|span|p|h[1-6]|a|img|picture|video|audio|canvas|form|input|textarea|select|option|button|label|ul|ol|li|table|thead|tbody|tr|th|td|details|summary|dialog|iframe|link|meta)(?=\s|/?>)"""
                ),
                requiresLiteralStart = true,
            ),
            Rule(
                "raw inline script/style markup is not allowed",
                rawScriptOrStyleTag,
                requiresLiteralStart = true,
            ),
            Rule(
                "script/style elements must be declared through typed head APIs",
                Regex("""(?i)\bcreateElement\s*\(\s*"(?:script|style)"\s*\)"""),
            ),
            Rule(
                "raw style attributes bypass typed modifiers",
                Regex("""(?i)\.\s*setAttribute\s*\(\s*"style"\s*,"""),
            ),
            Rule(
                "inline event-handler attributes embed raw JavaScript",
                Regex("""(?i)\.\s*setAttribute\s*\(\s*"on[a-z]+"\s*,"""),
            ),
        )

    }
}
