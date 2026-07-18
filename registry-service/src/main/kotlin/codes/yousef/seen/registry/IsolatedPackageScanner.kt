package codes.yousef.seen.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToJsonElement
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileDescriptor
import java.io.FilePermission
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketPermission
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.Permission
import java.time.Clock
import java.time.Duration
import java.util.PropertyPermission
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

private const val SCANNER_CHILD_MAIN = "codes.yousef.seen.registry.ScannerSubprocessMain"
private const val SCANNER_MAX_HEAP_BYTES = 384L * 1024 * 1024
private const val SCANNER_ACTIVE_PROCESSORS = 1
private val SCANNER_HARD_TIMEOUT: Duration = Duration.ofSeconds(30)
private val SCANNER_RULE_TIMEOUT: Duration = Duration.ofSeconds(20)

/**
 * Credentialed review workers use this engine only after fetching and validating
 * the release. Package-controlled bytes cross into a fresh, credential-free JVM
 * through [ScannerSubprocessProtocol] and no controller object is shared with it.
 */
internal class IsolatedPackageScanEngine(
    private val ruleTimeout: Duration = SCANNER_RULE_TIMEOUT,
    private val hardTimeout: Duration = SCANNER_HARD_TIMEOUT,
    private val maximumHeapBytes: Long = SCANNER_MAX_HEAP_BYTES,
    childMainClass: String = SCANNER_CHILD_MAIN,
    javaExecutable: String = Path.of(System.getProperty("java.home"), "bin", "java").toString(),
    classPath: String = System.getProperty("java.class.path"),
) : PackageScanEngine {
    private val child = ScannerChildProcess(
        javaExecutable = javaExecutable,
        classPath = classPath,
        maximumHeapBytes = maximumHeapBytes,
        activeProcessors = SCANNER_ACTIVE_PROCESSORS,
        hardTimeout = hardTimeout,
    )
    private val childMainClass = childMainClass

    init {
        require(!ruleTimeout.isZero && !ruleTimeout.isNegative)
        require(ruleTimeout <= hardTimeout) { "Scanner rule timeout must fit within the process timeout" }
    }

    override fun scan(input: PackageScanInput): ScanAttestationRecord {
        val output = child.execute(
            mainClass = childMainClass,
            arguments = listOf(ruleTimeout.toMillis().toString(), hardTimeout.toMillis().toString()),
        ) { ScannerSubprocessProtocol.writeInput(input, it) }
        val attestation = ScannerSubprocessProtocol.readAttestation(output)
        check(attestation.scanner.isolated)
        check(attestation.scanner.networkAccess == "none")
        check(attestation.scanner.secretAccess == "none")
        check(attestation.scanner.inputAccess == "read-only")
        check(attestation.sandbox.rootless && attestation.sandbox.ephemeral)
        check(attestation.sandbox.networkAccess == "none")
        check(attestation.sandbox.processLimit == 1)
        check(attestation.sandbox.cpuLimitMillis == hardTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        check(attestation.sandbox.memoryLimitBytes in 1..maximumHeapBytes)
        return attestation
    }
}

@Serializable
private data class ScannerRequestHeader(
    val phase: String,
    @SerialName("attestation_id") val attestationId: String,
    val sequence: Long,
    @SerialName("package") val packageIdentity: String,
    val version: String,
    @SerialName("archive_sha256") val archiveSha256: String,
    @SerialName("source_proof") val sourceProof: SourceProofRecord,
    @SerialName("source_proof_sha256") val sourceProofSha256: String,
    @SerialName("previous_attestation_id") val previousAttestationId: String? = null,
    val attempt: Int,
) {
    fun toInput(files: List<SourceArchiveFile>): PackageScanInput = PackageScanInput(
        phase = ReviewPhase.valueOf(phase.uppercase()),
        attestationId = attestationId,
        sequence = sequence,
        packageIdentity = packageIdentity,
        version = version,
        archiveSha256 = archiveSha256,
        sourceProof = sourceProof,
        sourceProofSha256 = sourceProofSha256,
        files = files,
        previousAttestationId = previousAttestationId,
        attempt = attempt,
    )

    companion object {
        fun from(input: PackageScanInput): ScannerRequestHeader = ScannerRequestHeader(
            phase = input.phase.name.lowercase(),
            attestationId = input.attestationId,
            sequence = input.sequence,
            packageIdentity = input.packageIdentity,
            version = input.version,
            archiveSha256 = input.archiveSha256,
            sourceProof = input.sourceProof,
            sourceProofSha256 = input.sourceProofSha256,
            previousAttestationId = input.previousAttestationId,
            attempt = input.attempt,
        )
    }
}

/** Deterministic, length-delimited scanner stdin and canonical attestation stdout. */
internal object ScannerSubprocessProtocol {
    private val magic = "SEEN-SCAN-INPUT-1\n".encodeToByteArray()
    private const val MAX_HEADER_BYTES = 1024 * 1024
    private const val MAX_ATTESTATION_BYTES = 2 * 1024 * 1024

    fun writeInput(input: PackageScanInput, output: OutputStream) {
        val header = canonicalJson(RegistryJson.encodeToJsonElement(ScannerRequestHeader.from(input)))
        require(header.size in 1..MAX_HEADER_BYTES) { "Scanner input header is too large" }
        require(input.files.size in 1..ArchivePolicy.MAX_ENTRIES) { "Scanner file count is outside archive policy" }
        val files = input.files.sortedBy(SourceArchiveFile::path)
        require(files.map(SourceArchiveFile::path).toSet().size == files.size) { "Scanner input has duplicate paths" }

        DataOutputStream(BufferedOutputStream(output, 64 * 1024)).use { data ->
            data.write(magic)
            data.writeInt(header.size)
            data.write(header)
            data.writeInt(files.size)
            var expandedBytes = 0L
            files.forEach { file ->
                val path = file.path.toByteArray(StandardCharsets.UTF_8)
                require(path.isNotEmpty() && path.size <= ArchivePolicy.MAX_PATH_BYTES) { "Scanner path is outside archive policy" }
                require(path.toString(StandardCharsets.UTF_8) == file.path) { "Scanner path is not canonical UTF-8" }
                require(file.bytes.size.toLong() <= ArchivePolicy.MAX_REGULAR_FILE_BYTES) { "Scanner file is outside archive policy" }
                expandedBytes += file.bytes.size
                require(expandedBytes <= ArchivePolicy.MAX_EXPANDED_BYTES) { "Scanner input is outside archive policy" }
                data.writeInt(path.size)
                data.write(path)
                data.writeLong(file.bytes.size.toLong())
                data.write(file.bytes)
            }
        }
    }

    fun readInput(input: InputStream): PackageScanInput {
        DataInputStream(BufferedInputStream(input, 64 * 1024)).use { data ->
            check(data.readExact(magic.size).contentEquals(magic)) { "Scanner input magic is invalid" }
            val headerLength = data.readInt()
            check(headerLength in 1..MAX_HEADER_BYTES) { "Scanner input header is too large" }
            val headerBytes = data.readExact(headerLength)
            val header = RegistryJson.decodeFromString<ScannerRequestHeader>(headerBytes.toString(StandardCharsets.UTF_8))
            check(canonicalJson(RegistryJson.encodeToJsonElement(header)).contentEquals(headerBytes)) {
                "Scanner input header is not canonical"
            }
            check(header.phase in setOf("first", "second")) { "Scanner phase is invalid" }
            check(header.attempt >= 1) { "Scanner attempt is invalid" }

            val count = data.readInt()
            check(count in 1..ArchivePolicy.MAX_ENTRIES) { "Scanner file count is outside archive policy" }
            val files = ArrayList<SourceArchiveFile>(count)
            var previousPath: String? = null
            var expandedBytes = 0L
            repeat(count) {
                val pathLength = data.readInt()
                check(pathLength in 1..ArchivePolicy.MAX_PATH_BYTES) { "Scanner path is outside archive policy" }
                val pathBytes = data.readExact(pathLength)
                val path = pathBytes.toString(StandardCharsets.UTF_8)
                check(path.toByteArray(StandardCharsets.UTF_8).contentEquals(pathBytes)) { "Scanner path is not strict UTF-8" }
                check(previousPath == null || requireNotNull(previousPath) < path) { "Scanner paths are not canonical" }
                previousPath = path

                val fileLength = data.readLong()
                check(fileLength in 0..ArchivePolicy.MAX_REGULAR_FILE_BYTES) { "Scanner file is outside archive policy" }
                expandedBytes += fileLength
                check(expandedBytes <= ArchivePolicy.MAX_EXPANDED_BYTES) { "Scanner input is outside archive policy" }
                files += SourceArchiveFile(path, data.readExact(fileLength.toInt()))
            }
            check(data.read() == -1) { "Scanner input has trailing bytes" }
            return header.toInput(files)
        }
    }

    fun writeAttestation(attestation: ScanAttestationRecord, output: OutputStream) {
        val bytes = canonicalJson(RegistryJson.encodeToJsonElement(attestation))
        check(bytes.size <= MAX_ATTESTATION_BYTES) { "Scanner attestation is too large" }
        output.write(bytes)
        output.flush()
    }

    fun readAttestation(bytes: ByteArray): ScanAttestationRecord {
        check(bytes.isNotEmpty() && bytes.size <= MAX_ATTESTATION_BYTES) { "Scanner attestation is missing or too large" }
        val attestation = RegistryJson.decodeFromString<ScanAttestationRecord>(bytes.toString(StandardCharsets.UTF_8))
        check(canonicalJson(RegistryJson.encodeToJsonElement(attestation)).contentEquals(bytes)) {
            "Scanner attestation is not canonical"
        }
        return attestation
    }

    private fun DataInputStream.readExact(length: Int): ByteArray = ByteArray(length).also { target ->
        readFully(target)
    }
}

/** Entry point used only by [IsolatedPackageScanEngine]. */
internal object ScannerSubprocessMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Scanner subprocess requires its rule and hard timeouts" }
        val timeout = Duration.ofMillis(args[0].toLong())
        val hardTimeout = Duration.ofMillis(args[1].toLong())
        require(!timeout.isZero && !timeout.isNegative && timeout <= hardTimeout)
        require(hardTimeout <= Duration.ofHours(1))
        ScannerSandboxPolicy.install()
        val input = ScannerSubprocessProtocol.readInput(System.`in`)
        val attestation = PackageScanner(
            clock = Clock.systemUTC(),
            timeout = timeout,
            executionProfile = PackageScannerExecutionProfile.isolatedSubprocess(hardTimeout),
        ).scan(input)
        ScannerSubprocessProtocol.writeAttestation(attestation, System.out)
    }
}

