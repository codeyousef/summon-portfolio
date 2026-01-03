package code.yousef.portfolio.building.server

import code.yousef.portfolio.building.auth.BuildingAuthProvider
import code.yousef.portfolio.building.auth.BuildingSession
import code.yousef.portfolio.building.import.ExcelImportService
import code.yousef.portfolio.building.model.Building
import code.yousef.portfolio.building.model.PaymentStatus
import code.yousef.portfolio.building.repo.BuildingRepository
import code.yousef.portfolio.building.repo.BuildingService
import code.yousef.portfolio.building.ui.*
import code.yousef.portfolio.server.respondSummonPage
import code.yousef.portfolio.ssr.SummonPage
import codes.yousef.aether.core.Exchange
import codes.yousef.aether.core.jvm.receiveParameters
import codes.yousef.aether.core.session.session
import codes.yousef.aether.web.Router
import codes.yousef.aether.web.pathParam
import codes.yousef.aether.web.router
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

private val log = LoggerFactory.getLogger("BuildingRoutes")

/**
 * Creates the building management router with all routes.
 */
fun createBuildingRouter(
    authProvider: BuildingAuthProvider,
    repository: BuildingRepository,
    service: BuildingService,
    importService: ExcelImportService
): Router {
    return router {
        buildingRoutes(authProvider, repository, service, importService)
    }
}

/**
 * Building management routes extension.
 */
