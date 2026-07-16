package codes.yousef.seen.registry

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarUtils
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.tomlj.Toml
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Duration
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

data class ArchiveValidation(
    val archiveSha256: String,
    val manifestSha256: String,
    val manifestBytes: ByteArray,
    val expandedBytes: Long,
    val entryCount: Int,
    val largestRegularFileBytes: Long,
    val longestPathBytes: Int,
    val maximumPathDepth: Int,
)

class ArchiveValidator(
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun validate(
        bytes: ByteArray,
        expectedArchiveSha256: String,
        expectedManifestSha256: String,
        expectedIdentity: String,
        expectedVersion: String,
        reservedManifest: kotlinx.serialization.json.JsonObject,
    ): ArchiveValidation {
        if (bytes.isEmpty()) reject("archive_empty")
        if (bytes.size > ArchivePolicy.MAX_COMPRESSED_BYTES) {
            throw RegistryException(413, "archive_too_large", "Archive exceeds the compressed byte limit")
        }
        val digest = sha256(bytes)
        if (digest != expectedArchiveSha256) throw RegistryException(422, "digest_mismatch", "Archive digest does not match the reservation")

        val started = nanoTime()
        val tarBytes = decodeSingleGzip(bytes, started)
        validateTarStructure(tarBytes)
        var expanded = 0L
        var entries = 0
        var largest = 0L
        var longestPath = 0
        var maximumDepth = 0
        var manifestBytes: ByteArray? = null
        val memberPaths = mutableListOf<String>()
        val exactPaths = HashSet<String>()
        val portablePaths = HashSet<String>()
        val normalizedPaths = HashSet<String>()
        val pathKinds = HashMap<String, Boolean>()

        try {
            TarArchiveInputStream(ByteArrayInputStream(tarBytes)).use { tar ->
                    while (true) {
                        checkDeadline(started)
                        val entry = tar.nextEntry ?: break
                        entries++
                        if (entries > ArchivePolicy.MAX_ENTRIES) reject("archive_entry_count_limit")
                        validateEntry(entry)
                        val path = validatePath(if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name)
                        validateHierarchy(path, entry.isDirectory, pathKinds)
                        if (path != "Seen.toml") memberPaths += path
                        if (!exactPaths.add(path)) reject("archive_duplicate_path")
                        if (!portablePaths.add(portableFold(path))) reject("archive_portable_case_collision")
                        if (!normalizedPaths.add(Normalizer.normalize(path, Normalizer.Form.NFC))) reject("archive_unicode_normalization_collision")
                        val pathBytes = path.toByteArray(StandardCharsets.UTF_8).size
                        val depth = path.split('/').size
                        longestPath = max(longestPath, pathBytes)
                        maximumDepth = max(maximumDepth, depth)

                        if (entry.isFile) {
                            if (entry.size < 0 || entry.size > ArchivePolicy.MAX_REGULAR_FILE_BYTES) reject("archive_file_size_limit")
                            if (entry.mode and 0b001_001_001 != 0) reject("archive_executable_mode_forbidden")
                            val content = readEntry(tar, entry.size, started)
                            expanded += content.size
                            largest = max(largest, content.size.toLong())
                            if (expanded > ArchivePolicy.MAX_EXPANDED_BYTES) reject("archive_expanded_size_limit")
                            classifyContent(path, content)
                            if (path == "Seen.toml") {
                                if (manifestBytes != null) reject("archive_duplicate_path")
                                manifestBytes = content
                            }
                        }
                    }
            }
        } catch (error: RegistryException) {
            throw error
        } catch (_: Exception) {
            reject("archive_parse_error")
        }

        if (expanded > bytes.size.toLong() * ArchivePolicy.MAX_COMPRESSION_RATIO) reject("archive_compression_ratio_limit")
        val manifest = manifestBytes ?: reject("archive_manifest_missing")
        val manifestDigest = sha256(manifest)
        if (manifestDigest != expectedManifestSha256) reject("archive_manifest_mismatch")
        val memberPatterns = validateManifest(manifest, expectedIdentity, expectedVersion, reservedManifest)
        if (memberPaths.any { path -> memberPatterns.none { pattern -> globMatch(pattern, path) } }) {
            reject("archive_include_policy_violation")
        }
        return ArchiveValidation(digest, manifestDigest, manifest, expanded, entries, largest, longestPath, maximumDepth)
    }

    private fun validateEntry(entry: TarArchiveEntry) {
        if (!entry.isFile && !entry.isDirectory) reject("archive_entry_type_forbidden")
        if (entry.isSymbolicLink || entry.isLink || entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO || entry.isSparse) {
            reject("archive_entry_type_forbidden")
        }
        if (entry.isDirectory && entry.size != 0L) reject("archive_entry_type_forbidden")
        if (entry.extraPaxHeaders.isNotEmpty()) reject("archive_entry_type_forbidden")
    }

    private fun validatePath(path: String): String {
        if (path.isBlank() || path.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(path) || '\\' in path) reject("archive_path_absolute")
        if (Normalizer.normalize(path, Normalizer.Form.NFC) != path) reject("archive_unicode_normalization_collision")
        if (path.any { it.code < 0x20 || it.code == 0x7f }) reject("archive_path_invalid")
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) reject("archive_path_traversal")
        if (segments.any(::isNonPortableSegment)) reject("archive_path_invalid")
        if (segments.size > ArchivePolicy.MAX_PATH_DEPTH) reject("archive_path_depth_limit")
        if (path.toByteArray(StandardCharsets.UTF_8).size > ArchivePolicy.MAX_PATH_BYTES) reject("archive_path_length_limit")
        if (segments.any { it.equals(".seen", true) || it.equals("package-map.tsv", true) }) reject("archive_path_invalid")
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

    private fun decodeSingleGzip(bytes: ByteArray, started: Long): ByteArray {
        val output = ByteArrayOutputStream()
        try {
            GzipCompressorInputStream(ByteArrayInputStream(bytes), false).use { gzip ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkDeadline(started)
                    val read = gzip.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    if (output.size().toLong() > MAX_RAW_TAR_BYTES) reject("archive_expanded_size_limit")
                }
                if (gzip.compressedCount != bytes.size.toLong()) reject("archive_parse_error")
            }
        } catch (error: RegistryException) {
            throw error
        } catch (_: Exception) {
            reject("archive_parse_error")
        }
        return output.toByteArray()
    }

    private fun validateTarStructure(bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size % TAR_BLOCK_SIZE != 0) reject("archive_parse_error")
        var offset = 0
        var pendingPax = false
        while (offset + TAR_BLOCK_SIZE <= bytes.size) {
            val header = bytes.copyOfRange(offset, offset + TAR_BLOCK_SIZE)
            if (header.all { it == 0.toByte() }) {
                if (pendingPax || offset + 2 * TAR_BLOCK_SIZE > bytes.size) reject("archive_parse_error")
                if (!bytes.copyOfRange(offset + TAR_BLOCK_SIZE, offset + 2 * TAR_BLOCK_SIZE).all { it == 0.toByte() }) reject("archive_parse_error")
                if (!bytes.copyOfRange(offset, bytes.size).all { it == 0.toByte() }) reject("archive_parse_error")
                return
            }
            if (!TarUtils.verifyCheckSum(header)) reject("archive_parse_error")
            val size = runCatching { TarUtils.parseOctalOrBinary(header, 124, 12) }.getOrElse { reject("archive_parse_error") }
            if (size < 0 || size > MAX_RAW_TAR_BYTES) reject("archive_parse_error")
            val type = header[156].toInt() and 0xff
            if (type !in setOf(0, '0'.code, '5'.code, 'x'.code)) reject("archive_entry_type_forbidden")
            if (type != 'x'.code) validateRawHeaderPath(header)
            val dataStart = offset + TAR_BLOCK_SIZE
            val dataEnd = dataStart.toLong() + size
            if (dataEnd > bytes.size) reject("archive_parse_error")
            if (type == 'x'.code) {
                if (pendingPax || size > MAX_PAX_BYTES) reject("archive_entry_type_forbidden")
                val keys = parsePaxKeys(bytes.copyOfRange(dataStart, dataEnd.toInt()))
                if (keys != setOf("path")) reject("archive_entry_type_forbidden")
                pendingPax = true
            } else {
                pendingPax = false
            }
            val padded = ((size + TAR_BLOCK_SIZE - 1) / TAR_BLOCK_SIZE) * TAR_BLOCK_SIZE
            val next = dataStart.toLong() + padded
            if (next > bytes.size || next > Int.MAX_VALUE) reject("archive_parse_error")
            offset = next.toInt()
        }
        reject("archive_parse_error")
    }

    private fun parsePaxKeys(bytes: ByteArray): Set<String> {
        val keys = linkedSetOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            var space = offset
            while (space < bytes.size && bytes[space] != ' '.code.toByte()) space++
            if (space <= offset || space == bytes.size) reject("archive_parse_error")
            val length = strictUtf8(bytes.copyOfRange(offset, space)).toIntOrNull() ?: reject("archive_parse_error")
            val end = offset + length
            if (length <= space - offset + 3 || end > bytes.size || bytes[end - 1] != '\n'.code.toByte()) reject("archive_parse_error")
            val record = strictUtf8(bytes.copyOfRange(space + 1, end - 1))
            val separator = record.indexOf('=')
            if (separator <= 0 || !keys.add(record.substring(0, separator))) reject("archive_parse_error")
            offset = end
        }
        return keys
    }

    private fun validateRawHeaderPath(header: ByteArray) {
        fun field(offset: Int, length: Int): String {
            val end = (offset until offset + length).firstOrNull { header[it] == 0.toByte() } ?: (offset + length)
            return strictUtf8(header.copyOfRange(offset, end))
        }
        val name = field(0, 100)
        if (name.isEmpty()) reject("archive_path_invalid")
        val magic = header.copyOfRange(257, 263).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
        if (magic.startsWith("ustar")) field(345, 155)
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

    private fun readEntry(tar: TarArchiveInputStream, declaredSize: Long, started: Long): ByteArray {
        val output = ByteArrayOutputStream(minOf(declaredSize, 4096).toInt())
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            checkDeadline(started)
            val read = tar.read(buffer)
            if (read < 0) break
            total += read
            if (total > ArchivePolicy.MAX_REGULAR_FILE_BYTES) reject("archive_file_size_limit")
            output.write(buffer, 0, read)
        }
        if (total != declaredSize) reject("archive_parse_error")
        return output.toByteArray()
    }

    private fun classifyContent(path: String, content: ByteArray) {
        val lower = path.lowercase()
        val base = lower.substringAfterLast('/')
        val suffixes = setOf(".o", ".obj", ".a", ".lib", ".so", ".dylib", ".dll", ".exe", ".wasm", ".class", ".jar", ".pyc", ".pyo", ".bc")
        val magic = listOf("7f454c46", "4d5a", "feedface", "feedfacf", "cefaedfe", "cffaedfe", "0061736d", "213c617263683e0a", "4243c0de", "cafebabe", "504b0304")
        val prefix = content.take(8).joinToString("") { "%02x".format(it) }
        if (base in setOf("a.out", "core") || suffixes.any(lower::endsWith) || magic.any(prefix::startsWith)) {
            reject("archive_prebuilt_artifact_forbidden")
        }
        val stem = base.substringBeforeLast('.')
        val lifecycleStems = setOf("preinstall", "install", "postinstall", "prebuild", "build", "postbuild", "prepare", "configure", "bootstrap", "prepublish", "postpublish", "release")
        val scriptSegments = setOf("scripts", ".hooks", "hooks", "lifecycle", "build-scripts", "install-scripts")
        val scriptSuffixes = setOf(".sh", ".bash", ".zsh", ".fish", ".ps1", ".cmd", ".bat", ".py", ".rb", ".js")
        val leadingText = content.decodeToString().take(64).lowercase()
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
    ): List<String> {
        val result = Toml.parse(strictUtf8(bytes))
        if (result.hasErrors()) reject("archive_manifest_invalid")
        if (runCatching { result.getLong("manifest-version") }.getOrNull() != 1L) reject("archive_manifest_invalid")
        val archiveIdentity = runCatching { result.getString("package.identity") }.getOrNull()
        val archiveVersion = runCatching { result.getString("project.version") }.getOrNull()
        if (archiveIdentity != identity || archiveVersion != version) reject("archive_manifest_mismatch")
        val reservedIdentity = runCatching { reserved["package"]?.jsonObject?.get("identity")?.jsonPrimitive?.contentOrNull }.getOrNull()
        val reservedVersion = runCatching { reserved["project"]?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull }.getOrNull()
        if (reservedIdentity != identity || reservedVersion != version) reject("archive_manifest_mismatch")
        val parsedManifest = runCatching { RegistryJson.parseToJsonElement(result.toJson()).jsonObject }
            .getOrElse { reject("archive_manifest_invalid") }
        if (parsedManifest != reserved) reject("archive_manifest_mismatch")
        val memberPatterns = runCatching {
            listOf("include", "assets").flatMap { field ->
                val archiveArray = result.getArray("package.$field") ?: error("missing package member list")
                (0 until archiveArray.size()).map { archiveArray.getString(it) }
            }
        }.getOrElse { reject("archive_manifest_invalid") }
        memberPatterns.forEach(::validateMemberPattern)
        return memberPatterns
    }

    private fun validateMemberPattern(pattern: String) {
        if (pattern.isEmpty() || pattern.startsWith('/') || '\\' in pattern || pattern.any { it.code < 0x20 }) reject("archive_manifest_invalid")
        if (pattern.split('/').any { it.isEmpty() || it == "." || it == ".." }) reject("archive_manifest_invalid")
    }

    private fun globMatch(pattern: String, name: String): Boolean {
        val patternParts = pattern.split('/')
        val nameParts = name.split('/')
        fun match(patternIndex: Int, nameIndex: Int): Boolean {
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
        if (nanoTime() - started > ArchivePolicy.MAX_VALIDATION_TIME.toNanos()) reject("archive_validation_timeout")
    }

    private fun reject(reason: String): Nothing = throw RegistryException(422, "archive_rejected", "Archive was rejected: $reason")

    private companion object {
        const val TAR_BLOCK_SIZE = 512
        const val MAX_PAX_BYTES = 8192L
        const val MAX_RAW_TAR_BYTES = ArchivePolicy.MAX_EXPANDED_BYTES + ArchivePolicy.MAX_ENTRIES * 2048L + 10240L
    }
}

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
