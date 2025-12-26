package code.yousef

import code.yousef.config.AppConfig
import code.yousef.config.loadAppConfig
import code.yousef.firestore.FirestoreProvider
import code.yousef.firestore.PortfolioMetaRepository
import code.yousef.firestore.PortfolioMetaService
import code.yousef.portfolio.admin.AdminContentService
import code.yousef.portfolio.admin.auth.AdminAuthService
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.contact.InMemoryContactRepository
import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.store.FileContentStore
import code.yousef.portfolio.docs.*
import code.yousef.portfolio.docs.summon.DocsRouter
import code.yousef.portfolio.server.HostRouter
import code.yousef.portfolio.server.StaticResourceHandler
import code.yousef.portfolio.server.portfolioRoutes
import code.yousef.portfolio.server.summonRoutes
import code.yousef.portfolio.server.docsRoutes
import code.yousef.portfolio.ssr.AdminRenderer
import code.yousef.portfolio.ssr.BlogRenderer
import code.yousef.portfolio.ssr.PortfolioRenderer
import code.yousef.portfolio.ssr.MateriaLandingRenderer
import code.yousef.portfolio.ssr.SigilLandingRenderer
import code.yousef.portfolio.ssr.EnvironmentLinksRegistry
import code.yousef.portfolio.ssr.resolveEnvironmentLinks
import code.yousef.portfolio.server.respondSummonPage
import codes.yousef.aether.core.AetherDispatcher
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.pipeline.Pipeline
import codes.yousef.aether.core.pipeline.installCallLogging
import codes.yousef.aether.core.pipeline.installContentNegotiation
import codes.yousef.aether.core.session.InMemorySessionStore
import codes.yousef.aether.core.session.SessionConfig
import codes.yousef.aether.core.session.SessionMiddleware
import codes.yousef.aether.web.router
import codes.yousef.aether.core.session.session
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import code.yousef.portfolio.db.ContentStoreDriver
import codes.yousef.aether.db.DatabaseDriverRegistry
import code.yousef.portfolio.admin.createAdminSite

data class ApplicationResources(
    val pipeline: Pipeline,
    val onShutdown: suspend () -> Unit
)

