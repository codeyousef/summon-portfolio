package code.yousef.portfolio.building.server

import code.yousef.portfolio.building.auth.BuildingAuthProvider
import code.yousef.portfolio.building.auth.BuildingSession
import code.yousef.portfolio.building.auth.PasswordResetService
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
    passwordResetService: PasswordResetService,
    repository: BuildingRepository,
    service: BuildingService,
    importService: ExcelImportService
): Router {
    return router {
        buildingRoutes(authProvider, passwordResetService, repository, service, importService)
    }
}

/**
 * Building management routes extension.
 */
fun Router.buildingRoutes(
    authProvider: BuildingAuthProvider,
    passwordResetService: PasswordResetService,
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
        
        // Debug: Check raw body
        val rawBody = exchange.request.bodyText()
        println("DEBUG: Raw body length = ${rawBody.length}, content = '${rawBody.take(200)}'")
        
        val params = exchange.receiveParameters()
        println("DEBUG: Parsed params = $params")
        
        val password = params["password"] ?: ""
        val confirm = params["confirm"] ?: ""
        
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
    
    // ===================== Password Reset Routes =====================
    
    // Admin: User management page (generate reset links)
    get("/admin/users") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        // Only admin can access this page
        if (session.username != "admin") {
            exchange.redirect("/")
            return@get
        }
        val users = authProvider.listUsers()
        exchange.respondSummonPage(userManagementPage(session.username, users, null, null))
    }
    
    // Admin: Generate password reset link
    post("/admin/users/:username/reset") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        // Only admin can generate reset links
        if (session.username != "admin") {
            exchange.redirect("/")
            return@post
        }
        
        val targetUsername = exchange.pathParam("username") ?: ""
        val token = passwordResetService.createResetToken(targetUsername)
        
        val users = authProvider.listUsers()
        if (token != null) {
            val host = exchange.request.headers["Host"] ?: "building.yousef.codes"
            val scheme = if (host.contains("localhost")) "http" else "https"
            val resetLink = "$scheme://$host/reset-password?token=$token"
            exchange.respondSummonPage(userManagementPage(session.username, users, resetLink, targetUsername))
        } else {
            exchange.respondSummonPage(userManagementPage(session.username, users, null, null), 400)
        }
    }
    
    // User: Reset password page (accessed via link)
    get("/reset-password") { exchange ->
        val token = exchange.request.queryParameter("token")
        if (token.isNullOrBlank()) {
            exchange.respondSummonPage(resetPasswordPage(null, BuildingStrings.RESET_LINK_EXPIRED), 400)
            return@get
        }
        
        val username = passwordResetService.validateToken(token)
        if (username == null) {
            exchange.respondSummonPage(resetPasswordPage(null, BuildingStrings.RESET_LINK_EXPIRED), 400)
            return@get
        }
        
        exchange.respondSummonPage(resetPasswordPage(token, null))
    }
    
    // User: Submit new password
    post("/reset-password") { exchange ->
        val params = exchange.receiveParameters()
        val token = params["token"] ?: ""
        val password = params["password"] ?: ""
        val confirm = params["confirm"] ?: ""
        
        // Validate token first
        val username = passwordResetService.validateToken(token)
        if (username == null) {
            exchange.respondSummonPage(resetPasswordPage(null, BuildingStrings.RESET_LINK_EXPIRED), 400)
            return@post
        }
        
        // Validate password
        when {
            password.isBlank() -> {
                exchange.respondSummonPage(resetPasswordPage(token, BuildingStrings.PASSWORD_EMPTY), 400)
            }
            password != confirm -> {
                exchange.respondSummonPage(resetPasswordPage(token, BuildingStrings.PASSWORDS_DONT_MATCH), 400)
            }
            else -> {
                // Update password and consume token
                authProvider.updatePassword(username, password)
                passwordResetService.consumeToken(token)
                
                // Redirect to login with success message
                exchange.redirect("/login?reset=success")
            }
        }
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
    
    // Edit building page
    get("/buildings/:id/edit") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        val buildingId = exchange.pathParam("id") ?: ""
        val building = repository.getBuilding(buildingId)
        
        if (building == null) {
            exchange.redirect("/buildings")
            return@get
        }
        
        exchange.respondSummonPage(editBuildingPage(session.username, building, null))
    }
    
    // Update building
    post("/buildings/:id/edit") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        val buildingId = exchange.pathParam("id") ?: ""
        val building = repository.getBuilding(buildingId)
        
        if (building == null) {
            exchange.redirect("/buildings")
            return@post
        }
        
        val params = exchange.receiveParameters()
        val name = params["name"]?.trim() ?: ""
        val address = params["address"]?.trim() ?: ""
        
        if (name.isBlank()) {
            exchange.respondSummonPage(editBuildingPage(session.username, building, "اسم العمارة مطلوب"), 400)
            return@post
        }
        
        repository.upsertBuilding(building.copy(name = name, address = address))
        exchange.redirect("/buildings")
    }
    
    // Delete building confirmation page
    get("/buildings/:id/delete") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        val buildingId = exchange.pathParam("id") ?: ""
        val building = repository.getBuilding(buildingId)
        
        if (building == null) {
            exchange.redirect("/buildings")
            return@get
        }
        
        val unitCount = repository.listApartmentsByBuilding(buildingId).size
        exchange.respondSummonPage(deleteBuildingPage(session.username, building, unitCount))
    }
    
    // Delete building
    post("/buildings/:id/delete") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        val buildingId = exchange.pathParam("id") ?: ""
        val building = repository.getBuilding(buildingId)
        
        if (building == null) {
            exchange.redirect("/buildings")
            return@post
        }
        
        // Delete all related data
        val apartments = repository.listApartmentsByBuilding(buildingId)
        apartments.forEach { apartment ->
            val leases = repository.listLeases().filter { it.unitId == apartment.id }
            leases.forEach { lease ->
                repository.listPayments().filter { it.leaseId == lease.id }.forEach {
                    repository.deletePayment(it.id)
                }
                repository.deleteLease(lease.id)
            }
            repository.deleteApartment(apartment.id)
        }
        repository.deleteBuilding(buildingId)
        
        exchange.redirect("/buildings")
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
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
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
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { BuildingChangePasswordPage(username = username, errorMessage = errorMessage) }
)