/**
 * Java 21 still supports a per-process SecurityManager. The scanner process is
 * deliberately a small trusted static analyzer: once this policy is installed,
 * it cannot observe controller environment variables, open sockets, execute a
 * process, mutate the filesystem, or read outside its JRE/classpath.
 */
@Suppress("DEPRECATION", "removal")
internal object ScannerSandboxPolicy {
    fun install() {
        check(System.getSecurityManager() == null) { "Scanner sandbox is already configured" }
        val user = ProcessHandle.current().info().user().orElse("")
        check(user.isNotBlank() && user != "root") { "Scanner subprocess must run rootless" }
        val policy = DenyScannerCapabilitiesSecurityManager(readableLocations())
        System.setSecurityManager(policy)
        check(System.getSecurityManager() === policy) { "Scanner sandbox was not installed" }
    }

    private fun readableLocations(): ReadableLocations {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        val exact = linkedSetOf<Path>()
        val roots = linkedSetOf<Path>()
        System.getProperty("java.class.path").split(System.getProperty("path.separator")).forEach { entry ->
            check(entry.isNotBlank()) { "Scanner classpath cannot contain the working directory" }
            val normalized = Path.of(entry).toAbsolutePath().normalize()
            check(normalized != workingDirectory) { "Scanner classpath cannot expose the working directory" }
            if (Files.isDirectory(normalized)) roots.add(normalized) else exact.add(normalized)
            runCatching { normalized.toRealPath() }.getOrNull()?.let { real ->
                if (Files.isDirectory(real)) roots.add(real) else exact.add(real)
            }
        }
        val javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()
        roots.add(javaHome)
        runCatching { javaHome.toRealPath() }.getOrNull()?.let(roots::add)
        listOf("/dev/random", "/dev/urandom").map(Path::of).mapTo(exact) { it.toAbsolutePath().normalize() }
        return ReadableLocations(roots, exact)
    }

