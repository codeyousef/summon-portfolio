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
        try {
            Files.writeString(
                fixture,
                """
                    fun rawUiFixture() {
                        RawHtml("<main>raw markup</main>")
                        Modifier().style("color: red")
                        js("window.alert('raw script')")
                    }
                """.trimIndent(),
            )

            val detectedRules = inspectKotlinSource(fixtureRoot, fixture).map(Violation::rule).toSet()

            assertTrue("RawHtml bypasses typed Summon components" in detectedRules)
            assertTrue("raw inline HTML bypasses typed components" in detectedRules)
            assertTrue("Modifier.style injects raw CSS" in detectedRules)
            assertTrue("Kotlin js() embeds raw JavaScript" in detectedRules)
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
