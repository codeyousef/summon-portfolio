package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistryDataCopyTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val tables = DynamoDbRegistryTables("seen-registry-dev")
    private val targetBuckets = mapOf(
        RegistryBucketRole.QUARANTINE to "seen-registry-dev-quarantine-000000000000",
        RegistryBucketRole.PUBLIC to "seen-registry-dev-public-000000000000",
        RegistryBucketRole.METADATA to "seen-registry-dev-metadata-000000000000",
    )

    private fun config(confirmed: Boolean) = RegistryDataCopyConfig(
        environment = "development",
        projectId = "portfolio-476219",
        sourceFirestoreDatabase = "seen-registry-dev",
        sourceBuckets = mapOf(
            RegistryBucketRole.QUARANTINE to "seen-dev-quarantine",
            RegistryBucketRole.PUBLIC to "seen-dev-public",
            RegistryBucketRole.METADATA to "seen-dev-metadata",
        ),
        targetBuckets = targetBuckets,
        objectPrefix = "v1",
        awsRegion = "eu-north-1",
        dynamoDbTablePrefix = "seen-registry-dev",
        confirmed = confirmed,
    )

    private class Target {
        val dynamo = InMemoryDynamoTableClient()
        val objects = RecordingObjects(FakeS3CompatibleObjectClient())
    }

    /** Records create order so tests can prove pointers are written last. */
    private class RecordingObjects(private val delegate: FakeS3CompatibleObjectClient) : S3CompatibleObjectClient by delegate {
        val created = mutableListOf<String>()
        override fun create(bucket: String, key: String, bytes: ByteArray, contentType: String, cacheControl: String): Boolean =
            delegate.create(bucket, key, bytes, contentType, cacheControl).also { if (it) created += key }
        override fun put(bucket: String, key: String, bytes: ByteArray, contentType: String, cacheControl: String) =
            error("The data copy must never issue an unconditional put")
        override fun replace(
            bucket: String,
            key: String,
            expectedVersion: String?,
            bytes: ByteArray,
            contentType: String,
            cacheControl: String,
        ): Boolean = error("The data copy must never replace an object")
        override fun delete(bucket: String, key: String) = error("The data copy must never delete")
    }

    private fun copy(source: RegistryCopySource, target: Target, confirmed: Boolean) = RegistryDataCopy(
        config(confirmed),
        source,
        tables,
        target.dynamo,
        target.objects,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `dry run plans every record and object without writing`() {
        val target = Target()
        val report = copy(FakeSource(), target, confirmed = false).run()
        assertTrue(report.dryRun)
        assertEquals(0, report.conflicts)
        assertFalse(report.parity)
        assertEquals(10, report.pending) // 1 package, 1 release, 2 artifacts, 1 idempotency, 1 state, 4 objects
        tables.all.forEach { assertEquals(emptyList(), target.dynamo.items(it), it) }
        assertEquals(emptyList(), target.objects.created)
    }

    @Test
    fun `apply copies create only verbatim and reaches parity then reruns idempotently`() {
        val source = FakeSource()
        val target = Target()
        val report = copy(source, target, confirmed = true).run()
        assertTrue(report.parity, report.render())
        assertEquals(10, report.sections.sumOf { it.outcomes[CopyOutcome.CREATED] ?: 0 })

        val repository = dynamoRepository(target.dynamo)
        assertEquals(source.release, repository.getRelease("seen/demo", "1.2.3"))
        assertEquals(source.release, repository.findReleaseByUpload(source.release.uploadId))
        assertEquals(listOf(source.artifact), repository.listReviewArtifacts("seen/demo", "1.2.3"))
        assertEquals("6", target.dynamo.items(tables.state).single()["version"]?.n())
        // Source JSON text is stored verbatim, not re-encoded.
        assertEquals(source.packageJson, target.dynamo.items(tables.packages).single()["json"]?.s())

        val metadata = targetBuckets.getValue(RegistryBucketRole.METADATA)
        assertContentEquals(source.rootBytes, target.objects.get(metadata, "v1/metadata/1.root.json")?.bytes)
        assertEquals("v1/metadata/root.json", target.objects.created.last())
        assertEquals(
            "public,max-age=31536000,immutable",
            target.objects.get(targetBuckets.getValue(RegistryBucketRole.PUBLIC), source.blobKey)?.cacheControl,
        )

        val rerun = copy(source, target, confirmed = true).run()
        assertTrue(rerun.parity, rerun.render())
        assertEquals(10, rerun.sections.sumOf { it.outcomes[CopyOutcome.ALREADY_IDENTICAL] ?: 0 })
        assertEquals(1, rerun.sections.single { it.name == "state" }.targetCount)
    }

    @Test
    fun `different existing target data is a conflict and is never overwritten`() {
        val source = FakeSource()
        val target = Target()
        val repository = dynamoRepository(target.dynamo)
        val squatter = source.storedPackage.copy(ownerPrincipal = "someone-else")
        assertTrue(repository.createPackage(squatter))
        val metadata = targetBuckets.getValue(RegistryBucketRole.METADATA)
        target.objects.create(metadata, "v1/metadata/1.root.json", "different".toByteArray(), "application/json", "x")

        val report = copy(source, target, confirmed = true).run()
        assertEquals(2, report.conflicts, report.render())
        assertFalse(report.parity)
        assertEquals(squatter, repository.getPackage("seen/demo"))
        assertContentEquals("different".toByteArray(), target.objects.get(metadata, "v1/metadata/1.root.json")?.bytes)
        assertTrue(report.render().contains("problem section=packages"))
        assertFalse(report.render().contains("someone-else"), "reports never include record contents")
    }

    @Test
    fun `content addressed objects must match their digest`() {
        val source = FakeSource(corruptBlob = true)
        val report = copy(source, Target(), confirmed = true).run()
        assertEquals(1, report.mismatches)
    }

    @Test
    fun `foreign trust roots and live publication leases stop the copy`() {
        assertFailsWith<IllegalStateException> {
            copy(FakeSource(rootEnvironment = "production"), Target(), confirmed = true).run()
        }
        assertFailsWith<IllegalStateException> {
            copy(FakeSource(leaseExpiry = now.plusSeconds(30)), Target(), confirmed = true).run()
        }
        // An expired lease is transient state and is not copied.
        val target = Target()
        assertTrue(copy(FakeSource(leaseExpiry = now.minusSeconds(30)), target, confirmed = true).run().parity)
        assertEquals(null, target.dynamo.items(tables.state).single()["publication_holder"])
    }

    @Test
    fun `configuration defaults to dry run and binds development only`() {
        val env = mapOf(
            "REGISTRY_ENVIRONMENT" to "development",
            "GOOGLE_CLOUD_PROJECT" to "portfolio-476219",
            "REGISTRY_COPY_SOURCE_FIRESTORE_DATABASE" to "seen-registry-dev",
            "REGISTRY_COPY_SOURCE_QUARANTINE_BUCKET" to "seen-dev-quarantine",
            "REGISTRY_COPY_SOURCE_PUBLIC_BUCKET" to "seen-dev-public",
            "REGISTRY_COPY_SOURCE_METADATA_BUCKET" to "seen-dev-metadata",
            "REGISTRY_QUARANTINE_BUCKET" to targetBuckets.getValue(RegistryBucketRole.QUARANTINE),
            "REGISTRY_PUBLIC_BUCKET" to targetBuckets.getValue(RegistryBucketRole.PUBLIC),
            "REGISTRY_METADATA_BUCKET" to targetBuckets.getValue(RegistryBucketRole.METADATA),
            "AWS_REGION" to "eu-north-1",
        )
        assertFalse(RegistryDataCopyConfig.fromEnvironment(env).confirmed)
        assertTrue(RegistryDataCopyConfig.fromEnvironment(env + ("REGISTRY_COPY_CONFIRM" to "copy-development-gcp-to-aws")).confirmed)
        listOf(
            env + ("REGISTRY_COPY_CONFIRM" to "yes"),
            env + ("REGISTRY_ENVIRONMENT" to "production"),
            env + ("REGISTRY_COPY_SOURCE_FIRESTORE_DATABASE" to "seen-registry-prod"),
            env + ("REGISTRY_COPY_SOURCE_PUBLIC_BUCKET" to "seen-prod-public"),
            env + ("REGISTRY_PUBLIC_BUCKET" to "seen-registry-prod-public-000000000000"),
            env + ("REGISTRY_DYNAMODB_TABLE_PREFIX" to "seen-registry-prod"),
            env + ("REGISTRY_METADATA_BUCKET" to targetBuckets.getValue(RegistryBucketRole.PUBLIC)),
            env + ("REGISTRY_MAINTENANCE_ENVELOPE_BASE64" to "YQ=="),
            env + ("REGISTRY_KMS_RELEASES_PUBLIC_KEY_HEX" to "00".repeat(32)),
            env + ("AWS_SECRET_ACCESS_KEY" to "static"),
            env - "REGISTRY_COPY_SOURCE_METADATA_BUCKET",
        ).forEach { candidate ->
            assertFailsWith<IllegalArgumentException>(candidate.toString()) { RegistryDataCopyConfig.fromEnvironment(candidate) }
        }
    }

    private inner class FakeSource(
        rootEnvironment: String = "development",
        private val leaseExpiry: Instant? = null,
        corruptBlob: Boolean = false,
    ) : RegistryCopySource {
        val release = dynamoRelease()
        val storedPackage = StoredPackage(
            record = PackageRecord(
                identity = "seen/demo",
                createdAt = "2026-07-16T00:00:00Z",
                updatedAt = "2026-07-16T00:00:00Z",
                links = PackageLinks("/packages/api/v1/packages/seen/demo", "/packages/api/v1/packages/seen/demo/releases"),
            ),
            ownerPrincipal = "publisher",
        )
        val packageJson: String = RegistryJson.encodeToString(StoredPackage.serializer(), storedPackage)
        val artifact = ReviewArtifact(
            artifactId = "art_1",
            kind = "source-proof",
            packageIdentity = "seen/demo",
            version = "1.2.3",
            archiveSha256 = "a".repeat(64),
            sequence = 1,
            createdAt = "2026-07-16T00:00:01Z",
            payload = buildJsonObject { put("id", JsonPrimitive("art_1")) },
        )
        private val unbound = artifact.copy(artifactId = "art_2", packageIdentity = null, version = null, sequence = 2)
        private val idempotency = StoredIdempotency(
            scope = "publisher:create:key",
            fingerprint = "f",
            attemptId = "a",
            createdAt = now.utc(),
            processingExpiresAt = now.plusSeconds(60).utc(),
            expiresAt = now.plusSeconds(600).utc(),
        )
        val rootBytes = """{"signatures":[],"signed":{"environment":"$rootEnvironment","repository_id":"seen-dev-registry-v1"}}"""
            .toByteArray()
        private val blob = "archive".toByteArray()
        val blobKey = "v1/blobs/sha256/${sha256(blob)}"
        private val objects = mapOf(
            RegistryBucketRole.QUARANTINE to listOf(
                SourceObject("v1/quarantine/upl_0123456789abcdef", blob, "application/gzip", "no-store"),
            ),
            RegistryBucketRole.PUBLIC to listOf(
                SourceObject(blobKey, if (corruptBlob) "tampered".toByteArray() else blob, "application/gzip",
                    "public,max-age=31536000,immutable"),
            ),
            RegistryBucketRole.METADATA to listOf(
                // Listed pointer-first to prove the copier reorders pointers last.
                SourceObject("v1/metadata/root.json", rootBytes, "application/json", "public,max-age=300,must-revalidate"),
                SourceObject("v1/metadata/1.root.json", rootBytes, "application/json", "public,max-age=31536000,immutable"),
            ),
        )

        private fun id(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        private fun json(value: String) = mapOf<String, Any?>("json" to value)

        override fun documents(collection: String): List<SourceDocument> = when (collection) {
            PACKAGES_COLLECTION -> listOf(SourceDocument(id("seen/demo"), json(packageJson)))
            RELEASES_COLLECTION -> listOf(SourceDocument(
                id("seen/demo@1.2.3"),
                json(RegistryJson.encodeToString(StoredRelease.serializer(), release)) + ("upload_id" to release.uploadId),
            ))
            REVIEW_ARTIFACTS_COLLECTION -> listOf(artifact, unbound).map {
                SourceDocument(id(it.artifactId), json(RegistryJson.encodeToString(ReviewArtifact.serializer(), it)))
            }
            IDEMPOTENCY_COLLECTION -> listOf(SourceDocument(
                id(idempotency.scope),
                json(RegistryJson.encodeToString(StoredIdempotency.serializer(), idempotency)),
            ))
            STATE_COLLECTION -> listOf(SourceDocument("metadata", buildMap {
                put("version", 6L)
                leaseExpiry?.let {
                    put("publication_holder", "holder-1")
                    put("publication_expires_at", it.utc())
                }
            }))
            else -> error("unexpected collection $collection")
        }

        override fun objectKeys(role: RegistryBucketRole, prefix: String): List<String> =
            objects.getValue(role).map(SourceObject::key).filter { it.startsWith(prefix) }

        override fun getObject(role: RegistryBucketRole, key: String): SourceObject? =
            objects.getValue(role).firstOrNull { it.key == key }
    }
}