    private data class ReadableLocations(val roots: Set<Path>, val exact: Set<Path>)

    private class DenyScannerCapabilitiesSecurityManager(
        private val readable: ReadableLocations,
    ) : SecurityManager() {
        override fun checkPermission(permission: Permission) {
            when (permission) {
                is SocketPermission -> denied("network access")
                is FilePermission -> checkFilePermission(permission)
                is PropertyPermission -> if ("write" in permission.actions.split(',')) denied("system property mutation")
                is RuntimePermission -> when {
                    permission.name == "getenv.*" || permission.name.startsWith("getenv.") -> denied("environment access")
                    permission.name == "setSecurityManager" -> denied("sandbox mutation")
                    permission.name == "setIO" -> denied("process I/O mutation")
                    permission.name == "createClassLoader" -> denied("classloader creation")
                    permission.name in setOf("modifyThread", "modifyThreadGroup") -> denied("thread modification")
                    permission.name == "manageProcess" -> denied("process access")
                    permission.name.startsWith("loadLibrary.") -> denied("native library loading")
                }
            }
        }

        override fun checkRead(file: String) {
            if (!isReadable(file)) denied("filesystem read")
        }

        override fun checkRead(fileDescriptor: FileDescriptor) = Unit

        override fun checkWrite(file: String) = denied("filesystem write")

        override fun checkWrite(fileDescriptor: FileDescriptor) {
            if (fileDescriptor !== FileDescriptor.out && fileDescriptor !== FileDescriptor.err) denied("filesystem write")
        }

        override fun checkDelete(file: String) = denied("filesystem delete")

        override fun checkExec(command: String) = denied("process execution")

        override fun checkLink(library: String) = denied("native library loading")

        override fun checkConnect(host: String, port: Int) = denied("network access")

        override fun checkConnect(host: String, port: Int, context: Any?) = denied("network access")

        override fun checkListen(port: Int) = denied("network access")

        override fun checkAccept(host: String, port: Int) = denied("network access")

        private fun checkFilePermission(permission: FilePermission) {
            val actions = permission.actions.split(',').map(String::trim)
            if (actions.any { it in setOf("write", "delete", "execute") }) denied("filesystem mutation")
            if ("read" in actions && !isReadable(permission.name)) denied("filesystem read")
        }

        private fun isReadable(file: String): Boolean {
            if (file == "<<ALL FILES>>") return false
            val path = runCatching { Path.of(file).toAbsolutePath().normalize() }.getOrNull() ?: return false
            return path in readable.exact || readable.roots.any(path::startsWith)
        }

        private fun denied(capability: String): Nothing = throw SecurityException("Scanner sandbox denied $capability")
    }
}

