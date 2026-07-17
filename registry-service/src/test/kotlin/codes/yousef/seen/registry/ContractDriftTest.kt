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
    fun `copied registry contract remains byte exact and public operations stay wired`() {
        val source = RegistryJson.parseToJsonElement(Files.readString(root.resolve("SOURCE.json"))).jsonObject
        assertTrue(Regex("^[0-9a-f]{40}$").matches(source["commit"]!!.jsonPrimitive.content))

        val hashes = source["files"]!!.jsonObject
        assertEquals(
            setOf(
                "README.md",
                "openapi.yaml",
                "fixtures/archive-policy-cases.json",
                "fixtures/release-transitions.json",
                "fixtures/registry-api-conformance-v1.json",
                "fixtures/scan-attestation-failure-cases-v1.json",
                "fixtures/scan-attestation-v1.json",
                "fixtures/source-proof-failure-cases.json",
                "fixtures/source-proof-v1.json",
                "schemas/registry-api-workflows-v1.schema.json",
                "schemas/scan-attestation-v1.schema.json",
                "schemas/source-proof.schema.json",
            ),
            hashes.keys,
        )
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
        assertTrue(openApi.contains("ScanAttestation:"))
        assertTrue(openApi.contains("./schemas/scan-attestation-v1.schema.json"))
    }

    @Test
    fun `hardened review corpus stays fail closed and input bound`() {
        val readme = Files.readString(root.resolve("README.md"))
        assertTrue(readme.contains("scan-attestation"))
        assertTrue(readme.contains("exactly 259,200 seconds"))
        assertTrue(readme.contains("compare-and-swap state revision"))

        val scanSchema = json("schemas/scan-attestation-v1.schema.json")
        val scanRules = scanSchema["x-seen-semantic-rules"]!!.jsonObject["rules"]!!.jsonArray
        val scanErrors = scanRules.map { it.jsonObject["error_code"]!!.jsonPrimitive.content }.toSet()
        assertTrue("scan_archive_digest_mismatch" in scanErrors)
        assertTrue("scan_source_proof_digest_mismatch" in scanErrors)
        assertTrue("scan_result_not_passed" in scanErrors)

        val scan = json("fixtures/scan-attestation-v1.json")
        val scanner = scan["scanner"]!!.jsonObject
        assertEquals("true", scanner["isolated"]!!.jsonPrimitive.content)
        assertEquals("none", scanner["network_access"]!!.jsonPrimitive.content)
        assertEquals("none", scanner["secret_access"]!!.jsonPrimitive.content)
        assertEquals("read-only", scanner["input_access"]!!.jsonPrimitive.content)

        val subject = scan["subject"]!!.jsonObject
        val input = scan["input"]!!.jsonObject
        val result = scan["result"]!!.jsonObject
        assertEquals(subject["archive_sha256"], input["archive_sha256"])
        assertEquals(subject["archive_sha256"], result["observed_archive_sha256"])
        assertEquals(subject["source_proof_sha256"], input["source_proof_sha256"])
        assertEquals(subject["source_proof_sha256"], result["observed_source_proof_sha256"])
        assertEquals("passed", result["status"]!!.jsonPrimitive.content)
        assertEquals("promotion-eligible", result["disposition"]!!.jsonPrimitive.content)

        val scanFailures = json("fixtures/scan-attestation-failure-cases-v1.json")["cases"]!!.jsonArray
        val scanFailureNames = scanFailures.map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
        assertEquals(
            setOf(
                "scanner-crash",
                "scanner-timeout",
                "scan-result-missing",
                "observed-archive-digest-missing",
                "observed-source-proof-digest-missing",
                "observed-archive-digest-inconsistent",
                "observed-source-proof-digest-inconsistent",
            ),
            scanFailureNames,
        )
        scanFailures.forEach { element ->
            val case = element.jsonObject
            assertEquals("retry-unavailable", case["expected"]!!.jsonPrimitive.content)
            assertEquals("temporarily_unavailable", case["public_error_code"]!!.jsonPrimitive.content)
            assertEquals("delayed", case["next_lifecycle"]!!.jsonPrimitive.content)
            assertEquals("false", case["publicly_visible"]!!.jsonPrimitive.content)
            assertTrue(case["validation"]!!.jsonPrimitive.content in setOf("schema-reject", "semantic-reject"))
        }

        val proofSchema = json("schemas/source-proof.schema.json")
        val proofErrors = proofSchema["x-seen-semantic-rules"]!!.jsonObject["rules"]!!.jsonArray
            .map { it.jsonObject["error_code"]!!.jsonPrimitive.content }
            .toSet()
        assertTrue("source_proof_mutable_ref_changed" in proofErrors)
        assertTrue("source_proof_archive_digest_mismatch" in proofErrors)
        val proofFailures = json("fixtures/source-proof-failure-cases.json")["invalid_cases"]!!.jsonArray
        assertTrue(
            proofFailures.any {
                val case = it.jsonObject
                case["expected"]!!.jsonPrimitive.content == "semantic-reject" &&
                    case["error_code"]!!.jsonPrimitive.content == "source_proof_mutable_ref_changed"
            },
        )

        val transitions = json("fixtures/release-transitions.json")
        val protocol = transitions["promotion_protocol"]!!.jsonObject
        assertEquals("259200", protocol["public_delay_seconds"]!!.jsonPrimitive.content)
        assertEquals("elapsed-greater-than-or-equal", protocol["delay_comparison"]!!.jsonPrimitive.content)
        assertEquals("compare-and-swap-on-revision", protocol["state_write"]!!.jsonPrimitive.content)
        assertEquals(
            setOf("archive_sha256", "source_proof_id", "source_proof_sha256"),
            protocol["reviewed_input_fields"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
        )
        val promotionCases = transitions["promotion_trace_cases"]!!.jsonArray
        val promotionNames = promotionCases.map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
        listOf(
            "public-delay-exact-72-hour-boundary",
            "public-delay-one-second-before-boundary",
            "double-promotion-is-one-idempotent-write",
            "concurrent-promotion-state-race-uses-cas",
            "promotion-rejects-unreviewed-archive",
            "promotion-rejects-unreviewed-source-proof",
        ).forEach { assertTrue(it in promotionNames, it) }
        val doublePromotion = promotionCases.first {
            it.jsonObject["name"]!!.jsonPrimitive.content == "double-promotion-is-one-idempotent-write"
        }.jsonObject
        assertEquals("1", doublePromotion["final"]!!.jsonObject["promotion_count"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("applied", "replayed"),
            doublePromotion["operations"]!!.jsonArray.map { it.jsonObject["expected"]!!.jsonPrimitive.content },
        )

        val archiveCases = json("fixtures/archive-policy-cases.json")["cases"]!!.jsonArray
        val forbiddenEntryTypes = archiveCases
            .filter { it.jsonObject["expected"]!!.jsonPrimitive.content == "reject" }
            .flatMap { case ->
                case.jsonObject["archive"]!!.jsonObject["entries"]!!.jsonArray.map {
                    it.jsonObject["type"]!!.jsonPrimitive.content
                }
            }
            .toSet()
        assertTrue(forbiddenEntryTypes.containsAll(setOf("fifo", "socket", "sparse-file", "unknown")))
    }

    private fun json(relative: String) =
        RegistryJson.parseToJsonElement(Files.readString(root.resolve(relative))).jsonObject
}
