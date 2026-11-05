package code.yousef.portfolio.server

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PortfolioEdgeJobRoutesTest {
    @Test
    fun `canonical edge JSON matches sorted worker payload hashing`() {
        val payload = Json.parseToJsonElement("""{"z":1,"nested":{"b":true,"a":"value"},"items":[2,null]}""")
        assertEquals(
            """{"items":[2,null],"nested":{"a":"value","b":true},"z":1}""",
            canonicalEdgeJson(payload),
        )
    }

    @Test
    fun `photography payload accepts bounded approved legacy inventory`() {
        val payload = Json.parseToJsonElement(
            """{"assets":[{"photoId":"photo-1","sourceStorageKey":"photography/portfolio-dev/photo-1.jpg","contentType":"image/jpeg","expectedSha256":"${"a".repeat(64)}","expectedSizeBytes":114808}]}""",
        )

        val decoded = assertNotNull(decodeAndValidatePhotographyBackfillPayload(payload, 15_728_640))

        assertEquals("photo-1", decoded.assets.single().photoId)
    }

    @Test
    fun `photography payload rejects export keys duplicate ids and invalid inventory`() {
        fun payload(assets: String) = Json.parseToJsonElement("""{"assets":[$assets]}""")
        val valid = """{"photoId":"photo-1","sourceStorageKey":"photography/portfolio-dev/photo-1.jpg","contentType":"image/jpeg","expectedSha256":"${"a".repeat(64)}","expectedSizeBytes":10}"""

        assertNull(
            decodeAndValidatePhotographyBackfillPayload(
                payload(valid.replace("photography/portfolio-dev/photo-1.jpg", "firestore-export/shard-0")),
                100,
            ),
        )
        assertNull(decodeAndValidatePhotographyBackfillPayload(payload("$valid,$valid"), 100))
        assertNull(
            decodeAndValidatePhotographyBackfillPayload(
                payload(valid.replace("a".repeat(64), "a".repeat(63))),
                100,
            ),
        )
        assertNull(
            decodeAndValidatePhotographyBackfillPayload(
                payload(valid.replace("\"expectedSizeBytes\":10", "\"expectedSizeBytes\":101")),
                100,
            ),
        )
    }

    @Test
    fun `photography payload rejects oversized batches`() {
        val assets = (1..26).joinToString(",") { index ->
            """{"photoId":"photo-$index","sourceStorageKey":"photography/portfolio-dev/photo-$index.jpg","contentType":"image/jpeg","expectedSha256":"${"a".repeat(64)}","expectedSizeBytes":10}"""
        }

        assertNull(
            decodeAndValidatePhotographyBackfillPayload(
                Json.parseToJsonElement("""{"assets":[$assets]}"""),
                100,
            ),
        )
    }
}
