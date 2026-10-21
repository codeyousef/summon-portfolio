package code.yousef.aws.dynamodb

import code.yousef.aws.DynamoDbLocal
import code.yousef.portfolio.ai.FirestoreAiProgressStore
import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.model.PhotographyMediaType
import code.yousef.portfolio.content.model.PhotographyPhoto
import code.yousef.portfolio.content.model.PhotographySourceKind
import code.yousef.portfolio.content.seed.PortfolioContentSeed
import code.yousef.portfolio.content.store.DocumentContentStore
import code.yousef.portfolio.finops.AllocationMethod
import code.yousef.portfolio.finops.CostAllocation
import code.yousef.portfolio.finops.FinOpsDirection
import code.yousef.portfolio.finops.FinOpsEntry
import code.yousef.portfolio.finops.FinOpsEntryOrder
import code.yousef.portfolio.finops.FinOpsIngestRequest
import code.yousef.portfolio.finops.FinOpsQuery
import code.yousef.portfolio.finops.FinOpsService
import code.yousef.portfolio.finops.FinOpsStatus
import code.yousef.portfolio.finops.FirestoreFinOpsRepository
import code.yousef.portfolio.finops.MoneyAmount
import code.yousef.portfolio.ui.fifthwall.DynamoDbFifthWallTelemetryStore
import code.yousef.portfolio.ui.fifthwall.FifthWallHiddenMetrics
import code.yousef.portfolio.ui.fifthwall.FifthWallTelemetryEvent
import code.yousef.portfolio.ui.fifthwall.FifthWallTelemetryPayload
import code.yousef.portfolio.ui.fifthwall.FifthWallTelemetrySummary
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Exercises the real repositories and stores against DynamoDB Local through the generic store. */
class DynamoDbBackedServicesTest {
    private lateinit var store: DynamoDbPortfolioDocumentStore

    @BeforeTest
    fun setUp() {
        val client = DynamoDbLocal.client()
        store = DynamoDbPortfolioDocumentStore(client, DynamoDbLocal.createDocumentsTable(client))
    }

    @Test
    fun `FinOps ingestion is idempotent and pages entries and rollups`() {
        val service = FinOpsService(FirestoreFinOpsRepository(store, shardedRollupWritesEnabled = true, shardedRollupsReady = true))
        val day = 1_792_000_000_000L - (1_792_000_000_000L % 86_400_000L)
        val entries = (0 until 7).map { index -> entry("vendor:$index", day + index * 3_600_000L, 1_000_000L * (index + 1)) }
        entries.forEach { entry ->
            val allocation = CostAllocation(
                id = "allocation:${entry.id}",
                entryId = entry.id,
                project = "samurai",
                userId = "user-1",
                allocatedUsdMicros = entry.usdMicros / 2,
                method = AllocationMethod.DIRECT,
            )
            assertEquals("inserted", service.ingest(FinOpsIngestRequest(entry, listOf(allocation)), "test").state)
            // A replay (queue redelivery) is a duplicate and does not double count rollups.
            assertEquals("duplicate", service.ingest(FinOpsIngestRequest(entry.copy(recordedAt = 9), listOf(allocation)), "test").state)
        }
        val query = FinOpsQuery(from = day, toExclusive = day + 86_400_000L, limit = 3, order = FinOpsEntryOrder.OLDEST)
        val ids = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = service.entries(query.copy(cursor = cursor))
            ids += page.entries.map { it.id }
            cursor = page.nextCursor
        } while (cursor != null)
        assertEquals(entries.map { it.id }, ids)