fun Router.buildingRoutes(
    authProvider: BuildingAuthProvider,
    repository: BuildingRepository,
    service: BuildingService,
    importService: ExcelImportService
) {
    // ===================== Auth Routes =====================
    
    get("/login") { exchange ->
        val session = exchange.getBuildingSession()
        if (session != null && !session.mustChangePassword) {
            exchange.redirect("/")
            return@get
        }
        exchange.respondSummonPage(buildingLoginPage(null))
    }
    
    post("/login") { exchange ->
        val params = exchange.receiveParameters()
        val username = params["username"]?.trim() ?: ""
        val password = params["password"] ?: ""
        
        when (val result = authProvider.authenticate(username, password)) {
            is BuildingAuthProvider.AuthResult.Invalid -> {
                exchange.respondSummonPage(
                    buildingLoginPage(BuildingStrings.INVALID_CREDENTIALS),
                    401
                )
            }
            is BuildingAuthProvider.AuthResult.Success -> {
                exchange.setBuildingSession(BuildingSession(username, result.mustChangePassword))
                if (result.mustChangePassword) {
                    exchange.redirect("/change-password")
                } else {
                    exchange.redirect("/")
                }
            }
        }
    }
    
    get("/change-password") { exchange ->
        val session = exchange.getBuildingSession()
        if (session == null) {
            exchange.redirect("/login")
            return@get
        }
        exchange.respondSummonPage(buildingChangePasswordPage(session.username, null))
    }
    
    post("/change-password") { exchange ->
        val session = exchange.getBuildingSession()
        if (session == null) {
            exchange.redirect("/login")
            return@post
        }
        
        // Debug: log raw body
        val rawBody = exchange.request.bodyText()
        println("DEBUG change-password: raw body = '$rawBody' (length=${rawBody.length})")
        
        val params = exchange.receiveParameters()
        val password = params["password"] ?: ""
        val confirm = params["confirm"] ?: ""
        
        // Debug logging
        println("DEBUG change-password: params keys = ${params.keys}")
        println("DEBUG change-password: password length = ${password.length}, confirm length = ${confirm.length}")
        
        when {
            password.isBlank() -> {
                exchange.respondSummonPage(
                    buildingChangePasswordPage(session.username, BuildingStrings.PASSWORD_EMPTY),
                    400
                )
            }
            password != confirm -> {
                exchange.respondSummonPage(
                    buildingChangePasswordPage(session.username, BuildingStrings.PASSWORDS_DONT_MATCH),
                    400
                )
            }
            else -> {
                authProvider.updatePassword(session.username, password)
                exchange.setBuildingSession(BuildingSession(session.username, false))
                exchange.redirect("/")
            }
        }
    }
    
    get("/logout") { exchange ->
        exchange.clearBuildingSession()
        exchange.redirect("/login")
    }
    
    // ===================== Protected Routes =====================
    
    // Dashboard
    get("/") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        val summary = service.getDashboardSummary()
        exchange.respondSummonPage(buildingDashboardPage(session.username, summary))
    }
    
    // Buildings list
    get("/buildings") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        val buildings = repository.listBuildings()
        val apartments = repository.listApartments()
        val unitCounts = apartments.groupBy { it.buildingId }.mapValues { it.value.size }
        exchange.respondSummonPage(buildingsListPage(session.username, buildings, unitCounts))
    }
    
    // Building detail (units)
    get("/buildings/:id") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        val buildingId = exchange.pathParam("id") ?: ""
        val building = repository.getBuilding(buildingId)
        
        if (building == null) {
            exchange.redirect("/buildings")
            return@get
        }
        
        val apartments = service.getApartmentsWithDetails(buildingId)
        exchange.respondSummonPage(buildingUnitsPage(session.username, building, apartments))
    }
    
    // Payments list
    get("/payments") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        val statusParam = exchange.request.queryParameter("status")
        val statusFilter = statusParam?.let { 
            try { PaymentStatus.valueOf(it) } catch (_: Exception) { null }
        }
        val payments = service.getPaymentsWithDetails(statusFilter = statusFilter)
        exchange.respondSummonPage(paymentsListPage(session.username, payments, statusParam))
    }
    
    // Apartment payments
    get("/apartments/:id/payments") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        val apartmentId = exchange.pathParam("id") ?: ""
        val apartment = repository.getApartment(apartmentId)
        
        if (apartment == null) {
            exchange.redirect("/buildings")
            return@get
        }
        
        val building = repository.getBuilding(apartment.buildingId)
        val payments = service.getPaymentsWithDetails(buildingId = apartment.buildingId)
            .filter { it.apartment?.id == apartmentId }
        
        exchange.respondSummonPage(paymentsListPage(session.username, payments, null))
    }
    
    // Import page
    get("/import") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        exchange.respondSummonPage(importPage(session.username, null, null))
    }
    
    // Handle file upload
    post("/import") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        
        try {
            // Get the raw body bytes
            val bodyBytes = exchange.request.bodyBytes()
            
            // Parse multipart form data manually
            val contentType = exchange.request.headers["Content-Type"] ?: ""
            val boundary = extractBoundary(contentType)
            
            if (boundary == null) {
                exchange.respondSummonPage(
                    importPage(session.username, null, "نوع المحتوى غير صالح"),
                    400
                )
                return@post
            }
            
            val fileBytes = extractFileFromMultipart(bodyBytes, boundary)
            
            if (fileBytes == null || fileBytes.isEmpty()) {
                exchange.respondSummonPage(
                    importPage(session.username, null, "لم يتم اختيار ملف"),
                    400
                )
                return@post
            }
            
            val result = importService.importFromExcel(ByteArrayInputStream(fileBytes))
            
            if (result.success) {
                val message = "تم استيراد ${result.unitsImported} شقة و ${result.paymentsImported} دفعة من ${result.buildingName}"
                exchange.respondSummonPage(importPage(session.username, message, null))
            } else {
                exchange.respondSummonPage(
                    importPage(session.username, null, result.errors.joinToString("\n")),
                    400
                )
            }
        } catch (e: Exception) {
            log.error("Import error", e)
            exchange.respondSummonPage(
                importPage(session.username, null, "خطأ في الاستيراد: ${e.message}"),
                500
            )
        }
    }
    
    // Clear all data
    post("/clear-data") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        
        try {
            repository.clearAllData()
            exchange.respondSummonPage(
                importPage(session.username, "تم مسح جميع البيانات بنجاح", null)
            )
        } catch (e: Exception) {
            log.error("Clear data error", e)
            exchange.respondSummonPage(
                importPage(session.username, null, "خطأ في مسح البيانات: ${e.message}"),
                500
            )
        }
    }
}

// ===================== Helper Functions =====================

private suspend fun Exchange.requireAuth(): BuildingSession? {
    val session = getBuildingSession()
    if (session == null) {
        redirect("/login")
        return null
    }
    if (session.mustChangePassword) {
        redirect("/change-password")
        return null
    }
    return session
}

