package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarUtils
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.tomlj.Toml
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Duration
import java.util.Comparator
import java.util.Locale
import kotlin.math.max

object ArchivePolicy {
    const val MAX_COMPRESSED_BYTES = 25L * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 100L * 1024 * 1024
    const val MAX_ENTRIES = 4096
    const val MAX_REGULAR_FILE_BYTES = 10L * 1024 * 1024
    const val MAX_PATH_BYTES = 240
    const val MAX_PATH_DEPTH = 16
    const val MAX_COMPRESSION_RATIO = 100L
    val MAX_VALIDATION_TIME: Duration = Duration.ofSeconds(30)
}

/** A source whose compressed bytes can be opened on demand without buffering them in memory. */
fun interface ReopenableArchiveSource {
    fun openStream(): InputStream
}

data class ArchiveValidation(
    val archiveSha256: String,
    val manifestSha256: String,
    val manifestBytes: ByteArray,
    val expandedBytes: Long,
    val entryCount: Int,
    val largestRegularFileBytes: Long,
    val longestPathBytes: Int,
    val maximumPathDepth: Int,
    /** Independently re-read regular-file evidence ready for [SourceVerificationInput]. */
    val archiveFiles: List<SourceArchiveFile>,
    val declaredPackagePaths: Set<String>,
)

