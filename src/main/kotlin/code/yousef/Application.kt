package code.yousef

import code.yousef.config.AppConfig
import code.yousef.config.loadAppConfig
import code.yousef.firestore.FirestoreProvider
import code.yousef.firestore.PortfolioMetaRepository
import code.yousef.firestore.PortfolioMetaService
import code.yousef.portfolio.admin.auth.AdminAuthProvider
import code.yousef.portfolio.admin.auth.AdminAuthService
import code.yousef.portfolio.admin.auth.FirestoreAdminAuthService
import code.yousef.portfolio.admin.createAdminSite
import code.yousef.portfolio.ai.AiCurriculumCatalog
import code.yousef.portfolio.ai.AiProgressStore
import code.yousef.portfolio.ai.FileAiProgressStore
import code.yousef.portfolio.ai.FirestoreAiProgressStore
import code.yousef.portfolio.building.auth.BuildingAuthProvider
import code.yousef.portfolio.building.auth.PasswordResetService
import code.yousef.portfolio.building.import.ExcelImportService
import code.yousef.portfolio.building.repo.BuildingRepository
import code.yousef.portfolio.building.repo.BuildingService
import code.yousef.portfolio.building.server.createBuildingRouter
import code.yousef.portfolio.contact.ContactService
import code.yousef.portfolio.contact.FileContactRepository
import code.yousef.portfolio.content.PortfolioContentService
import code.yousef.portfolio.content.store.FileContentStore
import code.yousef.portfolio.db.ContentStoreDriver
import code.yousef.portfolio.docs.*
import code.yousef.portfolio.photography.PhotographyService
import code.yousef.portfolio.photography.PortfolioPhotoAssetStoreFactory
import code.yousef.portfolio.docs.summon.DocsRouter
import code.yousef.portfolio.seen.SeenExecutionService
import code.yousef.portfolio.seen.SeenPlaygroundRenderer
import code.yousef.portfolio.server.*
import code.yousef.portfolio.session.PortfolioSessionStoreFactory
import code.yousef.portfolio.session.portfolioSessionConfig
import code.yousef.portfolio.finops.FinOpsService
import code.yousef.portfolio.finops.FinOpsRollupBackfillService
import code.yousef.portfolio.finops.FirestoreFinOpsRepository
import code.yousef.portfolio.finops.InMemoryFinOpsRepository
import code.yousef.portfolio.finops.HttpEdgeFinOpsReceiptStore
import code.yousef.portfolio.finops.HttpSamuraiIdentityResolver
import code.yousef.portfolio.finops.NoopSamuraiIdentityResolver
import code.yousef.portfolio.ssr.*
import code.yousef.portfolio.ui.fifthwall.FileFifthWallTelemetryStore
import code.yousef.portfolio.ui.fifthwall.FifthWallTelemetryStore
import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.AetherDispatcher
import codes.yousef.aether.core.jvm.VertxServer
import codes.yousef.aether.core.jvm.VertxServerConfig
import codes.yousef.aether.core.pipeline.Pipeline
import codes.yousef.aether.core.pipeline.installCallLogging
import codes.yousef.aether.core.pipeline.installContentNegotiation
import codes.yousef.aether.core.session.SessionMiddleware
import codes.yousef.aether.core.session.session
import codes.yousef.aether.db.DatabaseDriverRegistry
import codes.yousef.aether.web.router
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

data class ApplicationResources(
    val pipeline: Pipeline,
    val onShutdown: suspend () -> Unit
)