private fun resetPasswordPage(token: String?, errorMessage: String?): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.RESET_PASSWORD_TITLE} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { ResetPasswordPage(token = token, errorMessage = errorMessage) }
)

private fun userManagementPage(
    username: String,
    users: List<String>,
    generatedLink: String?,
    targetUser: String?
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.USER_MANAGEMENT} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { UserManagementPage(username = username, users = users, generatedLink = generatedLink, targetUser = targetUser) }
)

private fun buildingDashboardPage(username: String, summary: code.yousef.portfolio.building.model.DashboardSummary): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.DASHBOARD} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
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
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
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
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
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
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
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
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { ImportPage(username = username, successMessage = successMessage, errorMessage = errorMessage) }
)

private fun editBuildingPage(
    username: String,
    building: Building,
    errorMessage: String?
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.EDIT_BUILDING} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { EditBuildingPage(username = username, building = building, errorMessage = errorMessage) }
)

private fun deleteBuildingPage(
    username: String,
    building: Building,
    unitCount: Int
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.DELETE_BUILDING} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { DeleteBuildingPage(username = username, building = building, unitCount = unitCount) }
)

// Multipart parsing helpers
private fun extractBoundary(contentType: String): String? {
    val boundaryPrefix = "boundary="
    val idx = contentType.indexOf(boundaryPrefix)
    if (idx < 0) return null
    return contentType.substring(idx + boundaryPrefix.length).trim().removeSurrounding("\"")
}

/**
 * Extract file bytes from multipart form data using pure byte operations.
 * This avoids string conversion which can corrupt binary data.
 */
private fun extractFileFromMultipart(body: ByteArray, boundary: String): ByteArray? {
    val boundaryBytes = "--$boundary".toByteArray(Charsets.US_ASCII)
    val crlfCrlf = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
    val crlf = "\r\n".toByteArray(Charsets.US_ASCII)
    
    // Find boundary positions
    var pos = 0
    while (pos < body.size) {
        val boundaryPos = indexOf(body, boundaryBytes, pos)
        if (boundaryPos < 0) break
        
        // Move past boundary and CRLF
        var headerStart = boundaryPos + boundaryBytes.size
        if (headerStart + 2 <= body.size && body[headerStart] == '\r'.code.toByte() && body[headerStart + 1] == '\n'.code.toByte()) {
            headerStart += 2
        }
        
        // Find end of headers (double CRLF)
        val headerEnd = indexOf(body, crlfCrlf, headerStart)
        if (headerEnd < 0) {
            pos = headerStart
            continue
        }
        
        // Check if this part has a filename (it's the file upload)
        val headerBytes = body.copyOfRange(headerStart, headerEnd)
        val headerStr = String(headerBytes, Charsets.ISO_8859_1)
        
        if (headerStr.contains("filename=") && headerStr.contains("Content-Type")) {
            // Content starts after \r\n\r\n
            val contentStart = headerEnd + 4
            
            // Find the next boundary to determine content end
            val nextBoundary = indexOf(body, boundaryBytes, contentStart)
            val contentEnd = if (nextBoundary > 0) {
                // Content ends before CRLF before next boundary
                var end = nextBoundary
                if (end >= 2 && body[end - 1] == '\n'.code.toByte() && body[end - 2] == '\r'.code.toByte()) {
                    end -= 2
                }
                end
            } else {
                body.size
            }
            
            if (contentStart < contentEnd) {
                val fileBytes = body.copyOfRange(contentStart, contentEnd)
                log.info("Extracted file: ${fileBytes.size} bytes from multipart body of ${body.size} bytes")
                return fileBytes
            }
        }
        
        pos = headerEnd + 4
    }
    
    log.warn("No file part found in multipart body")
    return null
}

/**
 * Find byte array needle in haystack starting from offset.
 */
private fun indexOf(haystack: ByteArray, needle: ByteArray, fromIndex: Int = 0): Int {
    outer@ for (i in fromIndex..(haystack.size - needle.size)) {
        for (j in needle.indices) {
            if (haystack[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}
