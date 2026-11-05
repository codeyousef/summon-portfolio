package code.yousef.firestore

import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.migration.PortfolioFirestoreMigrationCollections
import code.yousef.firestore.migration.FirestoreCanonicalHash
import code.yousef.portfolio.admin.auth.FirestoreAdminAuthService
import code.yousef.portfolio.ai.FirestoreAiProgressStore
import code.yousef.portfolio.building.auth.BuildingAuthProvider
import code.yousef.portfolio.building.auth.PasswordResetService
import code.yousef.portfolio.building.model.Building
import code.yousef.portfolio.building.repo.BuildingRepository
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationAwarePortfolioStoresTest {
    @Test
    fun `all non-content collections are in the parity contract`() {
        assertEquals(
            setOf(
                "portfolio_meta",
                "admin_settings",
                "building_users",
                "building_password_reset_tokens",
                "buildings",
                "building_units",
                "building_tenants",
                "building_leases",
                "building_payments",
                "ai_curriculum",
                "finops_cost_entries",
                "finops_allocations",
                "finops_allocation_entry_projections",
                "finops_receipt_attachments",
                "finops_daily_rollups",
                "finops_daily_rollups_v2",
                "finops_user_daily_rollups",
                "finops_user_daily_rollups_v2",
                "finops_samurai_user_index",
                "finops_import_runs",
                "finops_reconciliations",
                "finops_reconciliations_v2",
                "finops_budgets",
                "finops_fx_rates",
                "finops_recurring_expenses",
                "finops_audit",
            ),
            PortfolioFirestoreCollections.all,
        )
    }

    @Test
    fun `startup parity contract includes content but excludes externally proven migration metadata`() {
        val startupCollections = PortfolioFirestoreMigrationCollections.all -
            PortfolioFirestoreMigrationCollections.migrationMetadata

        assertTrue(startupCollections.containsAll(PortfolioFirestoreMigrationCollections.content))
        assertTrue(startupCollections.containsAll(PortfolioFirestoreCollections.all))
        assertTrue(startupCollections.intersect(PortfolioFirestoreMigrationCollections.migrationMetadata).isEmpty())
    }

    @Test
    fun `parity comparison detects missing extra and changed documents without values`() {
        val difference = PortfolioFirestoreMigrationGate.compareCollection(
            collection = PortfolioFirestoreCollections.ADMIN_SETTINGS,
            source = mapOf(
                "source-only" to mapOf("secret" to "source"),
                "changed" to mapOf("revision" to 1L),
            ),
            target = mapOf(
                "target-only" to mapOf("secret" to "target"),
                "changed" to mapOf("revision" to 2L),
            ),
        )

        assertEquals(setOf("source-only"), difference.sourceOnlyDocumentIds)
        assertEquals(setOf("target-only"), difference.targetOnlyDocumentIds)
        assertEquals(setOf("changed"), difference.differingDocumentIds)
        assertFalse(difference.isEmpty)
    }

    @Test
    fun `migration proof requires identical exact-boundary count and hash evidence`() {
        val proof = mapOf<String, Any?>(
            "schemaVersion" to 1L,
            "proofId" to "portfolio-me-dev-initial-v1",
            "sourceProject" to "portfolio-476219",
            "sourceDatabase" to "(default)",
            "targetProject" to "portfolio-476219",
            "targetDatabase" to "portfolio-me-dev",
            "sourceCount" to 107_734L,
            "targetCount" to 107_734L,
            "sourceOnlyCount" to 0L,
            "targetOnlyCount" to 0L,
            "differingCount" to 0L,
            "collectionContractHash" to FirestoreCanonicalHash.collectionContract(),
            "parityHash" to "a".repeat(64),
            "verifiedAt" to "2026-08-22T19:00:00Z",
            "ready" to true,
        )

        verifyPortfolioMigrationProof(
            proofId = "portfolio-me-dev-initial-v1",
            sourceProject = "portfolio-476219",
            sourceDatabase = "(default)",
            targetProject = "portfolio-476219",
            targetDatabase = "portfolio-me-dev",
            sourceProof = proof,
            targetProof = proof.toMap(),
            maximumAge = Duration.ofDays(7),
            now = Instant.parse("2026-08-22T20:00:00Z"),
        )

        assertFailsWith<IllegalStateException> {
            verifyPortfolioMigrationProof(
                proofId = "portfolio-me-dev-initial-v1",
                sourceProject = "portfolio-476219",
                sourceDatabase = "(default)",
                targetProject = "portfolio-476219",
                targetDatabase = "portfolio-me-dev",
                sourceProof = proof,
                targetProof = proof + ("targetCount" to 107_733L),
                maximumAge = Duration.ofDays(7),
                now = Instant.parse("2026-08-22T20:00:00Z"),
            )
        }
    }

    @Test
    fun `migration proof age is bounded more tightly for target cutover`() {
        val proof = mapOf<String, Any?>(
            "schemaVersion" to 1L,
            "proofId" to "portfolio-me-dev-dual-v2",
            "sourceProject" to "portfolio-476219",
            "sourceDatabase" to "(default)",
            "targetProject" to "portfolio-476219",
            "targetDatabase" to "portfolio-me-dev",
            "sourceCount" to 107_734L,
            "targetCount" to 107_734L,
            "sourceOnlyCount" to 0L,
            "targetOnlyCount" to 0L,
            "differingCount" to 0L,
            "collectionContractHash" to FirestoreCanonicalHash.collectionContract(),
            "parityHash" to "b".repeat(64),
            "verifiedAt" to "2026-08-21T18:00:00Z",
            "ready" to true,
        )

        verifyPortfolioMigrationProof(
            proofId = "portfolio-me-dev-dual-v2",
            sourceProject = "portfolio-476219",
            sourceDatabase = "(default)",
            targetProject = "portfolio-476219",
            targetDatabase = "portfolio-me-dev",
            sourceProof = proof,
            targetProof = proof,
            maximumAge = PortfolioFirestoreMigrationGate.DUAL_PROOF_MAX_AGE,
            now = Instant.parse("2026-08-22T20:00:00Z"),
        )
        assertFailsWith<IllegalStateException> {
            verifyPortfolioMigrationProof(
                proofId = "portfolio-me-dev-dual-v2",
                sourceProject = "portfolio-476219",
                sourceDatabase = "(default)",
                targetProject = "portfolio-476219",
                targetDatabase = "portfolio-me-dev",
                sourceProof = proof,
                targetProof = proof,
                maximumAge = PortfolioFirestoreMigrationGate.TARGET_PROOF_MAX_AGE,
                now = Instant.parse("2026-08-22T20:00:00Z"),
            )
        }
        val futureProof = proof + ("verifiedAt" to "2026-08-22T20:06:00Z")
        assertFailsWith<IllegalStateException> {
            verifyPortfolioMigrationProof(
                proofId = "portfolio-me-dev-dual-v2",
                sourceProject = "portfolio-476219",
                sourceDatabase = "(default)",
                targetProject = "portfolio-476219",
                targetDatabase = "portfolio-me-dev",
                sourceProof = futureProof,
                targetProof = futureProof,
                maximumAge = PortfolioFirestoreMigrationGate.DUAL_PROOF_MAX_AGE,
                now = Instant.parse("2026-08-22T20:00:00Z"),
            )
        }
    }

    @Test
    fun `admin credentials bootstrap only in source and failed persistence does not change cache`() {
        val source = FakePortfolioFirestoreStore(FirestoreWriteMode.SOURCE)
        val missingCredentials = FirestoreAdminAuthService(source)
        assertNull(source.get(PortfolioFirestoreCollections.ADMIN_SETTINGS, "credentials"))
        assertFailsWith<IllegalStateException> {
            missingCredentials.currentUsername()
        }
        assertNull(source.get(PortfolioFirestoreCollections.ADMIN_SETTINGS, "credentials"))

        val bootstrapped = FirestoreAdminAuthService(source, allowBootstrapCredentials = true)
        assertEquals("admin", bootstrapped.currentUsername())
        assertNotNull(source.get(PortfolioFirestoreCollections.ADMIN_SETTINGS, "credentials"))

        source.failWrites = true
        assertFailsWith<IllegalStateException> {
            bootstrapped.updateCredentials("replacement", "new-password")
        }
        assertEquals("admin", bootstrapped.currentUsername())

        val dual = FirestoreAdminAuthService(
            FakePortfolioFirestoreStore(FirestoreWriteMode.DUAL),
            allowBootstrapCredentials = true,
        )
        assertFailsWith<IllegalStateException> {
            dual.currentUsername()
        }
    }

    @Test
    fun `building auth and password reset mutations use the migration store`() {
        val store = FakePortfolioFirestoreStore(FirestoreWriteMode.SOURCE)
        BuildingAuthProvider(store)
        assertNull(store.get(PortfolioFirestoreCollections.BUILDING_USERS, "waleed"))

        val auth = BuildingAuthProvider(store, seedOnInit = true)
        val resets = PasswordResetService(store, auth)

        val first = resets.createResetToken("waleed")
        assertNotNull(first)
        assertEquals("waleed", resets.validateToken(first))

        val second = resets.createResetToken("waleed")
        assertNotNull(second)
        assertNull(resets.validateToken(first))
        assertEquals("waleed", resets.validateToken(second))

        resets.consumeToken(second)
        assertNull(resets.validateToken(second))
        assertTrue(
            store.operations.any {
                it.kind == "merge" &&
                    it.collection == PortfolioFirestoreCollections.BUILDING_PASSWORD_RESET_TOKENS
            },
        )
    }

    @Test
    fun `building meta and AI progress writes cannot bypass migration boundary`() = runBlocking {
        val store = FakePortfolioFirestoreStore(FirestoreWriteMode.TARGET)
        val buildings = BuildingRepository(store)
        val meta = PortfolioMetaRepository(store)
        val progress = FirestoreAiProgressStore(store)

        buildings.upsertBuilding(Building("building-1", "Tower", "Riyadh", 42L))
        assertEquals("Tower", buildings.getBuilding("building-1")?.name)

        meta.putNow("hello", mapOf("updatedAt" to 42L))
        assertEquals(42L, meta.getNow("hello")?.get("updatedAt"))

        progress.updateProgress("lesson-a", true)
        progress.updateProgress("lesson-b", false)
        assertEquals(mapOf("lesson-a" to true, "lesson-b" to false), progress.getProgress())

        val writtenCollections = store.operations.map(Operation::collection).toSet()
        assertTrue(PortfolioFirestoreCollections.BUILDINGS in writtenCollections)
        assertTrue(PortfolioFirestoreCollections.META in writtenCollections)
        assertTrue(PortfolioFirestoreCollections.AI_CURRICULUM in writtenCollections)
        assertTrue(store.operations.any { it.kind == "merge" && it.collection == PortfolioFirestoreCollections.AI_CURRICULUM })
    }

    private data class Operation(
        val kind: String,
        val collection: String,
        val documentId: String,
    )

    private class FakePortfolioFirestoreStore(
        override val writeMode: FirestoreWriteMode,
    ) : PortfolioFirestoreStore {
        private val documents = linkedMapOf<String, LinkedHashMap<String, MutableMap<String, Any?>>>()
        val operations = mutableListOf<Operation>()
        var failWrites: Boolean = false

        override fun get(collection: String, documentId: String): PortfolioFirestoreDocument? =
            documents[collection]?.get(documentId)?.let { data ->
                PortfolioFirestoreDocument(documentId, data.toMap())
            }

        override fun list(collection: String): List<PortfolioFirestoreDocument> =
            documents[collection].orEmpty().map { (id, data) ->
                PortfolioFirestoreDocument(id, data.toMap())
            }

        override fun whereEqualTo(
            collection: String,
            filters: Map<String, Any>,
        ): List<PortfolioFirestoreDocument> = list(collection).filter { document ->
            filters.all { (field, expected) -> document.data[field] == expected }
        }

        override fun whereLessThan(
            collection: String,
            field: String,
            value: Any,
        ): List<PortfolioFirestoreDocument> {
            val upperBound = (value as Number).toLong()
            return list(collection).filter { document ->
                ((document.data[field] as? Number)?.toLong() ?: Long.MAX_VALUE) < upperBound
            }
        }

        override fun upsert(collection: String, documentId: String, data: Map<String, Any?>) {
            ensureWritable()
            collection(collection)[documentId] = data.toMutableMap()
            operations += Operation("upsert", collection, documentId)
        }

        override fun merge(collection: String, documentId: String, data: Map<String, Any?>) {
            ensureWritable()
            collection(collection).getOrPut(documentId) { linkedMapOf() }.putAll(data)
            operations += Operation("merge", collection, documentId)
        }

        override fun delete(collection: String, documentId: String) {
            ensureWritable()
            collection(collection).remove(documentId)
            operations += Operation("delete", collection, documentId)
        }

        private fun collection(name: String): LinkedHashMap<String, MutableMap<String, Any?>> =
            documents.getOrPut(name) { linkedMapOf() }

        private fun ensureWritable() {
            if (failWrites) throw IllegalStateException("simulated write failure")
        }
    }
}