internal class ScannerChildProcess(
    private val javaExecutable: String = Path.of(System.getProperty("java.home"), "bin", "java").toString(),
    private val classPath: String = System.getProperty("java.class.path"),
    val maximumHeapBytes: Long = SCANNER_MAX_HEAP_BYTES,
    private val activeProcessors: Int = SCANNER_ACTIVE_PROCESSORS,
    private val hardTimeout: Duration = SCANNER_HARD_TIMEOUT,
) {
    init {
        require(maximumHeapBytes >= 64L * 1024 * 1024)
        require(activeProcessors in 1..4)
        require(!hardTimeout.isZero && !hardTimeout.isNegative && hardTimeout <= Duration.ofHours(1))
        require(classPath.isNotBlank())
    }

    fun execute(
        mainClass: String,
        arguments: List<String> = emptyList(),
        inputWriter: (OutputStream) -> Unit = {},
    ): ByteArray {
        val command = listOf(
            javaExecutable,
            "-Xms16m",
            "-Xmx$maximumHeapBytes",
            "-XX:ActiveProcessorCount=$activeProcessors",
            "-XX:+UseSerialGC",
            "-XX:-TieredCompilation",
            "-XX:CICompilerCount=1",
            "-XX:+PerfDisableSharedMem",
            "-XX:+DisableAttachMechanism",
            "-XX:ErrorFile=/dev/stderr",
            "-Djava.awt.headless=true",
            "-Djava.security.manager=allow",
            "-cp",
            classPath,
            mainClass,
        ) + arguments
        val builder = ProcessBuilder(command)
        builder.environment().clear()
        val process = builder.start()
        val threadNumber = AtomicInteger()
        val io = Executors.newFixedThreadPool(3) { task ->
            Thread(task, "seen-scanner-subprocess-io-${threadNumber.incrementAndGet()}").apply { isDaemon = true }
        }
        val stdout = io.submit(Callable { process.inputStream.use { it.readBounded(2 * 1024 * 1024) } })
        val stderr = io.submit(Callable { process.errorStream.use { it.readBounded(64 * 1024) } })
        val stdin = io.submit(Callable {
            process.outputStream.use(inputWriter)
            Unit
        })
        val deadline = System.nanoTime() + hardTimeout.toNanos()
        try {
            if (!process.waitFor(remainingNanos(deadline), TimeUnit.NANOSECONDS)) {
                throw PackageScanTimeoutException()
            }
            val output = stdout.await(deadline)
            stderr.await(deadline)
            stdin.await(deadline)
            if (process.exitValue() != 0) throw PackageScanProcessException()
            return output
        } catch (timeout: TimeoutException) {
            throw PackageScanTimeoutException()
        } catch (failure: ExecutionException) {
            throw PackageScanProcessException(failure.cause)
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                runCatching { process.waitFor(5, TimeUnit.SECONDS) }
            }
            io.shutdownNow()
        }
    }

    private fun <T> Future<T>.await(deadline: Long): T = get(remainingNanos(deadline), TimeUnit.NANOSECONDS)

    private fun remainingNanos(deadline: Long): Long = (deadline - System.nanoTime()).coerceAtLeast(1)

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            check(total <= limit) { "Scanner subprocess output exceeded its limit" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

internal class PackageScanProcessException(cause: Throwable? = null) : RuntimeException("Scanner subprocess failed", cause)
