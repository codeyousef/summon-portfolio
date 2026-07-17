package codes.yousef.seen.registry

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarUtils
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveValidatorTest {
    @Test
    fun `accepts source-only archive bound to manifest`() {
        val manifest = manifestToml()
        val archive = archiveOf("Seen.toml" to manifest, "src/main.seen" to "fun main() {}".encodeToByteArray())
        var openCount = 0
        val result = ArchiveValidator().validate(
            ReopenableArchiveSource {
                openCount++
                ByteArrayInputStream(archive)
            },
            sha256(archive),
            sha256(manifest),
            "seen/demo",
            "1.2.3",
            manifestJson(),
        )
        assertEquals(2, result.entryCount)
        assertEquals(sha256(manifest), result.manifestSha256)
        assertEquals(1, openCount)
        assertEquals(setOf("Seen.toml", "src/main.seen"), result.declaredPackagePaths)
        assertEquals(
            "fun main() {}",
            result.archiveFiles.single { it.path == "src/main.seen" }.bytes.decodeToString(),
        )
    }

    @Test
    fun `rejects traversal and prebuilt artifacts before promotion`() {
        val manifest = manifestToml()
        val traversal = archiveOf("Seen.toml" to manifest, "../escape.seen" to byteArrayOf(1))
        assertEquals("archive_rejected", assertFailsWith<RegistryException> {
            ArchiveValidator().validate(traversal, sha256(traversal), sha256(manifest), "seen/demo", "1.2.3", manifestJson())
        }.code)

        val binary = archiveOf("Seen.toml" to manifest, "src/payload.wasm" to byteArrayOf(0, 0x61, 0x73, 0x6d))
        assertEquals("archive_rejected", assertFailsWith<RegistryException> {
            ArchiveValidator().validate(binary, sha256(binary), sha256(manifest), "seen/demo", "1.2.3", manifestJson())
        }.code)
    }

    @Test
    fun `recomputes include and asset membership from effective archive paths`() {
        val manifest = manifestToml(assets = listOf("assets/**"))
        val reserved = manifestJson(assets = listOf("assets/**"))
        val accepted = archiveOf(
            "Seen.toml" to manifest,
            "src/main.seen" to "fun main() {}".encodeToByteArray(),
            "assets/logo.txt" to "logo".encodeToByteArray(),
        )
        assertEquals(
            3,
            ArchiveValidator().validate(accepted, sha256(accepted), sha256(manifest), "seen/demo", "1.2.3", reserved).entryCount,
        )

        val escaped = archiveOf(
            "Seen.toml" to manifest,
            "src/main.seen" to "fun main() {}".encodeToByteArray(),
            "assets/logo.txt" to "logo".encodeToByteArray(),
            ".env.production" to "SECRET=value".encodeToByteArray(),
        )
        val error = assertFailsWith<RegistryException> {
            ArchiveValidator().validate(escaped, sha256(escaped), sha256(manifest), "seen/demo", "1.2.3", reserved)
        }
        assertContains(error.message.orEmpty(), "archive_include_policy_violation")
    }

    @Test
    fun `binds the complete parsed manifest value`() {
        val manifest = manifestToml(projectName = "archive_name")
        val archive = archiveOf("Seen.toml" to manifest, "src/main.seen" to "fun main() {}".encodeToByteArray())
        val error = assertFailsWith<RegistryException> {
            ArchiveValidator().validate(
                archive,
                sha256(archive),
                sha256(manifest),
                "seen/demo",
                "1.2.3",
                manifestJson(projectName = "reserved_name"),
            )
        }
        assertContains(error.message.orEmpty(), "archive_manifest_mismatch")
    }

    @Test
    fun `rejects trailing gzip members and tar streams without complete end markers`() {
        val manifest = manifestToml()
        val archive = archiveOf("Seen.toml" to manifest, "src/main.seen" to "fun main() {}".encodeToByteArray())
        listOf(archive + byteArrayOf(0), archive + archive).forEach { malformed ->
            assertEquals("archive_rejected", assertFailsWith<RegistryException> {
                ArchiveValidator().validate(malformed, sha256(malformed), sha256(manifest), "seen/demo", "1.2.3", manifestJson())
            }.code)
        }

        val raw = uncompressed(archive)
        var end = raw.size
        while (end >= 512 && raw.copyOfRange(end - 512, end).all { it == 0.toByte() }) end -= 512
        val truncated = gzip(raw.copyOf(end))
        assertEquals("archive_rejected", assertFailsWith<RegistryException> {
            ArchiveValidator().validate(truncated, sha256(truncated), sha256(manifest), "seen/demo", "1.2.3", manifestJson())
        }.code)
    }

    @Test
    fun `rejects nonportable names and file parent hierarchy conflicts`() {
        val manifest = manifestToml(include = listOf("**"))
        val reserved = manifestJson(include = listOf("**"))
        listOf("src/CON.seen", "src/bad?.seen", "src/trailing.").forEach { path ->
            val archive = archiveOf("Seen.toml" to manifest, path to byteArrayOf(1))
            assertEquals("archive_rejected", assertFailsWith<RegistryException> {
                ArchiveValidator().validate(archive, sha256(archive), sha256(manifest), "seen/demo", "1.2.3", reserved)
            }.code)
        }
        val hierarchy = archiveOf(
            "Seen.toml" to manifest,
            "src" to "not a directory".encodeToByteArray(),
            "src/main.seen" to "fun main() {}".encodeToByteArray(),
        )
        assertEquals("archive_rejected", assertFailsWith<RegistryException> {
            ArchiveValidator().validate(hierarchy, sha256(hierarchy), sha256(manifest), "seen/demo", "1.2.3", reserved)
        }.code)

        val foldedCollision = archiveOf(
            "Seen.toml" to manifest,
            "src/straße.seen" to byteArrayOf(1),
            "src/STRASSE.seen" to byteArrayOf(2),
        )
        assertEquals("archive_rejected", assertFailsWith<RegistryException> {
            ArchiveValidator().validate(foldedCollision, sha256(foldedCollision), sha256(manifest), "seen/demo", "1.2.3", reserved)
        }.code)
    }

    @Test
    fun `rejects unsupported pax metadata and powershell content in script paths`() {
        val manifest = manifestToml(include = listOf("src/**", "scripts/**"))
        val reserved = manifestJson(include = listOf("src/**", "scripts/**"))
        val pax = archiveWithPax(
            manifest,
            "src/main.seen",
            "fun main() {}".encodeToByteArray(),
            "SCHILY.xattr.user.test" to "value",
        )
        assertEquals("archive_rejected", assertFailsWith<RegistryException> {
            ArchiveValidator().validate(pax, sha256(pax), sha256(manifest), "seen/demo", "1.2.3", reserved)
        }.code)

        val powershell = archiveOf(
            "Seen.toml" to manifest,
            "src/main.seen" to "fun main() {}".encodeToByteArray(),
            "scripts/setup.txt" to "\$ErrorActionPreference = 'Stop'".encodeToByteArray(),
        )
        assertEquals("archive_rejected", assertFailsWith<RegistryException> {
            ArchiveValidator().validate(powershell, sha256(powershell), sha256(manifest), "seen/demo", "1.2.3", reserved)
        }.code)

        val malformedPathTar = uncompressed(archiveOf(
            "Seen.toml" to manifest,
            "src/main.seen" to "fun main() {}".encodeToByteArray(),
        ))
        val secondHeader = 1024
        malformedPathTar[secondHeader] = 0xff.toByte()
        val header = malformedPathTar.copyOfRange(secondHeader, secondHeader + 512)
        for (index in 148 until 156) header[index] = ' '.code.toByte()
        TarUtils.formatCheckSumOctalBytes(TarUtils.computeCheckSum(header), header, 148, 8)
        header.copyInto(malformedPathTar, secondHeader)
        val malformedPath = gzip(malformedPathTar)
        assertEquals("archive_rejected", assertFailsWith<RegistryException> {
            ArchiveValidator().validate(malformedPath, sha256(malformedPath), sha256(manifest), "seen/demo", "1.2.3", reserved)
        }.code)
    }

    @Test
    fun `rejects executable files and every non-regular tar entry class`() {
        val manifest = manifestToml(include = listOf("**"))
        val reserved = manifestJson(include = listOf("**"))
        val base = archiveOf("Seen.toml" to manifest, "src/payload.txt" to "payload".encodeToByteArray())

        val executable = mutateHeader(base, 1) { header ->
            writeOctal(header, 100, 8, 0b111_101_101)
        }
        assertRejected(executable, manifest, reserved, "archive_executable_mode_forbidden")

        mapOf(
            '1' to "hard link",
            '2' to "symbolic link",
            '3' to "character device",
            '4' to "block device",
            '6' to "FIFO",
            'S' to "sparse file",
            's' to "socket",
            'Z' to "unknown header",
        ).forEach { (type, description) ->
            val archive = mutateHeader(base, 1) { header -> header[156] = type.code.toByte() }
            val error = assertFailsWith<RegistryException>(description) {
                ArchiveValidator().validate(archive, sha256(archive), sha256(manifest), "seen/demo", "1.2.3", reserved)
            }
            assertContains(error.message.orEmpty(), "archive_entry_type_forbidden", message = description)
        }
    }

    @Test
    fun `rejects malformed checksum numeric and magic headers`() {
        val manifest = manifestToml()
        val base = archiveOf("Seen.toml" to manifest, "src/main.seen" to "fun main() {}".encodeToByteArray())
        val badChecksum = uncompressed(base).also { raw -> raw[1024] = (raw[1024].toInt() xor 1).toByte() }
            .let(::gzip)
        val badNumber = mutateHeader(base, 1) { header -> header[124] = '9'.code.toByte() }
        val badMagic = mutateHeader(base, 1) { header -> "broken".encodeToByteArray().copyInto(header, 257) }

        listOf(badChecksum, badNumber, badMagic).forEach { archive ->
            assertRejected(archive, manifest, manifestJson(), "archive_parse_error")
        }
    }

    @Test
    fun `rejects exact portable-case and unicode path collisions`() {
        val manifest = manifestToml(include = listOf("**"))
        val reserved = manifestJson(include = listOf("**"))
        val cases = listOf(
            archiveOf(
                "Seen.toml" to manifest,
                "src/same.seen" to byteArrayOf(1),
                "src/same.seen" to byteArrayOf(2),
            ) to "archive_duplicate_path",
            archiveOf(
                "Seen.toml" to manifest,
                "src/Main.seen" to byteArrayOf(1),
                "src/main.seen" to byteArrayOf(2),
            ) to "archive_portable_case_collision",
            archiveOf(
                "Seen.toml" to manifest,
                "src/caf\u00e9.seen" to byteArrayOf(1),
                "src/cafe\u0301.seen" to byteArrayOf(2),
            ) to "archive_unicode_normalization_collision",
        )
        cases.forEach { (archive, reason) -> assertRejected(archive, manifest, reserved, reason) }
    }

    @Test
    fun `rejects compressed and regular file limits entry-count bombs and compression-ratio bombs`() {
        val manifest = manifestToml(include = listOf("**"))
        val reserved = manifestJson(include = listOf("**"))

        val oversizedFile = archiveOf("Seen.toml" to manifest, "src/large.bin" to byteArrayOf(1))
            .let { archive ->
                mutateHeader(archive, 1) { header ->
                    writeOctal(header, 124, 12, ArchivePolicy.MAX_REGULAR_FILE_BYTES + 1)
                }
            }
        assertRejected(oversizedFile, manifest, reserved, "archive_file_size_limit")

        val tooManyEntries = buildList {
            add("Seen.toml" to manifest)
            repeat(ArchivePolicy.MAX_ENTRIES) { index -> add("src/e$index" to byteArrayOf()) }
        }.let { archiveOf(*it.toTypedArray()) }
        assertRejected(tooManyEntries, manifest, reserved, "archive_entry_count_limit")

        val ratioBomb = archiveOf(
            "Seen.toml" to manifest,
            "src/highly-compressible.txt" to ByteArray(512 * 1024),
        )
        assertRejected(ratioBomb, manifest, reserved, "archive_compression_ratio_limit")

        val parent = Files.createTempDirectory("archive-compressed-limit-test-")
        val quarantines = mutableListOf<Path>()
        try {
            val validator = ArchiveValidator(
                createQuarantineDirectory = {
                    Files.createTempDirectory(parent, "quarantine-").also(quarantines::add)
                },
            )
            val source = ReopenableArchiveSource {
                RepeatingInputStream(ArchivePolicy.MAX_COMPRESSED_BYTES + 1)
            }
            val error = assertFailsWith<RegistryException> {
                validator.validate(source, "unused", sha256(manifest), "seen/demo", "1.2.3", reserved)
            }
            assertEquals(413, error.status)
            assertEquals("archive_too_large", error.code)
            assertTrue(quarantines.all { !Files.exists(it) })
        } finally {
            deleteTree(parent)
        }
    }

    @Test
    fun `rejects expanded path-length and path-depth limits while accepting bounded pax paths`() {
        val manifest = manifestToml(include = listOf("**"))
        val reserved = manifestJson(include = listOf("**"))

        val boundedLongPath = "src/${"a".repeat(120)}.seen"
        val accepted = archiveOf("Seen.toml" to manifest, boundedLongPath to byteArrayOf(1))
        assertEquals(
            setOf("Seen.toml", boundedLongPath),
            ArchiveValidator().validate(
                accepted,
                sha256(accepted),
                sha256(manifest),
                "seen/demo",
                "1.2.3",
                reserved,
            ).declaredPackagePaths,
        )

        val pathTooLong = "src/${"a".repeat(ArchivePolicy.MAX_PATH_BYTES)}"
        val longArchive = archiveOf("Seen.toml" to manifest, pathTooLong to byteArrayOf(1))
        assertRejected(longArchive, manifest, reserved, "archive_path_length_limit")

        val pathTooDeep = (1..ArchivePolicy.MAX_PATH_DEPTH).joinToString("/") { "d$it" } + "/file"
        val deepArchive = archiveOf("Seen.toml" to manifest, pathTooDeep to byteArrayOf(1))
        assertRejected(deepArchive, manifest, reserved, "archive_path_depth_limit")

        val tenMiB = ByteArray(ArchivePolicy.MAX_REGULAR_FILE_BYTES.toInt())
        val expandedBomb = buildList {
            add("Seen.toml" to manifest)
            repeat(11) { index -> add("src/large-$index.txt" to tenMiB) }
        }.let { archiveOf(*it.toTypedArray()) }
        assertRejected(expandedBomb, manifest, reserved, "archive_expanded_size_limit")
    }

    @Test
    fun `rejects hidden binary magic even behind an allowed text name`() {
        val manifest = manifestToml()
        val hiddenElf = archiveOf(
            "Seen.toml" to manifest,
            "src/innocent.txt" to byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 1, 2, 3, 4),
        )
        assertRejected(hiddenElf, manifest, manifestJson(), "archive_prebuilt_artifact_forbidden")
    }

    @Test
    fun `uses fresh quarantines and cleans them after success rejection and timeout`() {
        val manifest = manifestToml()
        val archive = archiveOf("Seen.toml" to manifest, "src/main.seen" to "fun main() {}".encodeToByteArray())
        val parent = Files.createTempDirectory("archive-cleanup-test-")
        val quarantines = mutableListOf<Path>()
        val factory = {
            Files.createTempDirectory(parent, "quarantine-").also(quarantines::add)
        }
        try {
            val validator = ArchiveValidator(createQuarantineDirectory = factory)
            repeat(2) {
                validator.validate(archive, sha256(archive), sha256(manifest), "seen/demo", "1.2.3", manifestJson())
            }
            val malformed = archive + byteArrayOf(0)
            assertFailsWith<RegistryException> {
                validator.validate(malformed, sha256(malformed), sha256(manifest), "seen/demo", "1.2.3", manifestJson())
            }

            var clockReads = 0
            val timedOut = ArchiveValidator(
                nanoTime = {
                    if (clockReads++ == 0) 0L else ArchivePolicy.MAX_VALIDATION_TIME.toNanos() + 1
                },
                createQuarantineDirectory = factory,
            )
            val timeout = assertFailsWith<RegistryException> {
                timedOut.validate(archive, sha256(archive), sha256(manifest), "seen/demo", "1.2.3", manifestJson())
            }
            assertContains(timeout.message.orEmpty(), "archive_validation_timeout")

            assertEquals(4, quarantines.size)
            assertEquals(quarantines.size, quarantines.distinct().size)
            assertTrue(quarantines.all { !Files.exists(it) })
            Files.newDirectoryStream(parent).use { assertFalse(it.iterator().hasNext()) }
        } finally {
            deleteTree(parent)
        }
    }

    private fun assertRejected(
        archive: ByteArray,
        manifest: ByteArray,
        reserved: kotlinx.serialization.json.JsonObject,
        reason: String,
    ) {
        val error = assertFailsWith<RegistryException> {
            ArchiveValidator().validate(archive, sha256(archive), sha256(manifest), "seen/demo", "1.2.3", reserved)
        }
        assertEquals("archive_rejected", error.code)
        assertContains(error.message.orEmpty(), reason)
    }
}

