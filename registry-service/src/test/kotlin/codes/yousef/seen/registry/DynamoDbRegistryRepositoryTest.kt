package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* Every behavioural suite below re-runs unchanged against the DynamoDB adapter. */
class DynamoDbReleaseTransitionTest : ReleaseTransitionTest() {
    override fun newRepository(): RegistryRepository = dynamoRepository()
}

class DynamoDbReviewEvidencePersistenceTest : ReviewEvidencePersistenceTest() {
    override fun newRepository(): RegistryRepository = dynamoRepository()
}

class DynamoDbPromotionWorkerTest : PromotionWorkerTest() {
    override fun newRepository(): RegistryRepository = dynamoRepository()
}

class DynamoDbReviewWorkersTest : ReviewWorkersTest() {
    override fun newRepository(): RegistryRepository = dynamoRepository()
}

class DynamoDbRegistryFlowTest : RegistryFlowTest() {
    override fun newRepository(): RegistryRepository = dynamoRepository()
}

class DynamoDbEnforcementServiceTest : EnforcementServiceTest() {
    override fun newRepository(): RegistryRepository = dynamoRepository()
}

class DynamoDbRegistryRepositoryTest {
    private val tables = DynamoDbRegistryTables("seen-registry-dev")

    @Test
    fun `table layout is fixed per deployment identity`() {
        assertEquals("seen-registry-dev", DynamoDbRegistryTables.expectedPrefix("development"))
        assertEquals("seen-registry-prod", DynamoDbRegistryTables.expectedPrefix("production"))
        assertEquals(
            listOf(
                "seen-registry-dev-packages-v1",
                "seen-registry-dev-releases-v1",
                "seen-registry-dev-release-uploads-v1",
                "seen-registry-dev-state-v1",
                "seen-registry-dev-idempotency-v1",
                "seen-registry-dev-review-artifacts-v1",
                "seen-registry-dev-review-artifact-index-v1",
            ),
            tables.all,
        )
        assertEquals(listOf(PK, SK), tables.keySchema[tables.releases])
        assertEquals(listOf(PK, SK), tables.keySchema[tables.reviewArtifactIndex])
        assertFailsWith<IllegalArgumentException> { DynamoDbRegistryTables("bad prefix") }
    }

    @Test
    fun `packages and releases round trip byte stable JSON`() {
        val client = InMemoryDynamoTableClient()
        val repository = dynamoRepository(client)
        val storedPackage = storedPackage()
        assertTrue(repository.createPackage(storedPackage))
        assertFalse(repository.createPackage(storedPackage.copy(ownerPrincipal = "attacker")))
        assertEquals(storedPackage, repository.getPackage("seen/demo"))
        assertEquals(listOf(storedPackage), repository.listPackages())

        val release = dynamoRelease()
        assertTrue(repository.reserveRelease(release))
        assertFalse(repository.reserveRelease(release.copy(uploadId = "upl_ffffffffffffffff")))
        assertEquals(release, repository.getRelease("seen/demo", "1.2.3"))
        assertEquals(release, repository.findReleaseByUpload(release.uploadId))
        assertNull(repository.findReleaseByUpload("upl_ffffffffffffffff"))
        assertEquals(listOf(release), repository.listReleases("seen/demo"))
        assertEquals(emptyList(), repository.listReleases("seen/other"))
        assertEquals(listOf(release), repository.listReleases())

        val stored = client.items(tables.releases).single()
        assertEquals(RegistryJson.encodeToString(StoredRelease.serializer(), release), stored["json"]?.s())
        assertEquals("0", stored["revision"]?.n())
        assertEquals(release.uploadId, stored["upload_id"]?.s())
    }

    @Test
    fun `upload IDs cannot be rebound to a second release`() {
        val repository = dynamoRepository()
        val release = dynamoRelease()
        assertTrue(repository.reserveRelease(release))
        val other = release.copy(record = release.record.copy(version = "1.2.4"))
        assertFailsWith<IllegalStateException> { repository.reserveRelease(other) }
        assertNull(repository.getRelease("seen/demo", "1.2.4"))
    }

    @Test
    fun `transitions are compare and set and reject immutable field changes`() {
        val repository = dynamoRepository()
        val release = dynamoRelease()
        assertTrue(repository.reserveRelease(release))
        val next = release.copy(
            record = release.record.copy(state = release.record.state.copy(lifecycle = "quarantined")),
            revision = 1,
        )
        assertIs<ReleaseTransitionResult.Applied>(repository.transitionRelease(0, next))
        val conflict = assertIs<ReleaseTransitionResult.Conflict>(repository.transitionRelease(0, next))
        assertEquals(1, conflict.current.revision)
        assertFailsWith<IllegalArgumentException> {
            repository.transitionRelease(1, next.copy(manifest = buildJsonObject { put("x", JsonPrimitive(1)) }, revision = 2))
        }
        assertIs<ReleaseTransitionResult.Missing>(
            repository.transitionRelease(0, next.copy(record = next.record.copy(version = "9.9.9"))),
        )
    }

