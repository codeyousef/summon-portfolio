package code.yousef.portfolio.finops

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SamuraiIdentityResolverTest {
    @Test
    fun `batches identity lookups and sends the scoped bearer credential`() {
        val requests = mutableListOf<List<String>>()
        val resolver = HttpSamuraiIdentityResolver(
            endpoint = "https://samurai.example/internal/portfolio/finops/identities",
            bearerToken = TOKEN,
            accessClientId = ACCESS_CLIENT_ID,
            accessClientSecret = ACCESS_CLIENT_SECRET,
            transport = SamuraiIdentityProjectionTransport { request ->
                assertEquals(TOKEN, request.bearerToken)
                assertEquals(ACCESS_CLIENT_ID, request.accessClientId)
                assertEquals(ACCESS_CLIENT_SECRET, request.accessClientSecret)
                val ids = URLDecoder.decode(request.uri.rawQuery.removePrefix("ids="), StandardCharsets.UTF_8).split(',')
                requests += ids
                ids.joinToString(prefix = "[", postfix = "]") { id ->
                    "{\"userId\":\"$id\",\"displayName\":\"Name $id\",\"email\":null}"
                }.encodeToByteArray()
            },
        )

        val identities = resolver.resolve((1..205).mapTo(linkedSetOf()) { "user-$it" })

        assertEquals(listOf(100, 100, 5), requests.map(List<String>::size))
        assertEquals(205, identities.size)
        assertEquals("Name user-42", identities.getValue("user-42").displayName)
    }

    @Test
    fun `rejects unrequested principals duplicate identities and unsafe endpoints`() {
        assertFailsWith<IllegalArgumentException> {
            HttpSamuraiIdentityResolver("https://samurai.example/other", TOKEN)
        }
        assertFailsWith<IllegalArgumentException> {
            HttpSamuraiIdentityResolver(
                "https://samurai.example/internal/portfolio/finops/identities",
                TOKEN,
                transport = SamuraiIdentityProjectionTransport {
                    "[{\"userId\":\"another-user\"}]".encodeToByteArray()
                },
            ).resolve(setOf("user-1"))
        }
        assertFailsWith<IllegalArgumentException> {
            HttpSamuraiIdentityResolver(
                "https://samurai.example/internal/portfolio/finops/identities",
                TOKEN,
                transport = SamuraiIdentityProjectionTransport {
                    "[{\"userId\":\"user-1\"},{\"userId\":\"user-1\"}]".encodeToByteArray()
                },
            ).resolve(setOf("user-1"))
        }
    }

    companion object {
        private const val TOKEN = "identity-read-token-0000000000000000000000000000"
        private const val ACCESS_CLIENT_ID = "0123456789abcdef0123456789abcdef.access"
        private const val ACCESS_CLIENT_SECRET = "access-secret-00000000000000000000000000000000"
    }
}