        val summary = service.summary(FinOpsQuery(from = day, toExclusive = day + 86_400_000L))
        assertEquals((1..7).sumOf { it * 1_000_000L }, summary.finalizedSpendUsdMicros)
        val users = service.samuraiUsers(FinOpsQuery(from = day, toExclusive = day + 86_400_000L))
        assertTrue(users.toString().contains("user-1"))
        assertTrue(service.reconcileAllocationEntryProjections("test").ready)
    }

    @Test
    fun `content store shares the Firestore document shapes`() {
        val content = DocumentContentStore(store, seedOnInit = true)
        assertEquals(PortfolioContentSeed.projects.sortedBy { it.order }, content.listProjects())
        assertEquals(PortfolioContentSeed.hero, content.getHero())
        val photo = PhotographyPhoto(
            id = "p1",
            title = "T",
            altText = "A",
            caption = null,
            takenAt = LocalDate.parse("2026-01-02"),
            order = 1,
            published = true,
            storageKey = "sha256/${"a".repeat(64)}/p1.jpg",
            contentType = "image/jpeg",
            originalFilename = "x.jpg",
            sizeBytes = 10,
            mediaType = PhotographyMediaType.PHOTO,
            sourceKind = PhotographySourceKind.UPLOAD,
            category = "C",
            albumTitle = null,
            externalUrl = null,
            thumbnailUrl = null,
            featured = false,
            uploadedAt = Instant.parse("2026-01-03T00:00:00Z"),
        )
        content.upsertPhotographyPhoto(photo)
        assertEquals(listOf(photo), content.listPhotographyPhotos())
        val submission = ContactSubmission("c1", "me@example.com", "hi", Instant.parse("2026-01-01T00:00:00Z"))
        content.upsertContactSubmission(submission)
        assertEquals(listOf(submission), content.listContactSubmissions())
        assertEquals(content.listProjects().size, content.loadPortfolioContent().projects.size)
        content.deletePhotographyPhoto("p1")
        assertTrue(content.listPhotographyPhotos().isEmpty())
    }

    @Test
    fun `AI progress and Fifth Wall telemetry persist in the documents table`() = runBlocking {
        val progress = FirestoreAiProgressStore(store)
        progress.updateProgress("intro.1", true)
        progress.updateProgress("intro.2", false)
        assertEquals(mapOf("intro.1" to true, "intro.2" to false), progress.getProgress())

        val telemetry = DynamoDbFifthWallTelemetryStore(store, maxEventsPerSession = 3)
        val sessionId = "6f1c2a7e-1b2c-4d5e-8f90-123456789abc"
        telemetry.merge(payload(sessionId, revision = 2, events = listOf(event("e1", 1), event("e2", 2))))
        telemetry.merge(payload(sessionId, revision = 1, events = listOf(event("e2", 2), event("e3", 3), event("e4", 4))))
        val document = requireNotNull(store.get(DynamoDbFifthWallTelemetryStore.COLLECTION, sessionId)).data
        assertEquals(2L, document["lastRevision"])
        val record = document["record"] as String
        assertTrue(record.contains("\"e4\"") && !record.contains("\"e1\""), "keeps the newest bounded events")
        assertFailsWith<IllegalArgumentException> { telemetry.merge(payload("../bad", 1, emptyList())) }
        Unit
    }

    private fun event(id: String, at: Long) = FifthWallTelemetryEvent(id, "type", 1, at)

    private fun payload(sessionId: String, revision: Int, events: List<FifthWallTelemetryEvent>) = FifthWallTelemetryPayload(
        sessionId = sessionId,
        revision = revision,
        events = events,
        summary = FifthWallTelemetrySummary(
            visibleScore = 1,
            completedLevels = 1,
            sessionDurationMs = 10,
            generatedAtMs = 100,
            metrics = FifthWallHiddenMetrics(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5),
        ),
    )

    private fun entry(id: String, incurredAt: Long, usdMicros: Long) = FinOpsEntry(
        id = id,
        source = "vendor",
        sourceRecordId = "source:$id",
        direction = FinOpsDirection.EXPENSE,
        amount = MoneyAmount("USD", usdMicros, 6),
        usdMicros = usdMicros,
        incurredAt = incurredAt,
        invoiceMonth = "2026-10",
        project = "samurai",
        environment = "dev",
        vendor = "vendor",
        service = "api",
        status = FinOpsStatus.FINALIZED,
        reconciliationKey = "vendor:2026-10",
        sourceHash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) },
        recordedAt = 1,
    )
}
