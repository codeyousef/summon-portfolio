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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArchiveValidatorTest {
    @Test
    fun `accepts source-only archive bound to manifest`() {
        val manifest = manifestToml()
        val archive = archiveOf("Seen.toml" to manifest, "src/main.seen" to "fun main() {}".encodeToByteArray())
        val result = ArchiveValidator().validate(
            archive,
            sha256(archive),
            sha256(manifest),
            "seen/demo",
            "1.2.3",
            manifestJson(),
        )
        assertEquals(2, result.entryCount)
        assertEquals(sha256(manifest), result.manifestSha256)
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
