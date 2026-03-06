package code.yousef.portfolio.seen

import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class SeenRunRequest(val code: String, val language: String = "en")

@Serializable
data class ExecutionResult(
    val output: String,
    val error: String,
    val exitCode: Int,
    val compileTimeMs: Long,
    val runTimeMs: Long
)

/**
 * Executes Seen code in a sandboxed temp directory.
 *
 * The compiler must run from [seenHomePath] because it looks for `seen_runtime/`
 * relative to CWD. Source files and output binaries use absolute paths in the temp dir.
 *
 * @param seenHomePath  Directory containing the `seen` binary and `seen_runtime/`.
 *                      In Docker: `/opt/seen`. Locally: the seenlang repo root.
 */
class SeenExecutionService(
    private val seenHomePath: String = System.getenv("SEEN_HOME") ?: "/mnt/Storage/Projects/Rust/seenlang",
    private val compileTimeoutSec: Long = 10,
    private val runTimeoutSec: Long = 5,
    private val maxOutputBytes: Int = 64_000
) {
    private val log = LoggerFactory.getLogger(SeenExecutionService::class.java)

    private val seenBinary: String
        get() {
            val envBinary = System.getenv("SEEN_BINARY_PATH")
            if (envBinary != null) return envBinary
            // Check common locations relative to SEEN_HOME
            val candidates = listOf(
                "$seenHomePath/compiler_seen/target/seen",
                "$seenHomePath/seen"
            )
            return candidates.firstOrNull { Files.isExecutable(Path.of(it)) }
                ?: candidates.first()
        }

    private val validLanguages = setOf("en", "ar", "es", "ru", "zh", "ja")

    fun execute(code: String, language: String = "en"): ExecutionResult {
        val validatedLang = if (language in validLanguages) language else "en"
        val tempDir = Files.createTempDirectory("seen-playground-")
        try {
            val sourceFile = tempDir.resolve("main.seen")
            Files.writeString(sourceFile, code)

            // `seen run` compiles and executes via JIT (no linking, no -lvulkan)
            val start = System.currentTimeMillis()
            val result = runProcess(
                workDir = Path.of(seenHomePath),
                command = listOf(
                    "timeout", "${compileTimeoutSec}s",
                    seenBinary, "run",
                    sourceFile.toAbsolutePath().toString(),
                    "--verbose",
                    "--language", validatedLang
                )
            )
            val totalMs = System.currentTimeMillis() - start

            val timedOut = result.exitCode == 124
            if (timedOut) {
                return ExecutionResult(
                    output = "",
                    error = "Execution timed out after ${compileTimeoutSec}s",
                    exitCode = 124,
                    compileTimeMs = totalMs,
                    runTimeMs = 0
                )
            }

            if (result.exitCode != 0) {
                // Debug: inspect generated IR and lli version
                val debug = buildString {
                    val lliVer = runProcess(Path.of(seenHomePath), listOf("lli", "--version"))
                    appendLine("lli version: ${lliVer.stdout.lines().firstOrNull()}")
                    // Check if IR file was generated
                    val irFile = Path.of("/tmp/seen_jit_module_0.ll")
                    if (Files.exists(irFile)) {
                        val irContent = Files.readString(irFile)
                        val irLines = irContent.lines()
                        appendLine("IR: ${Files.size(irFile)} bytes, ${irLines.size} lines")
                        val defines = irLines.filter { it.trimStart().startsWith("define ") }
                        val declares = irLines.filter { it.trimStart().startsWith("declare ") }
                        appendLine("define: ${defines.size}, declare: ${declares.size}")
                        defines.take(3).forEach { appendLine("  ${it.take(100)}") }
                        // Show last 30 lines of IR to see if there's a truncation or error
                        appendLine("--- Last 30 lines of IR ---")
                        irLines.takeLast(30).forEach { appendLine("  ${it.take(120)}") }
                    } else {
                        appendLine("IR file NOT found")
                        val tmpFiles = Path.of("/tmp").toFile().listFiles()?.filter { it.name.startsWith("seen") }?.map { "${it.name} (${it.length()})" }
                        appendLine("Seen temp files: $tmpFiles")
                    }
                }
                val errorMsg = listOf(result.stdout, result.stderr, debug).filter { it.isNotBlank() }.joinToString("\n---\n")
                return ExecutionResult(
                    output = "",
                    error = truncate(errorMsg, "Error:\n"),
                    exitCode = result.exitCode,
                    compileTimeMs = totalMs,
                    runTimeMs = 0
                )
            }

            return ExecutionResult(
                output = truncate(result.stdout),
                error = truncate(result.stderr),
                exitCode = 0,
                compileTimeMs = totalMs,
                runTimeMs = 0
            )
        } catch (e: Exception) {
            log.error("Seen execution failed", e)
            return ExecutionResult(
                output = "",
                error = "Internal error: ${e.message}",
                exitCode = -1,
                compileTimeMs = 0,
                runTimeMs = 0
            )
        } finally {
            deleteTempDir(tempDir)
        }
    }

    private data class ProcessResult(val stdout: String, val stderr: String, val exitCode: Int)

    private fun runProcess(workDir: Path, command: List<String>): ProcessResult {
        val process = ProcessBuilder(command)
            .directory(workDir.toFile())
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return ProcessResult(stdout, stderr, exitCode)
    }

    private fun truncate(text: String, prefix: String = ""): String {
        val trimmed = text.take(maxOutputBytes)
        val result = if (text.length > maxOutputBytes) "$trimmed\n... (output truncated)" else trimmed
        return if (prefix.isNotEmpty() && result.isNotEmpty()) "$prefix$result" else result
    }

    private fun deleteTempDir(dir: Path) {
        try {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        } catch (e: Exception) {
            log.warn("Failed to clean up temp dir: {}", dir, e)
        }
    }
}