    @Test
    fun `a concurrent write between read and conditional put is reported as a conflict`() {
        val client = InMemoryDynamoTableClient()
        val repository = dynamoRepository(client)
        val release = dynamoRelease()
        assertTrue(repository.reserveRelease(release))
        // Simulate a racing writer that advanced revision after our read.
        val raced = release.copy(revision = 1)
        val competingItem = client.items(tables.releases).single().toMutableMap().apply {
            put("revision", AttributeValue.fromN("1"))
            put("json", AttributeValue.fromS(RegistryJson.encodeToString(StoredRelease.serializer(), raced)))
        }
        val racingClient = object : DynamoTableClient by client {
            var hasRaced = false
            override fun put(put: DynamoPut): DynamoWriteResult {
                if (!hasRaced && put.table == tables.releases) {
                    hasRaced = true
                    client.rawPut(tables.releases, competingItem)
                }
                return client.put(put)
            }
        }
        val result = DynamoDbRegistryRepository(racingClient, tables).transitionRelease(0, release.copy(revision = 1))
        assertEquals(1, assertIs<ReleaseTransitionResult.Conflict>(result).current.revision)
    }

    @Test
    fun `idempotency records acquire replay reject reuse and expire`() {
        val client = InMemoryDynamoTableClient()
        val repository = dynamoRepository(client)
        val now = Instant.parse("2026-07-16T00:00:00Z")
        val record = StoredIdempotency(
            scope = "publisher:create:key-1",
            fingerprint = "f1",
            attemptId = "a1",
            createdAt = now.utc(),
            processingExpiresAt = now.plusSeconds(60).utc(),
            expiresAt = now.plusSeconds(3600).utc(),
        )
        assertIs<IdempotencyBegin.Acquired>(repository.beginIdempotency(record, now))
        assertIs<IdempotencyBegin.InProgress>(repository.beginIdempotency(record.copy(attemptId = "a2"), now))
        val reused = assertFailsWith<RegistryException> {
            repository.beginIdempotency(record.copy(fingerprint = "f2"), now)
        }
        assertEquals(409, reused.status)
        // A stalled attempt is recoverable after its processing lease.
        val later = now.plusSeconds(61)
        assertIs<IdempotencyBegin.Acquired>(repository.beginIdempotency(record.copy(attemptId = "a2"), later))
        val response = StoredIdempotencyResponse(201, "e30", mapOf("Content-Type" to "application/json"))
        assertFalse(repository.completeIdempotency(record.scope, "f1", "a1", response))
        assertTrue(repository.completeIdempotency(record.scope, "f1", "a2", response))
        assertEquals(response, assertIs<IdempotencyBegin.Replay>(repository.beginIdempotency(record, later)).value.response)
        assertIs<IdempotencyBegin.Acquired>(repository.beginIdempotency(record.copy(attemptId = "a3"), now.plusSeconds(3600)))

        val item = client.items(tables.idempotency).single()
        assertEquals((now.plusSeconds(3600).epochSecond + 86_400L).toString(), item["ttl"]?.n())
    }

    @Test
    fun `only one concurrent idempotency reservation is acquired`() {
        val repository = dynamoRepository()
        val now = Instant.parse("2026-07-16T00:00:00Z")
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = (1..8).map { attempt ->
                executor.submit<IdempotencyBegin> {
                    start.await()
                    repository.beginIdempotency(StoredIdempotency(
                        scope = "scope",
                        fingerprint = "f",
                        attemptId = "a$attempt",
                        createdAt = now.utc(),
                        processingExpiresAt = now.plusSeconds(60).utc(),
                        expiresAt = now.plusSeconds(600).utc(),
                    ), now)
                }
            }
            start.countDown()
            val completed = results.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, completed.count { it is IdempotencyBegin.Acquired })
            assertEquals(7, completed.count { it is IdempotencyBegin.InProgress })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `review artifacts are create only and listed from the consistent index`() {
        val client = InMemoryDynamoTableClient()
        val repository = dynamoRepository(client)
        val first = artifact("art_1", sequence = 1, kind = "source-proof")
        val second = artifact("art_2", sequence = 2, kind = "scan-attestation")
        val unbound = artifact("art_3", sequence = 3, kind = "audit-event", packageIdentity = null, version = null)
        assertTrue(repository.appendReviewArtifact(first))
        assertTrue(repository.appendReviewArtifact(second))
        assertTrue(repository.appendReviewArtifact(unbound))
        assertFalse(repository.appendReviewArtifact(first.copy(sequence = 99)))

        assertEquals(first, repository.getReviewArtifact("art_1"))
        assertEquals(listOf(second, first), repository.listReviewArtifacts("seen/demo", "1.2.3"))
        assertEquals(listOf(first), repository.listReviewArtifacts("seen/demo", "1.2.3", "source-proof"))
        assertEquals(listOf(unbound, second, first), repository.listReviewArtifacts())
        assertEquals(listOf(unbound), repository.listReviewArtifacts(kind = "audit-event"))
        assertEquals(2, client.items(tables.reviewArtifactIndex).size)
        assertEquals(3, client.items(tables.reviewArtifacts).size)
    }