fun buildApplication(appConfig: AppConfig): ApplicationResources {
    val log = LoggerFactory.getLogger("Application")
    log.info("Starting Summon Portfolio with projectId={}, port={}", appConfig.projectId, appConfig.port)
    val sessionStoreResources = PortfolioSessionStoreFactory.fromEnvironment()

    // Services
    val firestoreDatabases = if (appConfig.useLocalStore) null else FirestoreProvider.createDatabases(appConfig)
    firestoreDatabases?.verifyPortfolioMigrationReady()
    val firestore = firestoreDatabases?.authority
    val mutationCoordinator = firestoreDatabases?.mutationCoordinator()
    val portfolioFirestoreStore = firestoreDatabases?.portfolioStore(mutationCoordinator!!)
    val portfolioMetaService = if (portfolioFirestoreStore != null) {
        PortfolioMetaService(PortfolioMetaRepository(portfolioFirestoreStore))
    } else {
        null
    }
    val samuraiIdentityResolver = System.getenv("SAMURAI_FINOPS_IDENTITY_URL")
        ?.trim()?.takeIf(String::isNotEmpty)?.let { endpoint ->
            HttpSamuraiIdentityResolver(
                endpoint = endpoint,
                bearerToken = requireNotNull(System.getenv("FINOPS_IDENTITY_READ_TOKEN")?.trim()?.takeIf(String::isNotEmpty)) {
                    "SAMURAI_FINOPS_IDENTITY_URL requires FINOPS_IDENTITY_READ_TOKEN"
                },
            )
        } ?: NoopSamuraiIdentityResolver
    val finOpsRepository = portfolioFirestoreStore?.let { store ->
            FirestoreFinOpsRepository(
                store = store,
                allocationEntryProjectionsReady = appConfig.finOpsAllocationEntryProjectionsReady,
                shardedRollupWritesEnabled = appConfig.finOpsShardedRollupWritesEnabled,
                shardedRollupsReady = appConfig.finOpsShardedRollupsReady,
            )
        } ?: InMemoryFinOpsRepository()
    val finOpsService = FinOpsService(
        repository = finOpsRepository,
        coverageStartAt = appConfig.finOpsCoverageStartAt,
        samuraiIdentityResolver = samuraiIdentityResolver,
    )
    val finOpsRollupBackfillService = portfolioFirestoreStore?.let { store ->
        FinOpsRollupBackfillService(
            source = FirestoreFinOpsRepository(store = store, shardedRollupsReady = false),
            target = FirestoreFinOpsRepository(
                store = store,
                shardedRollupWritesEnabled = true,
                shardedRollupsReady = true,
            ),
        )
    }
    val finOpsReceiptStore = appConfig.finOpsReceiptBaseUrl?.let { endpoint ->
        HttpEdgeFinOpsReceiptStore(
            endpoint = endpoint,
            bearerToken = requireNotNull(System.getenv("EDGE_ORIGIN_TOKEN")?.trim()?.takeIf(String::isNotEmpty)) {
                "FINOPS_RECEIPT_BASE_URL requires EDGE_ORIGIN_TOKEN"
            },
            maxReceiptBytes = appConfig.finOpsReceiptMaxBytes,
        )
    }
    
    val contentStore = if (firestore != null) {
        code.yousef.firestore.FirestoreContentStore(
            firestore = firestore,
            mutationCoordinator = requireNotNull(mutationCoordinator),
            seedOnInit = appConfig.firestoreSeedOnStart,
        )
    } else {
        FileContentStore.fromEnvironment()
    }
    
    val contentService = PortfolioContentService(contentStore)
    val contactService = ContactService(FileContactRepository(contentStore))
    val photoAssetStore = PortfolioPhotoAssetStoreFactory.fromEnvironment(appConfig)
    val photographyAssetBackfillService = PortfolioPhotoAssetStoreFactory.backfillFromEnvironment(appConfig)
    val photographyService = PhotographyService(
        contentStore = contentStore,
        assetStore = photoAssetStore,
        maxUploadBytes = appConfig.photographyMaxUploadBytes
    )
    
    // Admin auth - use Firestore in production, file-based locally
    val adminAuthService: AdminAuthProvider = if (portfolioFirestoreStore != null) {
        FirestoreAdminAuthService(
            store = portfolioFirestoreStore,
            allowBootstrapCredentials = appConfig.firestoreSeedOnStart,
        )
    } else {
        AdminAuthService(java.nio.file.Paths.get("storage/admin-credentials.json"))
    }
    
    // Building management services (requires Firestore)
    val buildingRouter = if (portfolioFirestoreStore != null) {
        val buildingAuthProvider = BuildingAuthProvider(
            store = portfolioFirestoreStore,
            seedOnInit = appConfig.firestoreSeedOnStart,
        )
        val passwordResetService = PasswordResetService(portfolioFirestoreStore, buildingAuthProvider)
        val buildingRepository = BuildingRepository(portfolioFirestoreStore)
        val buildingService = BuildingService(buildingRepository)
        val excelImportService = ExcelImportService(buildingRepository)
        createBuildingRouter(buildingAuthProvider, passwordResetService, buildingRepository, buildingService, excelImportService)
    } else {
        log.warn("Building management disabled - requires Firestore (set USE_LOCAL_STORE=false)")
        null
    }
    
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
    val docsRouter = DocsRouter(SeoExtractor(docsConfig))
    val docsCatalog = DocsCatalog(docsConfig)
    val webhookHandler = WebhookHandler(docsService, docsCache, docsConfig, docsCatalog)

    // Docs Services - Materia
    val materiaDocsConfig = DocsConfig.materiaFromEnv()
    val materiaDocsCache = DocsCache(materiaDocsConfig.cacheTtlSeconds)
    val materiaDocsService = DocsService(materiaDocsConfig, materiaDocsCache)
    val materiaDocsRouter = DocsRouter(SeoExtractor(materiaDocsConfig))
    val materiaDocsCatalog = DocsCatalog(materiaDocsConfig)
    val materiaWebhookHandler = WebhookHandler(materiaDocsService, materiaDocsCache, materiaDocsConfig, materiaDocsCatalog)

    // Docs Services - Sigil
    val sigilDocsConfig = DocsConfig.sigilFromEnv()
    val sigilDocsCache = DocsCache(sigilDocsConfig.cacheTtlSeconds)
    val sigilDocsService = DocsService(sigilDocsConfig, sigilDocsCache)
    val sigilDocsRouter = DocsRouter(SeoExtractor(sigilDocsConfig))
    val sigilDocsCatalog = DocsCatalog(sigilDocsConfig)
    val sigilWebhookHandler = WebhookHandler(sigilDocsService, sigilDocsCache, sigilDocsConfig, sigilDocsCatalog)

    // Docs Services - Aether
    val aetherDocsConfig = DocsConfig.aetherFromEnv()
    val aetherDocsCache = DocsCache(aetherDocsConfig.cacheTtlSeconds)
    val aetherDocsService = DocsService(aetherDocsConfig, aetherDocsCache)
    val aetherDocsRouter = DocsRouter(SeoExtractor(aetherDocsConfig))
    val aetherDocsCatalog = DocsCatalog(aetherDocsConfig)
    val aetherWebhookHandler = WebhookHandler(aetherDocsService, aetherDocsCache, aetherDocsConfig, aetherDocsCatalog)

    // Renderers
    val portfolioRenderer = PortfolioRenderer(contentService)
    val blogRenderer = BlogRenderer(contentService, markdownRenderer)
    val fifthWallRenderer = FifthWallRenderer()
    val materiaRenderer = MateriaLandingRenderer()
    val sigilRenderer = SigilLandingRenderer()
    val aetherRenderer = AetherLandingRenderer()

    // Docs Services - Seen
    val seenDocsConfig = DocsConfig.seenFromEnv()
    val seenDocsCache = DocsCache(seenDocsConfig.cacheTtlSeconds)
    val seenDocsService = DocsService(seenDocsConfig, seenDocsCache)
    val seenDocsRouter = DocsRouter(
        seoExtractor = SeoExtractor(seenDocsConfig),
        seenPackagesEnabled = appConfig.registryUpstreamUrl != null,
    )
    val seenDocsCatalog = DocsCatalog(seenDocsConfig)
    val seenWebhookHandler = WebhookHandler(seenDocsService, seenDocsCache, seenDocsConfig, seenDocsCatalog)

    // Seen
    val seenExecutionService = SeenExecutionService()
    val seenPlaygroundRenderer = SeenPlaygroundRenderer(
        packagesEnabled = appConfig.registryUpstreamUrl != null,
    )
    val seenLandingRenderer = SeenLandingRenderer(packagesEnabled = appConfig.registryUpstreamUrl != null)

    // AI Curriculum
    val aiProgressStore: AiProgressStore = if (portfolioFirestoreStore != null)
        FirestoreAiProgressStore(portfolioFirestoreStore) else FileAiProgressStore()
    val aiCurriculumCatalog = AiCurriculumCatalog()
    val aiCurriculumRenderer = AiCurriculumRenderer(markdownRenderer, aiCurriculumCatalog, aiProgressStore)
    val fifthWallTelemetryStore: FifthWallTelemetryStore = FileFifthWallTelemetryStore()

    // Routers
    val mainRouter = router {
        portfolioRoutes(
            portfolioRenderer,
            blogRenderer,
            fifthWallRenderer,
            contactService,
            contentService,
            adminAuthService,
            photographyService,
            aiCurriculumRenderer = aiCurriculumRenderer,
            aiProgressStore = aiProgressStore,
            fifthWallTelemetryStore = fifthWallTelemetryStore,
            markdownRenderer = markdownRenderer,
            finOpsService = finOpsService,
            finOpsReceiptStore = finOpsReceiptStore,
            finOpsInternalIngestToken = System.getenv("FINOPS_INTERNAL_INGEST_TOKEN")?.trim()?.takeIf(String::isNotEmpty),
        )
        registerPortfolioEdgeJobRoutes(
            mutationCoordinator = mutationCoordinator,
            photographyAssetBackfillService = photographyAssetBackfillService,
            finOpsRollupBackfillService = finOpsRollupBackfillService,
            photographyMaxAssetBytes = appConfig.photographyMaxUploadBytes,
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

    val aetherRouter = router {
        get("/") { exchange ->
            val page = aetherRenderer.landingPage()
            exchange.respondSummonPage(page)
        }
        docsRoutes(
            aetherDocsService,
            markdownRenderer,
            linkRewriter,
            aetherDocsRouter,
            aetherWebhookHandler,
            aetherDocsConfig,
            aetherDocsCatalog,
            basePath = "/docs"
        )
    }

    val seenRouter = router {
        seenRoutes(seenLandingRenderer, seenPlaygroundRenderer, seenExecutionService)
        docsRoutes(
            seenDocsService,
            markdownRenderer,
            linkRewriter,
            seenDocsRouter,
            seenWebhookHandler,
            seenDocsConfig,
            seenDocsCatalog,
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
    val hostMap = mutableMapOf(
        "summon.yousef.codes" to summonRouter.asMiddleware(),
        "summon.dev.yousef.codes" to summonRouter.asMiddleware(),
        "materia.dev.yousef.codes" to materiaRouter.asMiddleware(),
        "materia.yousef.codes" to materiaRouter.asMiddleware(),
        "sigil.dev.yousef.codes" to sigilRouter.asMiddleware(),
        "sigil.yousef.codes" to sigilRouter.asMiddleware(),
        "aether.yousef.codes" to aetherRouter.asMiddleware(),
        "aether.dev.yousef.codes" to aetherRouter.asMiddleware(),
        "seen.yousef.codes" to seenRouter.asMiddleware(),
        "seen.dev.yousef.codes" to seenRouter.asMiddleware(),
        "localhost" to mainRouter.asMiddleware(),
        "docs.yousef.codes" to docsRouterHandler.asMiddleware()
    )
    
    // Add building management routes if Firestore is available
    if (buildingRouter != null) {
        hostMap["building.yousef.codes"] = buildingRouter.asMiddleware()
        hostMap["building.dev.yousef.codes"] = buildingRouter.asMiddleware()
        log.info("Building management enabled at building.yousef.codes")
    }

    hostMap["*"] = mainRouter.asMiddleware()
    
    val hostRouter = HostRouter(hostMap)

    val staticHandler = StaticResourceHandler("static", "/static")
    // Root-level handler for hydration scripts (loaded by sigil-summon inline JS at /sigil-hydration.js)
    val rootHydrationHandler = StaticResourceHandler("static", "/")
    val registryGateway = appConfig.registryUpstreamUrl?.let { publicUpstream ->
        RegistryGatewayMiddleware(
            publicHost = requireNotNull(appConfig.registryPublicHost),
            upstreamUrl = publicUpstream,
            releaseActionsUpstreamUrl = appConfig.registryReleaseActionsUpstreamUrl,
            securityActionsUpstreamUrl = appConfig.registrySecurityActionsUpstreamUrl
        )
    }

    val pipeline = Pipeline().apply {
        // Final recovery boundary. Detailed failures stay in structured server
        // logs; production clients receive only an opaque correlation ID.
        this.use { exchange, next ->
            try {
                next()
            } catch (e: Throwable) {
                exchange.respondInternalServerError(log, "Unhandled portfolio request", e)
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

        registryGateway?.let { use(it.middleware) }

        use(SessionMiddleware(sessionStoreResources.store, portfolioSessionConfig()).asMiddleware())
        
        // Admin Site Middleware (only for main portfolio site, not building subdomain)
        val adminMiddleware: codes.yousef.aether.core.pipeline.Middleware = { exchange, next ->
            val host = exchange.request.headers["Host"]?.substringBefore(":")
            val isBuildingSite = host == "building.yousef.codes" || host == "building.dev.yousef.codes"
            val path = exchange.request.path
            if (
                !isBuildingSite &&
                (path == "/admin" ||
                    path.startsWith("/admin/photography") ||
                    path == "/admin/markdown-preview" ||
                    path.startsWith("/admin/spending"))
            ) {
                val session = exchange.session()
                val username = session?.get("username") as? String
                if (username == null) {
                    exchange.redirectWithLength("/admin/login?next=${path}")
                } else {
                    next()
                }
            } else if (!isBuildingSite &&
                path.startsWith("/admin") &&
                !path.startsWith("/admin/login") &&
                !path.startsWith("/admin/change-password")) {

                val session = exchange.session()
                val username = session?.get("username") as? String
                if (username == null) {
                    exchange.redirectWithLength("/admin/login")
                } else {
                    adminRouter.asMiddleware()(exchange, next)
                }
            } else if (!isBuildingSite && (path == "/ai" || path.startsWith("/ai/"))) {
                val session = exchange.session()
                val username = session?.get("username") as? String
                if (username == null) {
                    exchange.redirectWithLength("/admin/login?next=${path}")
                } else {
                    next()
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
        sessionStoreResources.close()
        firestoreDatabases?.close()
    }
}

fun main() {
    val appConfig = loadAppConfig()
    val resources = buildApplication(appConfig)

    // Aether buffers request bodies before middleware. Raise the global cap only
    // when a configured route needs it; each registry/receipt route still applies
    // its narrower content and size validation before processing the payload.
    val config = if (appConfig.registryUpstreamUrl != null || appConfig.finOpsReceiptBaseUrl != null) {
        VertxServerConfig(
            port = appConfig.port,
            maxRequestBodySize = maxOf(REGISTRY_GATEWAY_MAX_REQUEST_BYTES, MAX_FINOPS_MANUAL_MULTIPART_BYTES)
        )
    } else {
        VertxServerConfig(port = appConfig.port)
    }
    val server = VertxServer(config, resources.pipeline) { exchange ->
        exchange.notFound("Route not found")
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            server.stop()
            resources.onShutdown()
        }
    })

    runBlocking(AetherDispatcher.dispatcher) {
        server.start()
    }

    CountDownLatch(1).await()
}