class ArchiveValidator(
    private val nanoTime: () -> Long = System::nanoTime,
    private val createQuarantineDirectory: () -> Path = {
        Files.createTempDirectory("seen-registry-archive-")
    },
) {
    /**
     * Compatibility entry point for existing bounded in-memory callers and tests.
     * Production upload paths should use [ReopenableArchiveSource].
     */
    fun validate(
        bytes: ByteArray,
        expectedArchiveSha256: String,
        expectedManifestSha256: String,
        expectedIdentity: String,
        expectedVersion: String,
        reservedManifest: JsonObject,
    ): ArchiveValidation {
        if (bytes.isEmpty()) reject("archive_empty")
        if (bytes.size.toLong() > ArchivePolicy.MAX_COMPRESSED_BYTES) archiveTooLarge()
        return validate(
            source = ReopenableArchiveSource { ByteArrayInputStream(bytes) },
            expectedArchiveSha256 = expectedArchiveSha256,
            expectedManifestSha256 = expectedManifestSha256,
            expectedIdentity = expectedIdentity,
            expectedVersion = expectedVersion,
            reservedManifest = reservedManifest,
        )
    }

    fun validate(
        source: ReopenableArchiveSource,
        expectedArchiveSha256: String,
        expectedManifestSha256: String,
        expectedIdentity: String,
        expectedVersion: String,
        reservedManifest: JsonObject,
    ): ArchiveValidation {
        val started = nanoTime()
        val quarantine = createQuarantine()
        var primaryFailure: Throwable? = null
        try {
            val compressedArchive = quarantine.resolve(COMPRESSED_ARCHIVE_NAME)
            val staged = stageCompressedArchive(source, compressedArchive, started)
            if (staged.sha256 != expectedArchiveSha256) {
                throw RegistryException(422, "digest_mismatch", "Archive digest does not match the reservation")
            }

            validateRawTarStream(compressedArchive, staged.bytes, started)
            val extractedRoot = quarantine.resolve(EXTRACTED_DIRECTORY_NAME)
            Files.createDirectory(extractedRoot)
            val extraction = extractArchive(compressedArchive, extractedRoot, started)
            if (extraction.expandedBytes > staged.bytes * ArchivePolicy.MAX_COMPRESSION_RATIO) {
                reject("archive_compression_ratio_limit")
            }

            val archiveFiles = rehashExtractedFiles(extraction.files, started)
            val declaredPaths = archiveFiles.mapTo(linkedSetOf()) { it.path }
            val manifest = archiveFiles.singleOrNull { it.path == ROOT_MANIFEST }?.bytes
                ?: reject("archive_manifest_missing")
            val manifestDigest = sha256(manifest)
            if (manifestDigest != expectedManifestSha256) reject("archive_manifest_mismatch")
            val memberPatterns = validateManifest(
                manifest,
                expectedIdentity,
                expectedVersion,
                reservedManifest,
                started,
            )
            checkDeadline(started)
            if (extraction.memberPaths.any { path ->
                    checkDeadline(started)
                    memberPatterns.none { pattern -> globMatch(pattern, path, started) }
                }
            ) {
                reject("archive_include_policy_violation")
            }

            return ArchiveValidation(
                archiveSha256 = staged.sha256,
                manifestSha256 = manifestDigest,
                manifestBytes = manifest,
                expandedBytes = extraction.expandedBytes,
                entryCount = extraction.entryCount,
                largestRegularFileBytes = extraction.largestRegularFileBytes,
                longestPathBytes = extraction.longestPathBytes,
                maximumPathDepth = extraction.maximumPathDepth,
                archiveFiles = archiveFiles,
                declaredPackagePaths = declaredPaths,
            )
        } catch (error: Throwable) {
            primaryFailure = error
            when (error) {
                is RegistryException -> throw error
                is Exception -> reject("archive_parse_error")
                else -> throw error
            }
        } finally {
            try {
                deleteQuarantine(quarantine)
            } catch (cleanupFailure: Throwable) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure)
                } else {
                    throw RegistryException(500, "archive_cleanup_failed", "Archive quarantine cleanup failed")
                }
            }
        }
    }

    private fun createQuarantine(): Path {
        val directory = createQuarantineDirectory().toAbsolutePath().normalize()
        var verifiedEmpty = false
        try {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw RegistryException(500, "archive_quarantine_failed", "Archive quarantine could not be created")
            }
            Files.newDirectoryStream(directory).use { entries ->
                if (entries.iterator().hasNext()) {
                    throw RegistryException(500, "archive_quarantine_failed", "Archive quarantine was not empty")
                }
            }
            verifiedEmpty = true
            Files.getFileAttributeView(
                directory,
                PosixFileAttributeView::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )?.let {
                Files.setPosixFilePermissions(
                    directory,
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    ),
                )
            }
            return directory
        } catch (error: Throwable) {
            if (verifiedEmpty) runCatching { deleteQuarantine(directory) }
            throw error
        }
    }

    private fun stageCompressedArchive(
        source: ReopenableArchiveSource,
        destination: Path,
        started: Long,
    ): StagedArchive {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        source.openStream().use { input ->
            Files.newOutputStream(
                destination,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS,
            ).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    checkDeadline(started)
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > ArchivePolicy.MAX_COMPRESSED_BYTES) archiveTooLarge()
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        checkDeadline(started)
        if (total == 0L) reject("archive_empty")
        return StagedArchive(total, digest.hexDigest())
    }

    /**
     * Strictly validates raw tar blocks while gzip output is streamed. This pass catches
     * malformed checksums/headers and forbidden type flags before Commons Compress can
     * normalize them into higher-level entries.
     */
    private fun validateRawTarStream(compressedArchive: Path, compressedBytes: Long, started: Long) {
        Files.newInputStream(compressedArchive).use { compressed ->
            val gzip = try {
                // Concatenation is enabled only so every trailing compressed byte is
                // consumed and surfaced. A second member produces non-zero data after
                // the tar terminator and is rejected below; malformed trailing bytes
                // fail gzip parsing instead of disappearing in an inflater read-ahead.
                GzipCompressorInputStream(compressed, true)
            } catch (_: Exception) {
                reject("archive_parse_error")
            }
            gzip.use {
                val blocks = TarBlockReader(gzip, started)
                var pendingPax = false
                var sawEntry = false
                while (true) {
                    val header = blocks.readBlock() ?: reject("archive_parse_error")
                    if (header.isZeroBlock()) {
                        if (pendingPax) reject("archive_parse_error")
                        val secondEnd = blocks.readBlock() ?: reject("archive_parse_error")
                        if (!secondEnd.isZeroBlock()) reject("archive_parse_error")
                        while (true) {
                            val trailing = blocks.readBlock() ?: break
                            if (!trailing.isZeroBlock()) reject("archive_parse_error")
                        }
                        if (!sawEntry) reject("archive_parse_error")
                        break
                    }

                    if (!TarUtils.verifyCheckSum(header)) reject("archive_parse_error")
                    val mode = validateHeaderEncoding(header)
                    val size = parseTarNumber(header, SIZE_OFFSET, SIZE_LENGTH)
                    if (size < 0 || size > MAX_RAW_TAR_BYTES) reject("archive_parse_error")
                    val type = header[TYPE_OFFSET].toInt() and 0xff
                    if (type !in ALLOWED_RAW_TYPES) reject("archive_entry_type_forbidden")
                    if (type == DIRECTORY_TYPE && size != 0L) reject("archive_entry_type_forbidden")
                    if (type in setOf(0, REGULAR_TYPE) && size > ArchivePolicy.MAX_REGULAR_FILE_BYTES) {
                        reject("archive_file_size_limit")
                    }
                    if (type in setOf(0, REGULAR_TYPE) && mode and EXECUTABLE_MODE_MASK.toLong() != 0L) {
                        reject("archive_executable_mode_forbidden")
                    }
                    if (type != PAX_TYPE) validateRawHeaderPath(header)

                    val payload = if (type == PAX_TYPE) {
                        if (pendingPax || size > MAX_PAX_BYTES) reject("archive_entry_type_forbidden")
                        blocks.readPayload(size, capture = true)
                    } else {
                        blocks.readPayload(size, capture = false)
                    }
                    if (type == PAX_TYPE) {
                        if (parsePaxKeys(payload!!) != setOf("path")) reject("archive_entry_type_forbidden")
                        pendingPax = true
                    } else {
                        pendingPax = false
                        sawEntry = true
                    }
                }
                checkDeadline(started)
                if (gzip.compressedCount != compressedBytes) reject("archive_parse_error")
            }
        }
    }

    private fun validateHeaderEncoding(header: ByteArray): Long {
        val mode = parseTarNumber(header, MODE_OFFSET, MODE_LENGTH)
        // Some writers retain the POSIX file-type bits in this field. Keep the
        // value representable by TarArchiveEntry while enforcing permission bits
        // independently for regular files below.
        if (mode !in 0L..Int.MAX_VALUE.toLong()) reject("archive_parse_error")
        if (parseTarNumber(header, UID_OFFSET, UID_LENGTH) < 0L) reject("archive_parse_error")
        if (parseTarNumber(header, GID_OFFSET, GID_LENGTH) < 0L) reject("archive_parse_error")
        parseTarNumber(header, MTIME_OFFSET, MTIME_LENGTH)

        val magic = header.copyOfRange(MAGIC_OFFSET, MAGIC_OFFSET + MAGIC_LENGTH)
        if (!magic.all { it == 0.toByte() }) {
            val validMagic = magic.contentEquals(byteArrayOf('u'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), 0)) ||
                magic.contentEquals("ustar ".encodeToByteArray())
            if (!validMagic) reject("archive_parse_error")
            val version = header.copyOfRange(VERSION_OFFSET, VERSION_OFFSET + VERSION_LENGTH)
            if (!version.contentEquals("00".encodeToByteArray()) &&
                !version.contentEquals(byteArrayOf(' '.code.toByte(), 0))
            ) {
                reject("archive_parse_error")
            }
        }
        return mode
    }

    private fun parseTarNumber(header: ByteArray, offset: Int, length: Int): Long {
        val field = header.copyOfRange(offset, offset + length)
        if (field.first().toInt() and 0x80 == 0) {
            val significant = field.dropWhile { it == 0.toByte() || it == ' '.code.toByte() }
                .dropLastWhile { it == 0.toByte() || it == ' '.code.toByte() }
            if (significant.any { it !in '0'.code.toByte()..'7'.code.toByte() }) reject("archive_parse_error")
        }
        return runCatching { TarUtils.parseOctalOrBinary(header, offset, length) }
            .getOrElse { reject("archive_parse_error") }
    }

    private fun extractArchive(compressedArchive: Path, extractedRoot: Path, started: Long): ExtractionResult {
        var expanded = 0L
        var entries = 0
        var largest = 0L
        var longestPath = 0
        var maximumDepth = 0
        val memberPaths = mutableListOf<String>()
        val extractedFiles = mutableListOf<ExtractedFile>()
        val exactPaths = HashSet<String>()
        val portablePaths = HashSet<String>()
        val normalizedPaths = HashSet<String>()
        val pathKinds = HashMap<String, Boolean>()

        Files.newInputStream(compressedArchive).use { compressed ->
            GzipCompressorInputStream(compressed, false).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        checkDeadline(started)
                        val entry = tar.nextEntry ?: break
                        entries++
                        if (entries > ArchivePolicy.MAX_ENTRIES) reject("archive_entry_count_limit")
                        validateEntry(entry)
                        val path = validatePath(if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name)
                        validateHierarchy(path, entry.isDirectory, pathKinds)
                        if (path != ROOT_MANIFEST) memberPaths += path
                        if (!exactPaths.add(path)) reject("archive_duplicate_path")
                        if (!portablePaths.add(portableFold(path))) reject("archive_portable_case_collision")
                        if (!normalizedPaths.add(Normalizer.normalize(path, Normalizer.Form.NFC))) {
                            reject("archive_unicode_normalization_collision")
                        }
                        val pathBytes = path.toByteArray(StandardCharsets.UTF_8).size
                        val depth = path.split('/').size
                        longestPath = max(longestPath, pathBytes)
                        maximumDepth = max(maximumDepth, depth)

                        val destination = extractedRoot.resolve(path).normalize()
                        if (!destination.startsWith(extractedRoot)) reject("archive_path_traversal")
                        if (entry.isDirectory) {
                            Files.createDirectories(destination)
                        } else {
                            if (entry.size < 0 || entry.size > ArchivePolicy.MAX_REGULAR_FILE_BYTES) {
                                reject("archive_file_size_limit")
                            }
                            if (entry.mode and EXECUTABLE_MODE_MASK != 0) reject("archive_executable_mode_forbidden")
                            destination.parent?.let(Files::createDirectories)
                            val extracted = extractRegularFile(tar, entry, path, destination, started)
                            expanded += extracted.size
                            largest = max(largest, extracted.size)
                            if (expanded > ArchivePolicy.MAX_EXPANDED_BYTES) reject("archive_expanded_size_limit")
                            classifyContent(path, extracted.leadingBytes)
                            extractedFiles += extracted
                        }
                    }
                }
            }
        }
        return ExtractionResult(
            expandedBytes = expanded,
            entryCount = entries,
            largestRegularFileBytes = largest,
            longestPathBytes = longestPath,
            maximumPathDepth = maximumDepth,
            memberPaths = memberPaths,
            files = extractedFiles,
        )
    }

    private fun extractRegularFile(
        tar: TarArchiveInputStream,
        entry: TarArchiveEntry,
        path: String,
        destination: Path,
        started: Long,
    ): ExtractedFile {
        val digest = MessageDigest.getInstance("SHA-256")
        val leading = ByteArrayOutputStream(CONTENT_PREFIX_BYTES)
        var total = 0L
        Files.newOutputStream(
            destination,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { output ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                checkDeadline(started)
                val read = tar.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > ArchivePolicy.MAX_REGULAR_FILE_BYTES) reject("archive_file_size_limit")
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
                val prefixRead = minOf(read, CONTENT_PREFIX_BYTES - leading.size())
                if (prefixRead > 0) leading.write(buffer, 0, prefixRead)
            }
        }
        if (total != entry.size) reject("archive_parse_error")
        return ExtractedFile(path, destination, total, digest.hexDigest(), leading.toByteArray())
    }

    private fun rehashExtractedFiles(files: List<ExtractedFile>, started: Long): List<SourceArchiveFile> =
        files.map { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = ByteArrayOutputStream(minOf(file.size, 4096L).toInt())
            var total = 0L
            Files.newInputStream(file.path, LinkOption.NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    checkDeadline(started)
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > ArchivePolicy.MAX_REGULAR_FILE_BYTES) reject("archive_file_size_limit")
                    digest.update(buffer, 0, read)
                    bytes.write(buffer, 0, read)
                }
            }
            if (total != file.size || digest.hexDigest() != file.sha256) reject("archive_extracted_file_mismatch")
            SourceArchiveFile(file.archivePath, bytes.toByteArray())
        }

    private fun validateEntry(entry: TarArchiveEntry) {
        if (!entry.isFile && !entry.isDirectory) reject("archive_entry_type_forbidden")
        if (entry.isSymbolicLink || entry.isLink || entry.isCharacterDevice || entry.isBlockDevice ||
            entry.isFIFO || entry.isSparse
        ) {
            reject("archive_entry_type_forbidden")
        }
        if (entry.isDirectory && entry.size != 0L) reject("archive_entry_type_forbidden")
        if (entry.extraPaxHeaders.isNotEmpty()) reject("archive_entry_type_forbidden")
    }

    private fun validatePath(path: String): String {
        if (path.isBlank() || path.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(path) || '\\' in path) {
            reject("archive_path_absolute")
        }
        if (Normalizer.normalize(path, Normalizer.Form.NFC) != path) reject("archive_unicode_normalization_collision")
        if (path.any { it.code < 0x20 || it.code == 0x7f }) reject("archive_path_invalid")
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) reject("archive_path_traversal")
        if (segments.any(::isNonPortableSegment)) reject("archive_path_invalid")
        if (segments.size > ArchivePolicy.MAX_PATH_DEPTH) reject("archive_path_depth_limit")
        if (path.toByteArray(StandardCharsets.UTF_8).size > ArchivePolicy.MAX_PATH_BYTES) {
            reject("archive_path_length_limit")
        }
        if (segments.any { it.equals(".seen", true) || it.equals("package-map.tsv", true) }) {
            reject("archive_path_invalid")
        }
        return path
    }

    private fun isNonPortableSegment(segment: String): Boolean {
        if (segment.any { it in ":*?\"<>|" } || segment.endsWith('.') || segment.endsWith(' ')) return true
        val stem = segment.substringBefore('.').uppercase()
        return stem in setOf("CON", "PRN", "AUX", "NUL") || Regex("^(?:COM|LPT)[1-9]$").matches(stem)
    }

    private fun validateHierarchy(path: String, directory: Boolean, pathKinds: MutableMap<String, Boolean>) {
        val portable = portableFold(path)
        val segments = portable.split('/')
        for (index in 1 until segments.size) {
            if (pathKinds[segments.take(index).joinToString("/")] == false) reject("archive_path_invalid")
        }
        if (!directory && pathKinds.keys.any { it.startsWith("$portable/") }) reject("archive_path_invalid")
        pathKinds[portable] = directory
    }

    private fun portableFold(value: String): String = value.uppercase(Locale.ROOT).lowercase(Locale.ROOT)

    private fun parsePaxKeys(bytes: ByteArray): Set<String> {
        val keys = linkedSetOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            var space = offset
            while (space < bytes.size && bytes[space] != ' '.code.toByte()) space++
            if (space <= offset || space == bytes.size) reject("archive_parse_error")
            val rawLength = strictUtf8(bytes.copyOfRange(offset, space))
            if (!Regex("[1-9][0-9]*").matches(rawLength)) reject("archive_parse_error")
            val length = rawLength.toIntOrNull() ?: reject("archive_parse_error")
            val end = offset + length
            if (length <= space - offset + 3 || end > bytes.size || bytes[end - 1] != '\n'.code.toByte()) {
                reject("archive_parse_error")
            }
            val record = strictUtf8(bytes.copyOfRange(space + 1, end - 1))
            val separator = record.indexOf('=')
            if (separator <= 0 || separator == record.lastIndex || !keys.add(record.substring(0, separator))) {
                reject("archive_parse_error")
            }
            offset = end
        }
        return keys
    }

    private fun validateRawHeaderPath(header: ByteArray) {
        val name = strictTarString(header, NAME_OFFSET, NAME_LENGTH)
        if (name.isEmpty()) reject("archive_path_invalid")
        val magic = header.copyOfRange(MAGIC_OFFSET, MAGIC_OFFSET + MAGIC_LENGTH)
        if (!magic.all { it == 0.toByte() }) strictTarString(header, PREFIX_OFFSET, PREFIX_LENGTH)
    }

    private fun strictTarString(bytes: ByteArray, offset: Int, length: Int): String {
        val field = bytes.copyOfRange(offset, offset + length)
        val end = field.indexOfFirst { it == 0.toByte() }.let { if (it < 0) field.size else it }
        if (field.drop(end).any { it != 0.toByte() }) reject("archive_parse_error")
        return strictUtf8(field.copyOf(end))
    }

    private fun strictUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        reject("archive_path_invalid")
    }

    private fun classifyContent(path: String, leadingBytes: ByteArray) {
        val lower = path.lowercase()
        val base = lower.substringAfterLast('/')
        val suffixes = setOf(
            ".o", ".obj", ".a", ".lib", ".so", ".dylib", ".dll", ".exe", ".wasm",
            ".class", ".jar", ".pyc", ".pyo", ".bc",
        )
        val magic = listOf(
            "7f454c46", "4d5a", "feedface", "feedfacf", "cefaedfe", "cffaedfe",
            "0061736d", "213c617263683e0a", "4243c0de", "cafebabe", "504b0304",
        )
        val prefix = leadingBytes.take(8).joinToString("") { "%02x".format(it) }
        if (base in setOf("a.out", "core") || suffixes.any(lower::endsWith) || magic.any(prefix::startsWith)) {
            reject("archive_prebuilt_artifact_forbidden")
        }
        val stem = base.substringBeforeLast('.')
        val lifecycleStems = setOf(
            "preinstall", "install", "postinstall", "prebuild", "build", "postbuild", "prepare",
            "configure", "bootstrap", "prepublish", "postpublish", "release",
        )
        val scriptSegments = setOf("scripts", ".hooks", "hooks", "lifecycle", "build-scripts", "install-scripts")
        val scriptSuffixes = setOf(".sh", ".bash", ".zsh", ".fish", ".ps1", ".cmd", ".bat", ".py", ".rb", ".js")
        val leadingText = leadingBytes.decodeToString().take(64).lowercase()
        val isScript = scriptSuffixes.any(lower::endsWith) || prefix.startsWith("2321") ||
            leadingText.startsWith("@echo off") || leadingText.startsWith("\$erroractionpreference")
        if (stem in lifecycleStems || (path.split('/').any { it.lowercase() in scriptSegments } && isScript)) {
            reject("archive_lifecycle_script_forbidden")
        }
    }

    private fun validateManifest(
        bytes: ByteArray,
        identity: String,
        version: String,
        reserved: JsonObject,
        started: Long,
    ): List<String> {
        checkDeadline(started)
        val result = Toml.parse(strictUtf8(bytes))
        checkDeadline(started)
        if (result.hasErrors()) reject("archive_manifest_invalid")
        if (runCatching { result.getLong("manifest-version") }.getOrNull() != 1L) reject("archive_manifest_invalid")
        val archiveIdentity = runCatching { result.getString("package.identity") }.getOrNull()
        val archiveVersion = runCatching { result.getString("project.version") }.getOrNull()
        if (archiveIdentity != identity || archiveVersion != version) reject("archive_manifest_mismatch")
        val reservedIdentity = runCatching {
            reserved["package"]?.jsonObject?.get("identity")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val reservedVersion = runCatching {
            reserved["project"]?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (reservedIdentity != identity || reservedVersion != version) reject("archive_manifest_mismatch")
        val parsedManifest = runCatching { RegistryJson.parseToJsonElement(result.toJson()).jsonObject }
            .getOrElse { reject("archive_manifest_invalid") }
        checkDeadline(started)
        if (parsedManifest != reserved) reject("archive_manifest_mismatch")
        val memberPatterns = runCatching {
            listOf("include", "assets").flatMap { field ->
                val archiveArray = result.getArray("package.$field") ?: error("missing package member list")
                (0 until archiveArray.size()).map { archiveArray.getString(it) }
            }
        }.getOrElse { reject("archive_manifest_invalid") }
        memberPatterns.forEach { pattern ->
            checkDeadline(started)
            validateMemberPattern(pattern)
        }
        return memberPatterns
    }

    private fun validateMemberPattern(pattern: String) {
        if (pattern.isEmpty() || pattern.startsWith('/') || '\\' in pattern || pattern.any { it.code < 0x20 }) {
            reject("archive_manifest_invalid")
        }
        if (pattern.split('/').any { it.isEmpty() || it == "." || it == ".." }) reject("archive_manifest_invalid")
    }

    private fun globMatch(pattern: String, name: String, started: Long): Boolean {
        val patternParts = pattern.split('/')
        val nameParts = name.split('/')
        fun match(patternIndex: Int, nameIndex: Int): Boolean {
            checkDeadline(started)
            if (patternIndex == patternParts.size) return nameIndex == nameParts.size
            if (patternParts[patternIndex] == "**") {
                return (nameIndex..nameParts.size).any { match(patternIndex + 1, it) }
            }
            if (nameIndex == nameParts.size) return false
            val segmentMatches = runCatching {
                java.nio.file.FileSystems.getDefault().getPathMatcher("glob:${patternParts[patternIndex]}")
                    .matches(java.nio.file.Path.of(nameParts[nameIndex]))
            }.getOrDefault(false)
            return segmentMatches && match(patternIndex + 1, nameIndex + 1)
        }
        return match(0, 0)
    }

    private fun checkDeadline(started: Long) {
        if (nanoTime() - started > ArchivePolicy.MAX_VALIDATION_TIME.toNanos()) {
            reject("archive_validation_timeout")
        }
    }

    private fun archiveTooLarge(): Nothing =
        throw RegistryException(413, "archive_too_large", "Archive exceeds the compressed byte limit")

    private fun reject(reason: String): Nothing =
        throw RegistryException(422, "archive_rejected", "Archive was rejected: $reason")

    private fun deleteQuarantine(directory: Path) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private inner class TarBlockReader(
        private val input: InputStream,
        private val started: Long,
    ) {
        private var rawBytes = 0L

        fun readBlock(): ByteArray? {
            val block = ByteArray(TAR_BLOCK_SIZE)
            var offset = 0
            while (offset < block.size) {
                checkDeadline(started)
                val read = input.read(block, offset, block.size - offset)
                if (read < 0) {
                    if (offset == 0) return null
                    reject("archive_parse_error")
                }
                if (read == 0) continue
                offset += read
                rawBytes += read
                if (rawBytes > MAX_RAW_TAR_BYTES) reject("archive_expanded_size_limit")
            }
            return block
        }

        fun readPayload(size: Long, capture: Boolean): ByteArray? {
            val output = if (capture) ByteArrayOutputStream(size.toInt()) else null
            var remaining = size
            val blocks = (size + TAR_BLOCK_SIZE - 1) / TAR_BLOCK_SIZE
            repeat(blocks.toInt()) {
                val block = readBlock() ?: reject("archive_parse_error")
                val contentBytes = minOf(remaining, TAR_BLOCK_SIZE.toLong()).toInt()
                if (capture && contentBytes > 0) output!!.write(block, 0, contentBytes)
                remaining -= contentBytes
            }
            if (remaining != 0L) reject("archive_parse_error")
            return output?.toByteArray()
        }
    }

    private data class StagedArchive(val bytes: Long, val sha256: String)

    private data class ExtractedFile(
        val archivePath: String,
        val path: Path,
        val size: Long,
        val sha256: String,
        val leadingBytes: ByteArray,
    )

    private data class ExtractionResult(
        val expandedBytes: Long,
        val entryCount: Int,
        val largestRegularFileBytes: Long,
        val longestPathBytes: Int,
        val maximumPathDepth: Int,
        val memberPaths: List<String>,
        val files: List<ExtractedFile>,
    )

    private companion object {
        const val COPY_BUFFER_SIZE = 8192
        const val CONTENT_PREFIX_BYTES = 64
        const val TAR_BLOCK_SIZE = 512
        const val MAX_PAX_BYTES = 8192L
        const val MAX_RAW_TAR_BYTES = ArchivePolicy.MAX_EXPANDED_BYTES + ArchivePolicy.MAX_ENTRIES * 2048L + 10240L
        const val COMPRESSED_ARCHIVE_NAME = "archive.tar.gz"
        const val EXTRACTED_DIRECTORY_NAME = "extracted"
        const val ROOT_MANIFEST = "Seen.toml"

        const val NAME_OFFSET = 0
        const val NAME_LENGTH = 100
        const val MODE_OFFSET = 100
        const val MODE_LENGTH = 8
        const val UID_OFFSET = 108
        const val UID_LENGTH = 8
        const val GID_OFFSET = 116
        const val GID_LENGTH = 8
        const val SIZE_OFFSET = 124
        const val SIZE_LENGTH = 12
        const val MTIME_OFFSET = 136
        const val MTIME_LENGTH = 12
        const val TYPE_OFFSET = 156
        const val MAGIC_OFFSET = 257
        const val MAGIC_LENGTH = 6
        const val VERSION_OFFSET = 263
        const val VERSION_LENGTH = 2
        const val PREFIX_OFFSET = 345
        const val PREFIX_LENGTH = 155

        const val REGULAR_TYPE = '0'.code
        const val DIRECTORY_TYPE = '5'.code
        const val PAX_TYPE = 'x'.code
        val ALLOWED_RAW_TYPES = setOf(0, REGULAR_TYPE, DIRECTORY_TYPE, PAX_TYPE)
        const val EXECUTABLE_MODE_MASK = 0b001_001_001
    }
}

private fun ByteArray.isZeroBlock(): Boolean = all { it == 0.toByte() }

private fun MessageDigest.hexDigest(): String = digest().joinToString("") { "%02x".format(it) }

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