    @Test
    fun `publication lease mirrors Firestore expiry semantics`() {
        val client = InMemoryDynamoTableClient()
        val repository = dynamoRepository(client)
        val now = Instant.parse("2026-07-16T00:00:00Z")
        assertTrue(repository.tryAcquireMetadataPublication("a", now, now.plusSeconds(30)))
        assertTrue(repository.tryAcquireMetadataPublication("a", now, now.plusSeconds(60)))
        assertFalse(repository.tryAcquireMetadataPublication("b", now.plusSeconds(59), now.plusSeconds(90)))
        // Exactly at expiry the lease is no longer live, as with Instant.isAfter.
        assertTrue(repository.tryAcquireMetadataPublication("b", now.plusSeconds(60), now.plusSeconds(90)))
        repository.releaseMetadataPublication("a")
        assertFalse(repository.tryAcquireMetadataPublication("a", now.plusSeconds(61), now.plusSeconds(120)))
        repository.releaseMetadataPublication("b")
        assertTrue(repository.tryAcquireMetadataPublication("a", now.plusSeconds(61), now.plusSeconds(120)))

        assertEquals(1, repository.nextMetadataVersion())
        assertEquals(2, repository.nextMetadataVersion())
        val state = client.items(tables.state).single()
        assertEquals("2", state["version"]?.n())
        assertEquals("a", state["publication_holder"]?.s())
    }

    @Test
    fun `large records are gzip encoded and still round trip`() {
        val client = InMemoryDynamoTableClient()
        val repository = dynamoRepository(client)
        val large = artifact("art_large", sequence = 1, kind = "scan-attestation").copy(
            payload = buildJsonObject { put("blob", JsonPrimitive("x".repeat(400_000))) },
        )
        assertTrue(repository.appendReviewArtifact(large))
        val item = client.items(tables.reviewArtifacts).single()
        assertNull(item["json"])
        assertNotNull(item["json_gz"])
        assertEquals(large, repository.getReviewArtifact("art_large"))
    }

    @Test
    fun `incompressible records beyond the item bound fail closed`() {
        val repository = dynamoRepository()
        val random = java.util.Random(7)
        val noise = (1..800_000).map { "0123456789abcdef"[random.nextInt(16)] }.joinToString("")
        assertFailsWith<IllegalArgumentException> {
            repository.appendReviewArtifact(artifact("art_noise", 1, "scan-attestation").copy(
                payload = buildJsonObject { put("blob", JsonPrimitive(noise)) },
            ))
        }
    }

    @Test
    fun `expression rendering uses placeholders for every name and value`() {
        val rendered = DynamoExpressionBuilder.condition(DynamoCondition.AnyOf(listOf(
            DynamoCondition.Absent("publication_holder"),
            DynamoCondition.Equals("publication_holder", stringValue("me")),
            DynamoCondition.AtMost("publication_expires_at_epoch_nanos", 5),
        )))
        assertEquals("(attribute_not_exists(#c0) OR #c0 = :c0 OR #c1 <= :c1)", rendered.expression)
        assertEquals(mapOf("#c0" to "publication_holder", "#c1" to "publication_expires_at_epoch_nanos"), rendered.names)
        assertEquals(mapOf(":c0" to stringValue("me"), ":c1" to numberValue(5)), rendered.values)
    }

    private fun storedPackage() = StoredPackage(
        record = PackageRecord(
            identity = "seen/demo",
            createdAt = "2026-07-16T00:00:00Z",
            updatedAt = "2026-07-16T00:00:00Z",
            links = PackageLinks("/packages/api/v1/packages/seen/demo", "/packages/api/v1/packages/seen/demo/releases"),
        ),
        ownerPrincipal = "publisher",
    )

    private fun artifact(
        id: String,
        sequence: Long,
        kind: String,
        packageIdentity: String? = "seen/demo",
        version: String? = "1.2.3",
    ) = ReviewArtifact(
        artifactId = id,
        kind = kind,
        packageIdentity = packageIdentity,
        version = version,
        archiveSha256 = "a".repeat(64),
        sequence = sequence,
        createdAt = "2026-07-16T00:00:0${sequence % 10}Z",
        payload = buildJsonObject { put("id", JsonPrimitive(id)) },
    )
}

internal fun dynamoRelease(): StoredRelease {
    val now = "2026-07-16T00:00:00Z"
    return StoredRelease(
        record = ReleaseRecord(
            `package` = "seen/demo",
            version = "1.2.3",
            archive = ArchiveStats(sha256 = "a".repeat(64), compressedBytes = 128),
            manifestSha256 = "b".repeat(64),
            state = ReleaseState(visibility = "public"),
            timestamps = ReleaseTimestamps(reservedAt = now, updatedAt = now),
            links = ReleaseLinks("/packages/api/v1/packages/seen/demo/releases/1.2.3", "/packages/api/v1/packages/seen/demo"),
        ),
        ownerPrincipal = "publisher",
        uploadId = "upl_0123456789abcdef",
        uploadExpiresAt = "2026-07-16T01:00:00Z",
        manifest = manifestJson(),
        source = SourceDeclaration("github", "seen-demo", "installation-1", "refs/tags/v1.2.3", "a".repeat(40), "MIT"),
    )
}
