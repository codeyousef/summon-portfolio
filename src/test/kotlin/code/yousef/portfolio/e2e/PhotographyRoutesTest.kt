package code.yousef.portfolio.e2e

import code.yousef.portfolio.admin.auth.AdminAuthProvider
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.contact.FileContactRepository
import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.model.PhotographyPhoto
import code.yousef.portfolio.content.store.FileContentStore
import code.yousef.portfolio.docs.MarkdownRenderer
import code.yousef.portfolio.photography.LocalPhotoAssetStore
import code.yousef.portfolio.photography.PhotographyService
import code.yousef.portfolio.server.portfolioRoutes
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.FifthWallRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.pipeline.Pipeline
import codes.yousef.aether.web.router
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotographyRoutesTest {

    @Test
    fun `photography page renders only published photos`() {
        val tempDir = Files.createTempDirectory("photography-routes")
        val contentStore = FileContentStore(tempDir.resolve("content.json"))
        val assetStore = LocalPhotoAssetStore(tempDir.resolve("uploads"))
        assetStore.save("published.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        contentStore.upsertPhotographyPhoto(
            PhotographyPhoto(
                id = "published",
                title = "Published Frame",
                altText = "Published alt",
                caption = "Visible caption",
                order = 1,
                published = true,
                storageKey = "published.jpg",
                contentType = "image/jpeg",
                sizeBytes = 3,
                uploadedAt = Instant.parse("2026-07-05T00:00:00Z")
            )
        )
        contentStore.upsertPhotographyPhoto(
            PhotographyPhoto(
                id = "draft",
                title = "Draft Frame",
                altText = "Draft alt",
                order = 2,
                published = false,
                storageKey = "draft.jpg",
                contentType = "image/jpeg",
                uploadedAt = Instant.parse("2026-07-05T00:00:01Z")
            )
        )

        val contentService = PortfolioContentService(contentStore)
        val photographyService = PhotographyService(contentStore, assetStore, maxUploadBytes = 100)
        val route = router {
            portfolioRoutes(
                portfolioRenderer = PortfolioRenderer(contentService),
                blogRenderer = BlogRenderer(contentService, MarkdownRenderer()),
                fifthWallRenderer = FifthWallRenderer(),
                contactService = ContactService(FileContactRepository(contentStore)),
                contentService = contentService,
                adminAuthService = NoopAdminAuthProvider,
                photographyService = photographyService
            )
        }
        val pipeline = Pipeline().apply {
            use(route.asMiddleware())
        }
        val port = 8092
        val server = VertxServer(VertxServerConfig(port = port), pipeline) { exchange ->
            exchange.notFound("Route not found")
        }

        runBlocking { server.start() }
        try {
            val client = HttpClient.newHttpClient()
            val page = client.send(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:$port/photography")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, page.statusCode())
            assertTrue(page.body().contains("Published Frame"))
            assertTrue(page.body().contains("Visible caption"))
            assertFalse(page.body().contains("Draft Frame"))

            val asset = client.send(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:$port/uploads/photography/published")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
            )
            assertEquals(200, asset.statusCode())
            assertEquals("image/jpeg", asset.headers().firstValue("Content-Type").orElse(""))

            val unpublishedAsset = client.send(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:$port/uploads/photography/draft")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(404, unpublishedAsset.statusCode())
        } finally {
            runBlocking { server.stop() }
        }
    }

    private object NoopAdminAuthProvider : AdminAuthProvider {
        override fun authenticate(username: String, password: String): AdminAuthProvider.AuthResult =
            AdminAuthProvider.AuthResult.Invalid

        override fun mustChangePassword(): Boolean = false

        override fun currentUsername(): String = "admin"

        override fun updateCredentials(username: String, password: String) = Unit
    }
}
