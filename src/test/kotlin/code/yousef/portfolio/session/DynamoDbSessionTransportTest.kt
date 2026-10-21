package code.yousef.portfolio.session

import code.yousef.aws.DynamoDbLocal
import codes.yousef.aether.core.session.DefaultSession
import codes.yousef.aether.core.session.SerializableSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamoDbSessionTransportTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-21T00:00:00Z"))
    private lateinit var transport: DynamoDbSessionTransport

    @BeforeTest
    fun setUp() {
        val client = DynamoDbLocal.client()
        transport = DynamoDbSessionTransport(client, DynamoDbLocal.createSessionsTable(client), clock)
    }

    @Test
    fun `compare-and-set follows the Durable Object revision contract`() = runBlocking {
        val missing = transport.read(ID)
        assertFalse(missing.exists)
        assertEquals(0, missing.revision)

        assertEquals(EdgeSessionWriteResult.Committed(1), transport.compareAndSet(ID, EdgeSessionWrite(0, mapOf("k" to "v1"))))
        assertEquals(EdgeSessionWriteResult.Conflict(1), transport.compareAndSet(ID, EdgeSessionWrite(0, mapOf("k" to "x"))))
        assertEquals(EdgeSessionWriteResult.Conflict(1), transport.compareAndSet(ID, EdgeSessionWrite(5, mapOf("k" to "x"))))
        assertEquals(EdgeSessionWriteResult.Committed(2), transport.compareAndSet(ID, EdgeSessionWrite(1, mapOf("k" to "v2"))))

        val current = transport.read(ID)
        assertTrue(current.exists)
        assertEquals(2, current.revision)
        assertEquals(mapOf("k" to "v2"), current.values)
        assertEquals(clock.now.plusSeconds(86_400).toEpochMilli(), current.expiresAt)

        transport.delete(ID)
        assertFalse(transport.read(ID).exists)
    }

    @Test
    fun `expired sessions read as absent and can be recreated from revision zero`() = runBlocking {
        transport.compareAndSet(ID, EdgeSessionWrite(0, mapOf("k" to "v")))
        clock.now = clock.now.plusSeconds(86_401)
        assertFalse(transport.read(ID).exists)
        assertEquals(EdgeSessionWriteResult.Conflict(0), transport.compareAndSet(ID, EdgeSessionWrite(1, mapOf("k" to "late"))))
        assertEquals(EdgeSessionWriteResult.Committed(1), transport.compareAndSet(ID, EdgeSessionWrite(0, mapOf("k" to "new"))))
    }

    @Test
    fun `invalid ids and oversized sessions are rejected`() = runBlocking {
        assertFalse(transport.read("not-a-session").exists)
        assertFailsWith<IllegalArgumentException> { transport.compareAndSet("bad", EdgeSessionWrite(0, emptyMap())) }
        assertFailsWith<IllegalArgumentException> {
            transport.compareAndSet(ID, EdgeSessionWrite(0, mapOf("k" to "x".repeat(25 * 1024))))
        }
        Unit
    }

    @Test
    fun `Aether sessions round trip through the store over DynamoDB`() = runBlocking {
        val store = CloudflareDurableObjectSessionStore(transport)
        assertNull(store.get(ID))
        val session = DefaultSession.fromSerializable(
            SerializableSession(ID, createdAt = 1, lastAccessedAt = 2, data = mapOf("username" to JsonPrimitive("admin"))),
        )
        store.save(session)
        val loaded = assertNotNull(store.get(ID))
        assertEquals("admin", loaded.get("username"))
        loaded.set("theme", "dark")
        store.save(loaded)
        assertEquals("dark", assertNotNull(store.get(ID)).get("theme"))
        assertTrue(store.exists(ID))
        store.delete(ID)
        assertFalse(store.exists(ID))
    }

    @Test
    fun `factory selects DynamoDB sessions with secure cookies`() {
        val resources = PortfolioSessionStoreFactory.fromEnvironment(
            mapOf("PORTFOLIO_SESSION_BACKEND" to "dynamodb", "PORTFOLIO_DYNAMODB_SESSIONS_TABLE" to "sessions-table"),
            dynamoDbClient = { DynamoDbLocal.client() },
        )
        assertIs<CloudflareDurableObjectSessionStore>(resources.store)
        assertTrue(resources.secureCookie)
        assertTrue(portfolioSessionConfig(resources.secureCookie).secure)
        assertFailsWith<IllegalStateException> {
            PortfolioSessionStoreFactory.fromEnvironment(mapOf("PORTFOLIO_SESSION_BACKEND" to "dynamodb"))
        }
        assertFailsWith<IllegalArgumentException> {
            PortfolioSessionStoreFactory.fromEnvironment(
                mapOf("PORTFOLIO_SESSION_BACKEND" to "dynamodb", "PORTFOLIO_EDGE_SESSIONS_ENABLED" to "true"),
            )
        }
        assertFalse(PortfolioSessionStoreFactory.fromEnvironment(emptyMap()).secureCookie)
    }

    private companion object {
        const val ID = "abcdefghijklmnopqrstuvwxyzABCDEF"
    }
}
