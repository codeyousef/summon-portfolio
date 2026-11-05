package code.yousef.portfolio.session

import codes.yousef.aether.core.session.DefaultSession
import codes.yousef.aether.core.session.SerializableSession
import codes.yousef.aether.core.session.generateSecureSessionId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudflareDurableObjectSessionStoreTest {
    @Test
    fun `Aether generates session ids accepted by the edge contract`() {
        val config = portfolioSessionConfig()
        val sessionId = generateSecureSessionId(config.sessionIdLength)

        assertEquals("admin_session", config.cookieName)
        assertEquals(32, sessionId.length)
        assertTrue(sessionId.matches(Regex("^[A-Za-z0-9_-]{32}$")))
    }

    @Test
    fun `committed edge receipt matches the complete durable object response`() {
        val receipt = JSON.decodeFromString<EdgeSessionCommitted>(
            """{"revision":1,"expiresAt":1787591574791,"values":{"aether-session-v1":"payload"}}""",
        )

        assertEquals(1, receipt.revision)
        assertEquals(1_787_591_574_791, receipt.expiresAt)
        assertEquals("payload", receipt.values["aether-session-v1"])
    }

    @Test
    fun `factory remains local unless edge sessions are explicitly enabled`() {
        val resources = PortfolioSessionStoreFactory.fromEnvironment(emptyMap())
        assertEquals("InMemorySessionStore", resources.store::class.simpleName)
        resources.close()
    }

    @Test
    fun `factory fails closed when enabled without its authenticated endpoint`() {
        assertFailsWith<IllegalStateException> {
            PortfolioSessionStoreFactory.fromEnvironment(mapOf("PORTFOLIO_EDGE_SESSIONS_ENABLED" to "true"))
        }
    }

    @Test
    fun `missing remote session is not synthesized by the store`() = runBlocking {
        val transport = FakeTransport()
        val store = CloudflareDurableObjectSessionStore(transport)

        assertNull(store.get(SESSION_ID))
        assertFalse(store.exists(SESSION_ID))
    }

    @Test
    fun `conflicting writes merge only locally changed keys`() = runBlocking {
        val initial = serializable(mapOf("username" to JsonPrimitive("admin"), "theme" to JsonPrimitive("dark")))
        val concurrent = initial.copy(data = initial.data + ("locale" to JsonPrimitive("ar")))
        val transport = FakeTransport(initial, conflictReplacement = concurrent)
        val store = CloudflareDurableObjectSessionStore(transport)
        val session = store.get(SESSION_ID)!!

        session.set("theme", "light")
        store.save(session)

        val saved = requireNotNull(transport.session)
        assertEquals(JsonPrimitive("admin"), saved.data["username"])
        assertEquals(JsonPrimitive("light"), saved.data["theme"])
        assertEquals(JsonPrimitive("ar"), saved.data["locale"])
        assertEquals(3, transport.revision)
        assertEquals(2, transport.writeAttempts)
    }

    @Test
    fun `new invalidated session is deleted instead of written`() = runBlocking {
        val transport = FakeTransport()
        val store = CloudflareDurableObjectSessionStore(transport)
        val session = DefaultSession(SESSION_ID, System.currentTimeMillis()).apply { invalidate() }

        store.save(session)

        assertTrue(transport.deleted)
        assertEquals(0, transport.writeAttempts)
    }

    private class FakeTransport(
        initial: SerializableSession? = null,
        private val conflictReplacement: SerializableSession? = null,
    ) : EdgeSessionTransport {
        var session: SerializableSession? = initial
        var revision: Long = if (initial == null) 0 else 1
        var writeAttempts = 0
        var deleted = false
        private var conflictPending = conflictReplacement != null

        override suspend fun read(sessionId: String): EdgeSessionRead = EdgeSessionRead(
            exists = session != null,
            revision = revision,
            expiresAt = Long.MAX_VALUE,
            values = session?.let { mapOf("aether-session-v1" to JSON.encodeToString(SerializableSession.serializer(), it)) }
                ?: emptyMap(),
        )

        override suspend fun compareAndSet(
            sessionId: String,
            update: EdgeSessionWrite,
        ): EdgeSessionWriteResult {
            writeAttempts += 1
            if (conflictPending) {
                conflictPending = false
                session = conflictReplacement
                revision += 1
                return EdgeSessionWriteResult.Conflict(revision)
            }
            if (update.expectedRevision != revision) return EdgeSessionWriteResult.Conflict(revision)
            session = JSON.decodeFromString(
                SerializableSession.serializer(),
                requireNotNull(update.values["aether-session-v1"]),
            )
            revision += 1
            return EdgeSessionWriteResult.Committed(revision)
        }

        override suspend fun delete(sessionId: String) {
            deleted = true
            session = null
            revision = 0
        }
    }

    companion object {
        private const val SESSION_ID = "abcdefghijklmnopqrstuvwxyzABCDEF"
        private val JSON = Json { encodeDefaults = true }

        private fun serializable(data: Map<String, kotlinx.serialization.json.JsonElement>) = SerializableSession(
            id = SESSION_ID,
            createdAt = 1_000,
            lastAccessedAt = 2_000,
            data = data,
        )
    }
}
