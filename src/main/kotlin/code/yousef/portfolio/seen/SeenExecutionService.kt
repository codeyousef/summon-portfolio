package code.yousef.portfolio.seen

import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class SeenRunRequest(val code: String)

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

    fun execute(code: String): ExecutionResult {
        val tempDir = Files.createTempDirectory("seen-playground-")
        try {
            val sourceFile = tempDir.resolve("main.seen")
            Files.writeString(sourceFile, code)

            val outputBinary = tempDir.resolve("main")

            // Compile with --no-cache (each playground run is a fresh program)
            val compileStart = System.currentTimeMillis()
            val compileResult = runProcess(
                workDir = Path.of(seenHomePath),
                command = listOf(
                    "timeout", "${compileTimeoutSec}s",
                    seenBinary, "compile", "--no-cache",
                    sourceFile.toAbsolutePath().toString(),
                    outputBinary.toAbsolutePath().toString()
                )
            )
            val compileTimeMs = System.currentTimeMillis() - compileStart

            if (compileResult.exitCode != 0) {
                val errorMsg = compileResult.stderr.ifBlank { compileResult.stdout }
                return ExecutionResult(
                    output = "",
                    error = truncate(errorMsg, "Compilation error:\n"),
                    exitCode = compileResult.exitCode,
                    compileTimeMs = compileTimeMs,
                    runTimeMs = 0
                )
            }

            if (!Files.exists(outputBinary)) {
                return ExecutionResult(
                    output = "",
                    error = "Compilation produced no output binary.",
                    exitCode = 1,
                    compileTimeMs = compileTimeMs,
                    runTimeMs = 0
                )
            }

            // Run the compiled binary
            val runStart = System.currentTimeMillis()
            val runResult = runProcess(
                workDir = tempDir,
                command = listOf(
                    "timeout", "${runTimeoutSec}s",
                    outputBinary.toAbsolutePath().toString()
                )
            )
            val runTimeMs = System.currentTimeMillis() - runStart

            val timedOut = runResult.exitCode == 124
            val errorOutput = if (timedOut) {
                "Execution timed out after ${runTimeoutSec}s"
            } else {
                truncate(runResult.stderr)
            }

            return ExecutionResult(
                output = truncate(runResult.stdout),
                error = errorOutput,
                exitCode = runResult.exitCode,
                compileTimeMs = compileTimeMs,
                runTimeMs = runTimeMs
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
