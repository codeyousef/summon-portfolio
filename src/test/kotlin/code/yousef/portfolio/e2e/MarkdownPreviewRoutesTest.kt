package code.yousef.portfolio.e2e

import code.yousef.portfolio.admin.auth.AdminAuthProvider
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.contact.FileContactRepository
import code.yousef.portfolio.content.PortfolioContentService
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
import codes.yousef.aether.core.session.DefaultSession
import codes.yousef.aether.core.session.InMemorySessionStore
import codes.yousef.aether.core.session.SessionConfig
import codes.yousef.aether.core.session.SessionMiddleware
import codes.yousef.aether.web.router
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownPreviewRoutesTest {
    private lateinit var server: VertxServer
    private lateinit var baseUrl: String
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    @BeforeTest
    fun startServer() {
        val tempDir = Files.createTempDirectory("markdown-preview-routes")
        val contentStore = FileContentStore(tempDir.resolve("content.json"))
        val contentService = PortfolioContentService(contentStore)
        val markdownRenderer = MarkdownRenderer()
        val photographyService = PhotographyService(
            contentStore = contentStore,
            assetStore = LocalPhotoAssetStore(tempDir.resolve("uploads")),
            maxUploadBytes = 1_024,
        )
        val route = router {
            portfolioRoutes(
                portfolioRenderer = PortfolioRenderer(contentService),
                blogRenderer = BlogRenderer(contentService, markdownRenderer),
                fifthWallRenderer = FifthWallRenderer(),
                contactService = ContactService(FileContactRepository(contentStore)),
                contentService = contentService,
                adminAuthService = RejectingAdminAuthProvider,
                photographyService = photographyService,
                markdownRenderer = markdownRenderer,
            )
        }

        val sessionConfig = SessionConfig(cookieName = SESSION_COOKIE_NAME)
        val sessionStore = InMemorySessionStore(sessionConfig)
        val authenticatedSession = DefaultSession(
            id = AUTHENTICATED_SESSION_ID,
            createdAt = System.currentTimeMillis(),
        ).apply {
            set("username", "preview-admin")
            set("mustChangePassword", "false")
        }
        runBlocking { sessionStore.save(authenticatedSession) }

        val pipeline = Pipeline().apply {
            use(SessionMiddleware(sessionStore, sessionConfig).asMiddleware())
            use(route.asMiddleware())
        }
        val port = ServerSocket(0).use { it.localPort }
        baseUrl = "http://localhost:$port"
        server = VertxServer(VertxServerConfig(port = port), pipeline) { exchange ->
            exchange.notFound("Route not found")
        }
        runBlocking { server.start() }
    }

    @AfterTest
    fun stopServer() {
        runBlocking { server.stop() }
    }

    @Test
    fun `unauthenticated preview redirects to login with return path`() {
        val response = get("/admin/markdown-preview", authenticated = false)

        assertEquals(302, response.statusCode())
        assertEquals(
            "/admin/login?next=/admin/markdown-preview",
            response.headers().firstValue("Location").orElse(null),
        )
    }

    @Test
    fun `preview renders a typed native form without app authored scripts`() {
        val response = get("/admin/markdown-preview")

        assertEquals(200, response.statusCode())
        val document = Jsoup.parse(response.body())
        val form = document.selectFirst("form[action='/admin/markdown-preview'][method=post]")
        assertTrue(form != null, "The preview must be a native server-submitted form")
        assertTrue(form.selectFirst("textarea[name=markdown][maxlength=50000]") != null)
        assertEquals("Render preview", form.selectFirst("button[type=submit]")?.text())
        val inlineScripts = document.select("script:not([src])")
        val frameworkBootstrap = inlineScripts.filter {
            it.attr("type").isBlank() && it.data().contains("window.__SUMMON_QUEUE__")
        }
        val frameworkHydrationData = inlineScripts.filter {
            it.id() == "summon-hydration-data" && it.attr("type") == "application/json"
        }
        assertEquals(1, frameworkBootstrap.size, "Summon's bootstrap must be the only executable inline script")
        assertEquals(1, frameworkHydrationData.size, "Summon's hydration data must be the only inline data script")
        assertEquals(
            inlineScripts.size,
            frameworkBootstrap.size + frameworkHydrationData.size,
            "The application must not add scripts outside the Summon runtime",
        )
        assertEquals(
            listOf("/summon-hydration.js", "/summon-bootloader.js"),
            document.select("script[src]").map { it.attr("src") },
            "The framework hydration runtime must be the only script reference",
        )
        assertTrue(document.select("[onclick], [oninput], [onchange], [onsubmit]").isEmpty())
        assertFalse(response.body().contains("markdown-preview.js"))
    }

    @Test
    fun `submitted markdown renders through Prose and escapes raw markup`() {
        val source = """
            # Safe heading

            **Typed output**

            <script>window.previewWasCompromised = true</script>

            <img src=x onerror=alert(1)>

            [unsafe link](javascript:alert(1))
        """.trimIndent()
        val response = postMarkdown(source)

        assertEquals(200, response.statusCode())
        val document = Jsoup.parse(response.body())
        val preview = document.selectFirst("#preview")
        assertTrue(preview != null)
        assertEquals("Safe heading", preview.selectFirst("h1")?.text())
        assertEquals("Typed output", preview.selectFirst("strong")?.text())
        assertTrue(preview.select("script, img").isEmpty(), "Raw HTML must not become executable DOM")
        assertTrue(preview.select("a[href^=javascript]").isEmpty(), "Unsafe URL schemes must be discarded")
        assertTrue(preview.text().contains("<script>window.previewWasCompromised = true</script>"))
        assertTrue(preview.text().contains("<img src=x onerror=alert(1)>"))
        assertEquals(source, document.selectFirst("textarea[name=markdown]")?.wholeText())
    }

    @Test
    fun `oversized declared form body is rejected before markdown rendering`() {
        val oversizedSource = "a".repeat(64 * 1_024 + 1)
        val response = postMarkdown(oversizedSource)

        assertEquals(413, response.statusCode())
        assertTrue(response.body().contains("The preview request is too large."))
        assertFalse(response.body().contains("Rendered output"))
    }

    private fun get(path: String, authenticated: Boolean = true): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl$path"))
            .GET()
        if (authenticated) builder.header("Cookie", "$SESSION_COOKIE_NAME=$AUTHENTICATED_SESSION_ID")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun postMarkdown(source: String): HttpResponse<String> {
        val encodedBody = "markdown=" + URLEncoder.encode(source, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/admin/markdown-preview"))
            .header("Cookie", "$SESSION_COOKIE_NAME=$AUTHENTICATED_SESSION_ID")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encodedBody))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private object RejectingAdminAuthProvider : AdminAuthProvider {
        override fun authenticate(username: String, password: String): AdminAuthProvider.AuthResult =
            AdminAuthProvider.AuthResult.Invalid

        override fun mustChangePassword(): Boolean = false

        override fun currentUsername(): String = "preview-admin"

        override fun updateCredentials(username: String, password: String) = Unit
    }

    private companion object {
        const val SESSION_COOKIE_NAME = "admin_session"
        const val AUTHENTICATED_SESSION_ID = "markdown-preview-test-session"
    }
}
