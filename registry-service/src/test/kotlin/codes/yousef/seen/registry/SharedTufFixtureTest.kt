package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedTufFixtureTest {
    private val contractRoot = Path.of("contracts/package-registry/v1")

    @Test
    fun `shared frozen TUF fixture verifies with registry canonicalization and crypto`() {
        val fixture = RegistryJson.parseToJsonElement(
            Files.readString(contractRoot.resolve("fixtures/tuf-metadata-examples.json")),
        ).jsonObject

        assertEquals("1", fixture.getValue("contract_version").jsonPrimitive.content)
        assertEquals("../schemas/tuf-metadata-envelope-v1.schema.json", fixture.getValue("schema").jsonPrimitive.content)
        assertEquals("tuf-canonical-json-v1", fixture.getValue("canonical_serialization").jsonPrimitive.content)
        assertEquals(
            "deterministic-test-only-not-an-official-trust-root",
            fixture.getValue("cryptographic_material").jsonPrimitive.content,
        )

        val trustBoundaries = fixture.getValue("trust_boundaries").jsonObject
        val environment = "development"
        val repositoryId = trustBoundaries.getValue("test_repository_id").jsonPrimitive.content
        val registryOrigin = trustBoundaries.getValue("test_registry_origin").jsonPrimitive.content
        assertEquals("seen-dev-test-fixture-v1", repositoryId)
        assertEquals("https://test.invalid/packages", registryOrigin)
        listOf("development_official_root", "production_official_root").forEach { name ->
            val boundary = trustBoundaries.getValue(name).jsonObject
            assertEquals("unconfigured", boundary.getValue("status").jsonPrimitive.content)
            assertEquals("fail-closed", boundary.getValue("client_behavior").jsonPrimitive.content)
        }

        val validationTime = Instant.parse(fixture.getValue("validation_time").jsonPrimitive.content)
        val metadata = fixture.getValue("metadata").jsonObject
        assertEquals(
            setOf("root", "targets", "release_targets", "security_targets", "snapshot", "timestamp"),
            metadata.keys,
        )

        val root = parseEnvelope(metadata.getValue("root"))
        val rootSigned = root.getValue("signed").jsonObject
        val rootKeys = parseKeys(rootSigned.getValue("keys").jsonObject)
        val rootRoles = rootSigned.getValue("roles").jsonObject

        val targets = parseEnvelope(metadata.getValue("targets"))
        val targetsSigned = targets.getValue("signed").jsonObject
        val delegations = targetsSigned.getValue("delegations").jsonObject
        val delegatedKeys = parseKeys(delegations.getValue("keys").jsonObject)
        val delegatedRoles = delegations.getValue("roles").jsonArray
            .associateBy { it.jsonObject.getValue("name").jsonPrimitive.content }

        val roleTrust = mapOf(
            "root" to roleTrust(rootRoles.getValue("root").jsonObject, rootKeys),
            "targets" to roleTrust(rootRoles.getValue("targets").jsonObject, rootKeys),
            "release_targets" to roleTrust(delegatedRoles.getValue("releases").jsonObject, delegatedKeys),
            "security_targets" to roleTrust(delegatedRoles.getValue("security").jsonObject, delegatedKeys),
            "snapshot" to roleTrust(rootRoles.getValue("snapshot").jsonObject, rootKeys),
            "timestamp" to roleTrust(rootRoles.getValue("timestamp").jsonObject, rootKeys),
        )
        val expectedTypes = mapOf(
            "root" to "root",
            "targets" to "targets",
            "release_targets" to "targets",
            "security_targets" to "targets",
            "snapshot" to "snapshot",
            "timestamp" to "timestamp",
        )

        metadata.forEach { (name, document) ->
            val envelope = parseEnvelope(document)
            val signed = envelope.getValue("signed").jsonObject
            assertEquals(expectedTypes.getValue(name), signed.getValue("_type").jsonPrimitive.content, name)
            assertEquals("1.0", signed.getValue("spec_version").jsonPrimitive.content, name)
            assertTrue(signed.getValue("version").jsonPrimitive.content.toLong() >= 1L, name)
            assertTrue(
                Instant.parse(signed.getValue("expires").jsonPrimitive.content).isAfter(validationTime),
                "$name is not fresh at the fixture validation time",
            )
            assertEquals(environment, signed.getValue("environment").jsonPrimitive.content, name)
            assertEquals(repositoryId, signed.getValue("repository_id").jsonPrimitive.content, name)

            val trust = roleTrust.getValue(name)
            val signatures = envelope.getValue("signatures").jsonArray.map(JsonElement::jsonObject)
            signatures.forEach { signature ->
                assertEquals(setOf("keyid", "sig"), signature.keys, name)
                assertTrue(signature.getValue("keyid").jsonPrimitive.content in trust.keyIds, name)
            }
            verifyThreshold(envelope, trust.keys, trust.keyIds, trust.threshold)
        }

        assertMetadataChain(fixture, metadata)
        assertTargets(metadata, validationTime, environment, registryOrigin)
        assertDamagedSignatureFails(metadata, roleTrust.getValue("release_targets"))
    }

    private fun assertMetadataChain(fixture: JsonObject, metadata: JsonObject) {
        val chain = fixture.getValue("metadata_chain").jsonObject
        val snapshot = parseEnvelope(metadata.getValue("snapshot")).getValue("signed").jsonObject
        val snapshotMeta = snapshot.getValue("meta").jsonObject
        val snapshotDocuments = chain.getValue("snapshot_meta_documents").jsonObject
        assertEquals(snapshotDocuments.keys, snapshotMeta.keys)
        snapshotDocuments.forEach { (filename, documentName) ->
            assertDescriptor(snapshotMeta.getValue(filename).jsonObject, metadata, documentName.jsonPrimitive.content)
        }

        val timestamp = parseEnvelope(metadata.getValue("timestamp")).getValue("signed").jsonObject
        val timestampMeta = timestamp.getValue("meta").jsonObject
        val timestampDocuments = chain.getValue("timestamp_meta_documents").jsonObject
        assertEquals(timestampDocuments.keys, timestampMeta.keys)
        timestampDocuments.forEach { (filename, documentName) ->
            assertDescriptor(timestampMeta.getValue(filename).jsonObject, metadata, documentName.jsonPrimitive.content)
        }
    }

    private fun assertDescriptor(descriptor: JsonObject, metadata: JsonObject, documentName: String) {
        assertEquals(setOf("version", "length", "hashes"), descriptor.keys, documentName)
        val document = metadata.getValue(documentName)
        val canonical = canonicalJson(document)
        val signed = document.jsonObject.getValue("signed").jsonObject
        assertEquals(
            signed.getValue("version").jsonPrimitive.content.toLong(),
            descriptor.getValue("version").jsonPrimitive.content.toLong(),
            documentName,
        )
        assertEquals(canonical.size.toLong(), descriptor.getValue("length").jsonPrimitive.content.toLong(), documentName)
        assertEquals(
            sha256(canonical),
            descriptor.getValue("hashes").jsonObject.getValue("sha256").jsonPrimitive.content,
            documentName,
        )
    }

    private fun assertTargets(
        metadata: JsonObject,
        validationTime: Instant,
        environment: String,
        registryOrigin: String,
    ) {
        val releases = parseEnvelope(metadata.getValue("release_targets"))
            .getValue("signed").jsonObject.getValue("targets").jsonObject
        val security = parseEnvelope(metadata.getValue("security_targets"))
            .getValue("signed").jsonObject.getValue("targets").jsonObject
        assertTrue(releases.isNotEmpty())
        assertEquals(releases.keys, security.keys)

        releases.forEach { (path, target) ->
            assertTarget(path, target.jsonObject, "releases", validationTime, environment, registryOrigin)
        }
        security.forEach { (path, target) ->
            assertTarget(path, target.jsonObject, "security", validationTime, environment, registryOrigin)

            val releaseTarget = releases.getValue(path).jsonObject
            assertEquals(releaseTarget.getValue("length"), target.jsonObject.getValue("length"), path)
            assertEquals(releaseTarget.getValue("hashes"), target.jsonObject.getValue("hashes"), path)
            val releaseCustom = releaseTarget.getValue("custom").jsonObject
            val normalizedSecurity = target.jsonObject.getValue("custom").jsonObject.toMutableMap().apply {
                put("availability", releaseCustom.getValue("availability"))
                remove("incident_id")
                remove("security_action")
            }.let(::JsonObject)
            assertEquals(releaseCustom, normalizedSecurity, path)
        }
    }

    private fun assertTarget(
        path: String,
        target: JsonObject,
        role: String,
        validationTime: Instant,
        environment: String,
        registryOrigin: String,
    ) {
        assertEquals(setOf("length", "hashes", "custom"), target.keys, path)
        val length = target.getValue("length").jsonPrimitive.content.toLong()
        val targetSha256 = target.getValue("hashes").jsonObject.getValue("sha256").jsonPrimitive.content
        val custom = target.getValue("custom").jsonObject
        val owner = custom.getValue("owner").jsonPrimitive.content
        val name = custom.getValue("name").jsonPrimitive.content
        val packageIdentity = custom.getValue("package").jsonPrimitive.content
        val version = custom.getValue("version").jsonPrimitive.content
        val archiveSha256 = custom.getValue("archive_sha256").jsonPrimitive.content
        val archiveFilename = custom.getValue("archive_filename").jsonPrimitive.content
        val blob = custom.getValue("blob").jsonObject

        assertEquals("$owner/$name", packageIdentity, path)
        assertEquals("$name-$version.seenpkg.tgz", archiveFilename, path)
        assertEquals("packages/$packageIdentity/$version/$archiveSha256/$archiveFilename", path)
        assertEquals(archiveSha256, targetSha256, path)
        assertEquals(setOf("sha256", "length"), blob.keys, path)
        assertEquals(targetSha256, blob.getValue("sha256").jsonPrimitive.content, path)
        assertEquals(length, blob.getValue("length").jsonPrimitive.content.toLong(), path)
        assertEquals(environment, custom.getValue("environment").jsonPrimitive.content, path)
        assertEquals(registryOrigin, custom.getValue("registry_origin").jsonPrimitive.content, path)
        assertEquals("public", custom.getValue("visibility").jsonPrimitive.content, path)
        assertEquals("active", custom.getValue("lifecycle").jsonPrimitive.content, path)
        assertEquals("retained", custom.getValue("retention").jsonPrimitive.content, path)
        assertFalse(Instant.parse(custom.getValue("activated_at").jsonPrimitive.content).isAfter(validationTime), path)

        val review = custom.getValue("review").jsonObject
        assertEquals("passed", review.getValue("result").jsonPrimitive.content, path)
        assertEquals(
            review.getValue("source_proof_sha256").jsonPrimitive.content,
            custom.getValue("source_proof_sha256").jsonPrimitive.content,
            path,
        )
        custom.getValue("dependencies").jsonArray
        custom.getValue("capabilities").jsonArray

        val attestationProjection = buildJsonObject {
            put("subject", buildJsonObject {
                listOf("package", "owner", "name", "version", "blob", "visibility").forEach { field ->
                    put(field, custom.getValue(field))
                }
            })
            put("publisher_principal", custom.getValue("publisher_principal"))
            put("registry_service_identity", custom.getValue("registry_service_identity"))
            put("source_repository", custom.getValue("source_repository"))
            put("source_commit", custom.getValue("source_commit"))
            put("review", review)
            put("activated_at", custom.getValue("activated_at"))
        }
        val attestationSha256 = sha256(canonicalJson(attestationProjection))
        assertEquals(attestationSha256, custom.getValue("registry_attestation_sha256").jsonPrimitive.content, path)
        assertEquals(attestationSha256, custom.getValue("provenance_sha256").jsonPrimitive.content, path)

        when (role) {
            "releases" -> assertEquals("available", custom.getValue("availability").jsonPrimitive.content, path)
            "security" -> {
                assertEquals("security-quarantined", custom.getValue("availability").jsonPrimitive.content, path)
                assertTrue(custom.getValue("incident_id").jsonPrimitive.content.startsWith("inc_"), path)
                assertEquals("quarantine", custom.getValue("security_action").jsonPrimitive.content, path)
            }
            else -> error("Unexpected target role $role")
        }
    }

    private fun assertDamagedSignatureFails(metadata: JsonObject, trust: RoleTrust) {
        val envelope = parseEnvelope(metadata.getValue("release_targets"))
        val signatures = envelope.getValue("signatures").jsonArray
        val first = signatures.first().jsonObject
        val signature = first.getValue("sig").jsonPrimitive.content
        val damaged = (if (signature[0] == '0') "1" else "0") + signature.drop(1)
        val damagedSignatures = JsonArray(signatures.mapIndexed { index, item ->
            if (index == 0) {
                JsonObject(item.jsonObject.toMutableMap().apply { put("sig", JsonPrimitive(damaged)) })
            } else {
                item
            }
        })
        val damagedEnvelope = JsonObject(envelope.toMutableMap().apply { put("signatures", damagedSignatures) })
        assertFailsWith<IllegalArgumentException> {
            verifyThreshold(damagedEnvelope, trust.keys, trust.keyIds, trust.threshold)
        }
    }

    private fun parseEnvelope(document: JsonElement): JsonObject =
        parseCanonicalEnvelope(canonicalJson(document))

    private fun roleTrust(role: JsonObject, keys: Map<String, ByteArray>): RoleTrust {
        val keyIds = role.getValue("keyids").jsonArray.map { it.jsonPrimitive.content }.toSet()
        val threshold = role.getValue("threshold").jsonPrimitive.content.toInt()
        assertTrue(keyIds.isNotEmpty())
        assertTrue(keyIds.all(keys::containsKey))
        assertTrue(threshold in 1..keyIds.size)
        return RoleTrust(keys, keyIds, threshold)
    }

    private data class RoleTrust(
        val keys: Map<String, ByteArray>,
        val keyIds: Set<String>,
        val threshold: Int,
    )
}