fun buildApplication(appConfig: AppConfig): ApplicationResources {
    val log = LoggerFactory.getLogger("Application")
    log.info("Starting Summon Portfolio with projectId={}, port={}", appConfig.projectId, appConfig.port)

    // Services
    val firestore = if (appConfig.useLocalStore) null else FirestoreProvider.create(appConfig)
    val portfolioMetaService = if (firestore != null) {
        PortfolioMetaService(PortfolioMetaRepository(firestore))
    } else {
        null
    }
    
    val contentStore = if (firestore != null) {
        code.yousef.firestore.FirestoreContentStore(firestore)
    } else {
        FileContentStore.fromEnvironment()
    }
    
    val contentService = PortfolioContentService(contentStore)
    // Use InMemoryContactRepository for now as FirestoreContactRepository is missing
    val contactService = ContactService(InMemoryContactRepository())
    val adminAuthService = AdminAuthService(java.nio.file.Paths.get("storage/admin-credentials.json"))
    val adminContentService = AdminContentService(contentStore)
    
    // Initialize Aether DB Driver
    val driver = ContentStoreDriver(contentStore)
    DatabaseDriverRegistry.initialize(driver)
    
    // Initialize Admin Site
    val adminSite = createAdminSite()
    val adminRouter = adminSite.urls()
    
    // Docs Services - Summon
    val docsConfig = DocsConfig.fromEnv()
    val docsCache = DocsCache(docsConfig.cacheTtlSeconds)
    val docsService = DocsService(docsConfig, docsCache) 
    val markdownRenderer = MarkdownRenderer()
    val linkRewriter = LinkRewriter()
    val docsRouter = DocsRouter(SeoExtractor(docsConfig), "https://docs.yousef.codes")
    val docsCatalog = DocsCatalog(docsConfig)
    val webhookHandler = WebhookHandler(docsService, docsCache, docsConfig, docsCatalog)

    // Docs Services - Materia
    val materiaDocsConfig = DocsConfig.materiaFromEnv()
    val materiaDocsCache = DocsCache(materiaDocsConfig.cacheTtlSeconds)
    val materiaDocsService = DocsService(materiaDocsConfig, materiaDocsCache)
    val materiaDocsRouter = DocsRouter(SeoExtractor(materiaDocsConfig), "https://materia.yousef.codes")
    val materiaDocsCatalog = DocsCatalog(materiaDocsConfig)
    val materiaWebhookHandler = WebhookHandler(materiaDocsService, materiaDocsCache, materiaDocsConfig, materiaDocsCatalog)

    // Docs Services - Sigil
    val sigilDocsConfig = DocsConfig.sigilFromEnv()
    val sigilDocsCache = DocsCache(sigilDocsConfig.cacheTtlSeconds)
    val sigilDocsService = DocsService(sigilDocsConfig, sigilDocsCache)
    val sigilDocsRouter = DocsRouter(SeoExtractor(sigilDocsConfig), "https://sigil.yousef.codes")
    val sigilDocsCatalog = DocsCatalog(sigilDocsConfig)
    val sigilWebhookHandler = WebhookHandler(sigilDocsService, sigilDocsCache, sigilDocsConfig, sigilDocsCatalog)
    
    // Renderers
    val portfolioRenderer = PortfolioRenderer(contentService)
    val blogRenderer = BlogRenderer(contentService)
    val materiaRenderer = MateriaLandingRenderer()
    val sigilRenderer = SigilLandingRenderer()
    val adminRenderer = AdminRenderer()

    // Routers
    val mainRouter = router {
        portfolioRoutes(
            portfolioRenderer,
            blogRenderer,
            contactService,
            contentService,
            adminRenderer,
            adminContentService,
            adminAuthService
        )
    }

    val summonRouter = router {
        summonRoutes(
            portfolioRenderer,
            docsService,
            markdownRenderer,
            linkRewriter,
            docsRouter,
            webhookHandler,
            docsConfig,
            docsCatalog
        )
    }

    val materiaRouter = router {
        get("/") { exchange ->
            val page = materiaRenderer.landingPage()
            exchange.respondSummonPage(page)
        }
        docsRoutes(
            materiaDocsService,
            markdownRenderer,
            linkRewriter,
            materiaDocsRouter,
            materiaWebhookHandler,
            materiaDocsConfig,
            materiaDocsCatalog,
            basePath = "/docs"
        )
    }

    val sigilRouter = router {
        get("/") { exchange ->
            val page = sigilRenderer.landingPage()
            exchange.respondSummonPage(page)
        }
        docsRoutes(
            sigilDocsService,
            markdownRenderer,
            linkRewriter,
            sigilDocsRouter,
            sigilWebhookHandler,
            sigilDocsConfig,
            sigilDocsCatalog,
            basePath = "/docs"
        )
    }

    val docsRouterHandler = router {
        docsRoutes(
            docsService,
            markdownRenderer,
            linkRewriter,
            docsRouter,
            webhookHandler,
            docsConfig,
            docsCatalog
        )
    }

    // Host Routing
    val hostRouter = HostRouter(mapOf(
        "summon.yousef.codes" to summonRouter.asMiddleware(),
        "summon.dev.yousef.codes" to summonRouter.asMiddleware(),
        "materia.dev.yousef.codes" to materiaRouter.asMiddleware(),
        "materia.yousef.codes" to materiaRouter.asMiddleware(),
        "sigil.dev.yousef.codes" to sigilRouter.asMiddleware(),
        "sigil.yousef.codes" to sigilRouter.asMiddleware(),
        "localhost" to mainRouter.asMiddleware(),
        "docs.yousef.codes" to docsRouterHandler.asMiddleware(),
        "*" to mainRouter.asMiddleware()
    ))

    val staticHandler = StaticResourceHandler("static", "/static")
    // Root-level handler for hydration scripts (loaded by sigil-summon inline JS at /sigil-hydration.js)
    val rootHydrationHandler = StaticResourceHandler("static", "/")

    val pipeline = Pipeline().apply {
        // Custom debug recovery to print stack traces
        this.use { exchange, next ->
            try {
                next()
            } catch (e: Throwable) {
                System.err.println("Uncaught exception: ${e.message}")
                e.printStackTrace()
                val errorBody = "Debug Recovery: ${e.message}\n${e.stackTraceToString()}"
                val errorBytes = errorBody.toByteArray(Charsets.UTF_8)
                exchange.response.statusCode = 500
                exchange.response.setHeader("Content-Type", "text/plain")
                exchange.response.setHeader("Content-Length", errorBytes.size.toString())
                exchange.response.write(errorBytes)
                exchange.response.end()
            }
        }
        installCallLogging()
        installContentNegotiation()

        this.use { exchange, next ->
            val host = exchange.request.headers["Host"]
            val links = resolveEnvironmentLinks(host)
            EnvironmentLinksRegistry.withLinks(links) {
                next()
            }
        }

        use(SessionMiddleware(InMemorySessionStore(), SessionConfig(cookieName = "admin_session")).asMiddleware())
        
        // Admin Site Middleware
        val adminMiddleware: codes.yousef.aether.core.pipeline.Middleware = { exchange, next ->
            if (exchange.request.path.startsWith("/admin") && 
                !exchange.request.path.startsWith("/admin/login") && 
                !exchange.request.path.startsWith("/admin/change-password")) {
                
                val session = exchange.session()
                val username = session?.get("username") as? String
                if (username == null) {
                    exchange.redirect("/admin/login")
                } else {
                    adminRouter.asMiddleware()(exchange, next)
                }
            } else {
                next()
            }
        }
        this.use(adminMiddleware)

        use(staticHandler::handle)
        use(rootHydrationHandler::handle)
        use(hostRouter::handle)
    }
    
    return ApplicationResources(pipeline) {
        firestore?.close()
    }
}

fun main() = runBlocking(AetherDispatcher.dispatcher) {
    val appConfig = loadAppConfig()
    val resources = buildApplication(appConfig)

    val config = VertxServerConfig(port = appConfig.port)
    val server = VertxServer(config, resources.pipeline) { exchange ->
        exchange.notFound("Route not found")
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            server.stop()
            resources.onShutdown()
        }
    })

    server.start()
}