private suspend fun Exchange.getBuildingSession(): BuildingSession? {
    val session = session() ?: return null
    val username = session.get("building_username") as? String ?: return null
    val mustChangePassword = session.get("building_mustChangePassword")?.toString()?.toBoolean() ?: false
    return BuildingSession(username, mustChangePassword)
}

private suspend fun Exchange.setBuildingSession(buildingSession: BuildingSession) {
    val session = session() ?: throw IllegalStateException("Session middleware not installed")
    session.set("building_username", buildingSession.username)
    session.set("building_mustChangePassword", buildingSession.mustChangePassword.toString())
}

private suspend fun Exchange.clearBuildingSession() {
    val session = session() ?: return
    session.remove("building_username")
    session.remove("building_mustChangePassword")
}

private fun buildingLoginPage(errorMessage: String?): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.LOGIN_TITLE} - ${BuildingStrings.APP_TITLE}")
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        // Add Tajawal Arabic font
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { BuildingLoginPage(errorMessage = errorMessage) }
)

private fun buildingChangePasswordPage(username: String, errorMessage: String?): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.CHANGE_PASSWORD_TITLE} - ${BuildingStrings.APP_TITLE}")
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { BuildingChangePasswordPage(username = username, errorMessage = errorMessage) }
)

private fun buildingDashboardPage(username: String, summary: code.yousef.portfolio.building.model.DashboardSummary): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.DASHBOARD} - ${BuildingStrings.APP_TITLE}")
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { BuildingDashboardPage(username = username, summary = summary) }
)

private fun buildingsListPage(
    username: String, 
    buildings: List<Building>,
    unitCounts: Map<String, Int>
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.BUILDINGS} - ${BuildingStrings.APP_TITLE}")
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { BuildingsListPage(username = username, buildings = buildings, unitCounts = unitCounts) }
)

private fun buildingUnitsPage(
    username: String,
    building: Building,
    apartments: List<code.yousef.portfolio.building.model.ApartmentWithDetails>
): SummonPage = SummonPage(
    head = { head ->
        head.title("${building.name} - ${BuildingStrings.APP_TITLE}")
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { BuildingUnitsPage(username = username, building = building, apartments = apartments) }
)

private fun paymentsListPage(
    username: String,
    payments: List<code.yousef.portfolio.building.model.PaymentWithDetails>,
    currentFilter: String?
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.PAYMENTS} - ${BuildingStrings.APP_TITLE}")
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { PaymentsListPage(username = username, payments = payments, currentFilter = currentFilter) }
)

private fun importPage(
    username: String,
    successMessage: String?,
    errorMessage: String?
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.IMPORT_DATA} - ${BuildingStrings.APP_TITLE}")
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { ImportPage(username = username, successMessage = successMessage, errorMessage = errorMessage) }
)

// Multipart parsing helpers
private fun extractBoundary(contentType: String): String? {
    val boundaryPrefix = "boundary="
    val idx = contentType.indexOf(boundaryPrefix)
    if (idx < 0) return null
    return contentType.substring(idx + boundaryPrefix.length).trim().removeSurrounding("\"")
}

private fun extractFileFromMultipart(body: ByteArray, boundary: String): ByteArray? {
    val bodyStr = String(body, Charsets.ISO_8859_1)
    val boundaryStr = "--$boundary"
    
    // Find the file part
    val parts = bodyStr.split(boundaryStr)
    for (part in parts) {
        if (part.contains("filename=") && part.contains("Content-Type")) {
            // Find the start of file content (after double CRLF)
            val headerEnd = part.indexOf("\r\n\r\n")
            if (headerEnd > 0) {
                val contentStart = headerEnd + 4
                // Find end of content (before next boundary or end)
                var contentEnd = part.length
                if (part.endsWith("--\r\n")) {
                    contentEnd -= 4
                } else if (part.endsWith("\r\n")) {
                    contentEnd -= 2
                }
                
                // Extract bytes from original body
                val partStartInBody = bodyStr.indexOf(part)
                val fileStartInBody = partStartInBody + contentStart
                val fileEndInBody = partStartInBody + contentEnd
                
                if (fileStartInBody >= 0 && fileEndInBody <= body.size) {
                    return body.copyOfRange(fileStartInBody, fileEndInBody)
                }
            }
        }
    }
    return null
}
