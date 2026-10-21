package code.yousef.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwsAppConfigTest {
    private val aws = mapOf(
        "PORTFOLIO_DATA_BACKEND" to "dynamodb",
        "PORTFOLIO_DYNAMODB_DOCUMENTS_TABLE" to "portfolio-documents",
        "PHOTOGRAPHY_WRITE_MODE" to "target",
        "PORTFOLIO_S3_MEDIA_BUCKET" to "portfolio-media",
        "PORTFOLIO_S3_RECEIPTS_BUCKET" to "portfolio-receipts",
        "PORTFOLIO_SQS_JOBS_QUEUE_URL" to "https://sqs.eu-north-1.amazonaws.com/123456789012/portfolio-jobs",
    )

    @Test
    fun `defaults keep the Firestore backend`() {
        val config = loadAppConfig(mapOf("GOOGLE_CLOUD_PROJECT" to "p"))
        assertEquals(PortfolioDataBackend.FIRESTORE, config.dataBackend)
        assertNull(config.s3MediaBucket)
        assertFalse(config.cdnProxyEnabled)
        assertEquals(PortfolioDataBackend.LOCAL, loadAppConfig(mapOf("USE_LOCAL_STORE" to "true")).dataBackend)
        assertEquals(PortfolioDataBackend.LOCAL, loadAppConfig(mapOf("PORTFOLIO_DATA_BACKEND" to "local")).dataBackend)
        assertTrue(loadAppConfig(mapOf("PORTFOLIO_DATA_BACKEND" to "local")).useLocalStore)
    }

    @Test
    fun `AWS mode needs no Google project, edge token or R2 endpoint`() {
        val config = loadAppConfig(aws)
        assertEquals(PortfolioDataBackend.DYNAMODB, config.dataBackend)
        assertEquals("portfolio-documents", config.dynamoDbDocumentsTable)
        assertEquals(PhotographyAssetWriteMode.TARGET, config.photographyWriteMode)
        assertEquals(300, config.sqsJobsVisibilityTimeoutSeconds)
        assertFalse(config.useLocalStore)
    }

    @Test
    fun `AWS mode rejects ambiguous or incomplete configuration`() {
        assertFailsWith<IllegalArgumentException> { loadAppConfig(aws - "PORTFOLIO_DYNAMODB_DOCUMENTS_TABLE") }
        assertFailsWith<IllegalArgumentException> { loadAppConfig(aws + ("PORTFOLIO_DATA_BACKEND" to "mongo")) }
        assertFailsWith<IllegalArgumentException> { loadAppConfig(aws + ("USE_LOCAL_STORE" to "true")) }
        assertFailsWith<IllegalArgumentException> { loadAppConfig(aws + ("PHOTOGRAPHY_R2_BASE_URL" to "https://edge.example")) }
        assertFailsWith<IllegalArgumentException> { loadAppConfig(aws + ("FINOPS_RECEIPT_BASE_URL" to "https://edge.example/r")) }
        assertFailsWith<IllegalArgumentException> { loadAppConfig(aws + ("PORTFOLIO_SQS_JOBS_QUEUE_URL" to "ftp://queue")) }
        assertFailsWith<IllegalArgumentException> { loadAppConfig(aws + ("PORTFOLIO_SQS_JOBS_VISIBILITY_TIMEOUT_SECONDS" to "5")) }
        assertFailsWith<IllegalArgumentException> { loadAppConfig(aws + ("PORTFOLIO_CDN_PROXY_ENABLED" to "yes")) }
        assertFailsWith<IllegalArgumentException> {
            loadAppConfig(aws - "PORTFOLIO_S3_MEDIA_BUCKET" + ("PHOTOGRAPHY_WRITE_MODE" to "source") + ("PORTFOLIO_CDN_PROXY_ENABLED" to "true"))
        }
    }

    @Test
    fun `dev CDN proxy is opt-in`() {
        val config = loadAppConfig(aws + ("PORTFOLIO_CDN_PROXY_ENABLED" to "true") + ("PORTFOLIO_S3_DOCS_BUCKET" to "portfolio-docs"))
        assertTrue(config.cdnProxyEnabled)
        assertEquals("portfolio-docs", config.s3DocsBucket)
    }
}