internal fun manifestToml(
    identity: String = "seen/demo",
    version: String = "1.2.3",
    projectName: String = "demo",
    include: List<String> = listOf("src/**"),
    assets: List<String> = emptyList(),
): ByteArray = """
    manifest-version = 1

    [project]
    name = "$projectName"
    version = "$version"

    [package]
    identity = "$identity"
    visibility = "public"
    include = ${tomlArray(include)}
    assets = ${tomlArray(assets)}
    capabilities = []

    [dependencies]
""".trimIndent().encodeToByteArray()

internal fun manifestJson(
    identity: String = "seen/demo",
    version: String = "1.2.3",
    projectName: String = "demo",
    include: List<String> = listOf("src/**"),
    assets: List<String> = emptyList(),
) = buildJsonObject {
    put("manifest-version", 1)
    put("project", buildJsonObject {
        put("name", projectName)
        put("version", version)
    })
    put("package", buildJsonObject {
        put("identity", identity)
        put("visibility", "public")
        put("include", buildJsonArray { include.forEach(::add) })
        put("assets", buildJsonArray { assets.forEach(::add) })
        put("capabilities", buildJsonArray {})
    })
    put("dependencies", buildJsonObject {})
}

private fun tomlArray(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

internal fun archiveOf(vararg files: Pair<String, ByteArray>): ByteArray {
    val compressed = ByteArrayOutputStream()
    GzipCompressorOutputStream(compressed).use { gzip ->
        TarArchiveOutputStream(gzip).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            files.forEach { (name, content) ->
                val entry = TarArchiveEntry(name).apply {
                    size = content.size.toLong()
                    mode = 0b110_100_100
                }
                tar.putArchiveEntry(entry)
                tar.write(content)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
    }
    return compressed.toByteArray()
}

private fun uncompressed(archive: ByteArray): ByteArray =
    GzipCompressorInputStream(ByteArrayInputStream(archive), false).use { it.readAllBytes() }

private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
    GzipCompressorOutputStream(output).use { it.write(bytes) }
}.toByteArray()

private fun archiveWithPax(
    manifest: ByteArray,
    path: String,
    content: ByteArray,
    pax: Pair<String, String>,
): ByteArray {
    val compressed = ByteArrayOutputStream()
    GzipCompressorOutputStream(compressed).use { gzip ->
        TarArchiveOutputStream(gzip).use { tar ->
            listOf("Seen.toml" to manifest, path to content).forEachIndexed { index, (name, value) ->
                val entry = TarArchiveEntry(name).apply {
                    size = value.size.toLong()
                    mode = 0b110_100_100
                    if (index == 1) addPaxHeader(pax.first, pax.second)
                }
                tar.putArchiveEntry(entry)
                tar.write(value)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
    }
    return compressed.toByteArray()
}

private fun mutateHeader(
    archive: ByteArray,
    headerIndex: Int,
    mutation: (ByteArray) -> Unit,
): ByteArray {
    val raw = uncompressed(archive)
    val offset = tarHeaderOffsets(raw)[headerIndex]
    val header = raw.copyOfRange(offset, offset + 512)
    mutation(header)
    for (index in 148 until 156) header[index] = ' '.code.toByte()
    TarUtils.formatCheckSumOctalBytes(TarUtils.computeCheckSum(header), header, 148, 8)
    header.copyInto(raw, offset)
    return gzip(raw)
}

private fun tarHeaderOffsets(raw: ByteArray): List<Int> = buildList {
    var offset = 0
    while (offset + 512 <= raw.size) {
        val header = raw.copyOfRange(offset, offset + 512)
        if (header.all { it == 0.toByte() }) break
        add(offset)
        val size = TarUtils.parseOctalOrBinary(header, 124, 12)
        offset += 512 + (((size + 511) / 512) * 512).toInt()
    }
}

private fun writeOctal(header: ByteArray, offset: Int, length: Int, value: Long) {
    val encoded = value.toString(8).padStart(length - 1, '0') + '\u0000'
    require(encoded.length == length)
    encoded.encodeToByteArray().copyInto(header, offset)
}

private class RepeatingInputStream(private var remaining: Long) : InputStream() {
    override fun read(): Int {
        if (remaining == 0L) return -1
        remaining--
        return 0
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return -1
        val count = minOf(remaining, length.toLong()).toInt()
        buffer.fill(0, offset, offset + count)
        remaining -= count
        return count
    }
}

private fun deleteTree(path: Path) {
    if (!Files.exists(path)) return
    Files.walk(path).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
}
