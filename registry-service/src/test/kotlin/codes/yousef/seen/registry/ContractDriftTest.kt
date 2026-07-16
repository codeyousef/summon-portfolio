package codes.yousef.seen.registry

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContractDriftTest {
    private val root = Path.of("contracts/package-registry/v1")

    @Test
    fun `copied FEL-632 contract remains byte exact and public operations stay wired`() {
        val source = RegistryJson.parseToJsonElement(Files.readString(root.resolve("SOURCE.json"))).jsonObject
        assertEquals("44b14e7e7b08de59fbeb1b7ba62dc07c5f08eb1e", source["commit"]!!.jsonPrimitive.content)

        val hashes = source["files"]!!.jsonObject
        hashes.forEach { (relative, expected) ->
            assertEquals(expected.jsonPrimitive.content, sha256(Files.readAllBytes(root.resolve(relative))), relative)
        }

        val fixture = RegistryJson.parseToJsonElement(
            Files.readString(root.resolve("fixtures/registry-api-conformance-v1.json")),
        ).jsonObject
        assertEquals("1", fixture["contract_version"]!!.jsonPrimitive.content)
        assertEquals("../openapi.yaml", fixture["openapi"]!!.jsonPrimitive.content)
        assertEquals("../schemas/registry-api-workflows-v1.schema.json", fixture["workflow_schema"]!!.jsonPrimitive.content)

        val operations = fixture["cases"]!!.jsonArray.map {
            it.jsonObject["operation_id"]!!.jsonPrimitive.content
        }.toSet()
        assertTrue("getSignedMetadata" in operations)
        assertTrue("getArchiveBlob" in operations)

        val openApi = Files.readString(root.resolve("openapi.yaml"))
        assertTrue(openApi.contains("/packages/api/v1/metadata/{metadata_filename}:"))
        assertTrue(openApi.contains("/packages/api/v1/blobs/sha256/{archive_sha256}:"))
    }
}
