package code.yousef.portfolio.building.server

import code.yousef.portfolio.building.auth.BuildingAuthProvider
import code.yousef.portfolio.building.auth.BuildingSession
import code.yousef.portfolio.building.auth.PasswordResetService
import code.yousef.portfolio.building.bulk.BulkDateMode
import code.yousef.portfolio.building.bulk.BulkDateUnit
import code.yousef.portfolio.building.bulk.BulkDateUpdate
import code.yousef.portfolio.building.bulk.BulkOperationResult
import code.yousef.portfolio.building.bulk.BuildingBulkFormParser
import code.yousef.portfolio.building.bulk.parseBulkDate
import code.yousef.portfolio.building.import.ExcelImportService
import code.yousef.portfolio.building.model.Apartment
import code.yousef.portfolio.building.model.ApartmentWithDetails
import code.yousef.portfolio.building.model.Building
import code.yousef.portfolio.building.model.Lease
import code.yousef.portfolio.building.model.Payment
import code.yousef.portfolio.building.model.PaymentWithDetails
import code.yousef.portfolio.building.model.PaymentStatus
import code.yousef.portfolio.building.model.Tenant
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

    // Bulk building review
    post("/buildings/bulk/review") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        val params = exchange.receiveParameters()
        val action = normalizeBulkAction(params["bulkAction"], allowDates = false)
        val ids = BuildingBulkFormParser.selectedIds(params)

        if (ids.isEmpty()) {
            exchange.respondSummonPage(
                bulkErrorPage(session.username, BuildingStrings.BULK_REVIEW, BuildingStrings.SELECT_AT_LEAST_ONE, "/buildings"),
                400
            )
            return@post
        }

        val buildings = service.getBuildingsByIds(ids)
        if (buildings.isEmpty()) {
            exchange.respondSummonPage(
                bulkErrorPage(session.username, BuildingStrings.BULK_REVIEW, "السجلات المحددة لم تعد موجودة", "/buildings"),
                400
            )
            return@post
        }

        exchange.respondSummonPage(
            bulkBuildingsReviewPage(
                username = session.username,
                action = action,
                buildings = buildings,
                cascadePlan = if (action == "delete") service.planBuildingDeletion(ids) else null,
                errorMessage = null
            )
        )
    }

    // Bulk building apply
    post("/buildings/bulk/apply") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        val params = exchange.receiveParameters()
        val action = normalizeBulkAction(params["bulkAction"], allowDates = false)
        val ids = BuildingBulkFormParser.recordIds(params)

        if (ids.isEmpty()) {
            exchange.respondSummonPage(
                bulkErrorPage(session.username, BuildingStrings.BULK_RESULT, BuildingStrings.SELECT_AT_LEAST_ONE, "/buildings"),
                400
            )
            return@post
        }

        if (action == "delete") {
            val result = service.deleteBuildingsCascade(ids)
            exchange.respondSummonPage(
                bulkResultPage(session.username, result, "/buildings", "تم حذف العمارات المحددة")
            )
            return@post
        }

        val buildings = service.getBuildingsByIds(ids)
        val updates = try {
            buildBuildingUpdates(buildings, params)
        } catch (e: IllegalArgumentException) {
            exchange.respondSummonPage(
                bulkBuildingsReviewPage(session.username, "edit", buildings, null, e.message ?: "المدخلات غير صالحة"),
                400
            )
            return@post
        }
        val result = service.bulkUpdateBuildings(updates).withRequestedIds(ids)
        exchange.respondSummonPage(
            bulkResultPage(session.username, result, "/buildings", "تم تحديث العمارات المحددة")
        )
    }

    // Bulk apartment/unit review
    post("/buildings/:id/units/bulk/review") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        val buildingId = exchange.pathParam("id") ?: ""
        val building = repository.getBuilding(buildingId)

        if (building == null) {
            exchange.redirect("/buildings")
            return@post
        }

        val params = exchange.receiveParameters()
        val action = normalizeBulkAction(params["bulkAction"], allowDates = true)
        val ids = BuildingBulkFormParser.selectedIds(params)

        if (ids.isEmpty()) {
            exchange.respondSummonPage(
                bulkErrorPage(session.username, BuildingStrings.BULK_REVIEW, BuildingStrings.SELECT_AT_LEAST_ONE, "/buildings/$buildingId"),
                400
            )
            return@post
        }

        val apartments = service.getApartmentsWithDetailsByIds(ids)
            .filter { it.apartment.buildingId == buildingId }
        if (apartments.isEmpty()) {
            exchange.respondSummonPage(
                bulkErrorPage(session.username, BuildingStrings.BULK_REVIEW, "الشقق المحددة لم تعد موجودة", "/buildings/$buildingId"),
                400
            )
            return@post
        }

        exchange.respondSummonPage(
            bulkApartmentsReviewPage(
                username = session.username,
                building = building,
                action = action,
                apartments = apartments,
                cascadePlan = if (action == "delete") service.planApartmentDeletion(apartments.map { it.apartment.id }) else null,
                errorMessage = null
            )
        )
    }

    // Bulk apartment/unit apply
    post("/buildings/:id/units/bulk/apply") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        val buildingId = exchange.pathParam("id") ?: ""
        val building = repository.getBuilding(buildingId)

        if (building == null) {
            exchange.redirect("/buildings")
            return@post
        }

        val params = exchange.receiveParameters()
        val action = normalizeBulkAction(params["bulkAction"], allowDates = true)
        val ids = BuildingBulkFormParser.recordIds(params)
        val apartments = service.getApartmentsWithDetailsByIds(ids)
            .filter { it.apartment.buildingId == buildingId }
        val apartmentIds = apartments.map { it.apartment.id }

        if (ids.isEmpty()) {
            exchange.respondSummonPage(
                bulkErrorPage(session.username, BuildingStrings.BULK_RESULT, BuildingStrings.SELECT_AT_LEAST_ONE, "/buildings/$buildingId"),
                400
            )
            return@post
        }

        when (action) {
            "delete" -> {
                val result = service.deleteApartmentsCascade(apartmentIds).withRequestedIds(ids)
                exchange.respondSummonPage(
                    bulkResultPage(session.username, result, "/buildings/$buildingId", "تم حذف الشقق المحددة")
                )
            }
            "dates" -> {
                val fields = selectedDateFields(params)
                val update = try {
                    if (fields.isEmpty()) throw IllegalArgumentException("اختر حقل تاريخ واحداً على الأقل")
                    parseBulkDateUpdate(params)
                } catch (e: IllegalArgumentException) {
                    exchange.respondSummonPage(
                        bulkApartmentsReviewPage(session.username, building, "dates", apartments, null, e.message ?: "المدخلات غير صالحة"),
                        400
                    )
                    return@post
                }
                val result = try {
                    service.bulkUpdateLeaseDatesForApartments(apartmentIds, fields, update).withRequestedIds(ids)
                } catch (e: IllegalArgumentException) {
                    exchange.respondSummonPage(
                        bulkApartmentsReviewPage(session.username, building, "dates", apartments, null, e.message ?: "المدخلات غير صالحة"),
                        400
                    )
                    return@post
                }
                exchange.respondSummonPage(
                    bulkResultPage(session.username, result, "/buildings/$buildingId", "تم تحديث تواريخ العقود المحددة")
                )
            }
            else -> {
                val payload = try {
                    buildApartmentUpdates(apartments, params)
                } catch (e: IllegalArgumentException) {
                    exchange.respondSummonPage(
                        bulkApartmentsReviewPage(session.username, building, "edit", apartments, null, e.message ?: "المدخلات غير صالحة"),
                        400
                    )
                    return@post
                }
                val result = service.bulkUpdateApartments(payload.apartments, payload.tenants, payload.leases).withRequestedIds(ids)
                exchange.respondSummonPage(
                    bulkResultPage(session.username, result, "/buildings/$buildingId", "تم تحديث الشقق المحددة")
                )
            }
        }
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

    // Bulk payment review
    post("/payments/bulk/review") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        val params = exchange.receiveParameters()
        val action = normalizeBulkAction(params["bulkAction"], allowDates = true)
        val ids = BuildingBulkFormParser.selectedIds(params)

        if (ids.isEmpty()) {
            exchange.respondSummonPage(
                bulkErrorPage(session.username, BuildingStrings.BULK_REVIEW, BuildingStrings.SELECT_AT_LEAST_ONE, "/payments"),
                400
            )
            return@post
        }

        val payments = service.getPaymentsWithDetailsByIds(ids)
        if (payments.isEmpty()) {
            exchange.respondSummonPage(
                bulkErrorPage(session.username, BuildingStrings.BULK_REVIEW, "الدفعات المحددة لم تعد موجودة", "/payments"),
                400
            )
            return@post
        }

        exchange.respondSummonPage(
            bulkPaymentsReviewPage(
                username = session.username,
                action = action,
                payments = payments,
                cascadePlan = if (action == "delete") service.planPaymentDeletion(ids) else null,
                errorMessage = null
            )
        )
    }

    // Bulk payment apply
    post("/payments/bulk/apply") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        val params = exchange.receiveParameters()
        val action = normalizeBulkAction(params["bulkAction"], allowDates = true)
        val ids = BuildingBulkFormParser.recordIds(params)
        val payments = service.getPaymentsWithDetailsByIds(ids)

        if (ids.isEmpty()) {
            exchange.respondSummonPage(
                bulkErrorPage(session.username, BuildingStrings.BULK_RESULT, BuildingStrings.SELECT_AT_LEAST_ONE, "/payments"),
                400
            )
            return@post
        }

        when (action) {
            "delete" -> {
                val result = service.deletePayments(ids)
                exchange.respondSummonPage(
                    bulkResultPage(session.username, result, "/payments", "تم حذف الدفعات المحددة")
                )
            }
            "dates" -> {
                val fields = selectedDateFields(params)
                val update = try {
                    if (fields.isEmpty()) throw IllegalArgumentException("اختر حقل تاريخ واحداً على الأقل")
                    parseBulkDateUpdate(params)
                } catch (e: IllegalArgumentException) {
                    exchange.respondSummonPage(
                        bulkPaymentsReviewPage(session.username, "dates", payments, null, e.message ?: "المدخلات غير صالحة"),
                        400
                    )
                    return@post
                }
                val result = try {
                    service.bulkUpdatePaymentDates(ids, fields, update)
                } catch (e: IllegalArgumentException) {
                    exchange.respondSummonPage(
                        bulkPaymentsReviewPage(session.username, "dates", payments, null, e.message ?: "المدخلات غير صالحة"),
                        400
                    )
                    return@post
                }
                exchange.respondSummonPage(
                    bulkResultPage(session.username, result, "/payments", "تم تحديث تواريخ الدفعات المحددة")
                )
            }
            else -> {
                val updates = try {
                    buildPaymentUpdates(payments, params)
                } catch (e: IllegalArgumentException) {
                    exchange.respondSummonPage(
                        bulkPaymentsReviewPage(session.username, "edit", payments, null, e.message ?: "المدخلات غير صالحة"),
                        400
                    )
                    return@post
                }
                val result = service.bulkUpdatePayments(updates).withRequestedIds(ids)
                exchange.respondSummonPage(
                    bulkResultPage(session.username, result, "/payments", "تم تحديث الدفعات المحددة")
                )
            }
        }
    }

    // Quick payment status update
    post("/payments/:id/status") { exchange ->
        exchange.requireAuth() ?: return@post
        val paymentId = exchange.pathParam("id") ?: ""
        val params = exchange.receiveParameters()
        val redirectPath = safeRedirectPath(params["redirect"])
        val status = try {
            PaymentStatus.valueOf(params["status"].orEmpty())
        } catch (_: Exception) {
            exchange.redirect(redirectPath)
            return@post
        }

        service.updatePaymentStatus(paymentId, status)
        exchange.redirect(redirectPath)
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

    // Edit apartment page
    get("/apartments/:id/edit") { exchange ->
        val session = exchange.requireAuth() ?: return@get
        val apartmentId = exchange.pathParam("id") ?: ""
        val apartment = repository.getApartment(apartmentId)

        if (apartment == null) {
            exchange.redirect("/buildings")
            return@get
        }

        val building = repository.getBuilding(apartment.buildingId)
        if (building == null) {
            exchange.redirect("/buildings")
            return@get
        }

        val lease = repository.getLeaseByUnit(apartmentId)
        val tenant = lease?.let { repository.getTenant(it.tenantId) }

        exchange.respondSummonPage(editApartmentPage(session.username, building, apartment, tenant, lease, null))
    }

    // Update apartment
    post("/apartments/:id/edit") { exchange ->
        val session = exchange.requireAuth() ?: return@post
        val apartmentId = exchange.pathParam("id") ?: ""
        val apartment = repository.getApartment(apartmentId)

        if (apartment == null) {
            exchange.redirect("/buildings")
            return@post
        }

        val building = repository.getBuilding(apartment.buildingId)
        if (building == null) {
            exchange.redirect("/buildings")
            return@post
        }

        val params = exchange.receiveParameters()
        val unitNumber = params["unitNumber"]?.trim() ?: apartment.unitNumber
        val floor = params["floor"]?.trim()?.toIntOrNull()
        val apartmentNotes = params["apartmentNotes"]?.trim() ?: ""
        val tenantName = params["tenantName"]?.trim() ?: ""
        val tenantPhone = params["tenantPhone"]?.trim() ?: ""
        val annualRent = params["annualRent"]?.trim()?.toDoubleOrNull()
        val startDate = params["startDate"]?.trim() ?: ""
        val endDate = params["endDate"]?.trim() ?: ""
        val leaseNotes = params["leaseNotes"]?.trim() ?: ""

        // Update apartment
        repository.upsertApartment(
            apartment.copy(
                unitNumber = unitNumber,
                floor = floor,
                notes = apartmentNotes
            )
        )

        // Get or create tenant if name is provided
        val existingLease = repository.getLeaseByUnit(apartmentId)

        if (tenantName.isNotBlank()) {
            val tenantId = existingLease?.tenantId ?: java.util.UUID.randomUUID().toString()
            val existingTenant = existingLease?.let { repository.getTenant(it.tenantId) }

            repository.upsertTenant(
                code.yousef.portfolio.building.model.Tenant(
                    id = tenantId,
                    name = tenantName,
                    phone = tenantPhone,
                    email = existingTenant?.email ?: "",
                    nationalId = existingTenant?.nationalId ?: "",
                    notes = existingTenant?.notes ?: ""
                )
            )

            // Create or update lease if we have rent and dates
            if (annualRent != null && annualRent > 0 && startDate.isNotBlank() && endDate.isNotBlank()) {
                val leaseId = existingLease?.id ?: java.util.UUID.randomUUID().toString()
                repository.upsertLease(
                    code.yousef.portfolio.building.model.Lease(
                        id = leaseId,
                        unitId = apartmentId,
                        tenantId = tenantId,
                        annualRent = annualRent,
                        startDate = startDate,
                        endDate = endDate,
                        notes = leaseNotes
                    )
                )
            } else if (existingLease != null) {
                // Update existing lease
                repository.upsertLease(
                    existingLease.copy(
                        tenantId = tenantId,
                        annualRent = annualRent ?: existingLease.annualRent,
                        startDate = startDate.ifBlank { existingLease.startDate },
                        endDate = endDate.ifBlank { existingLease.endDate },
                        notes = leaseNotes
                    )
                )
            }
        }

        service.syncCurrentYearPayments()
        exchange.redirect("/buildings/${building.id}")
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
                service.syncCurrentYearPayments()
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

private fun bulkErrorPage(
    username: String,
    title: String,
    message: String,
    returnHref: String
): SummonPage = SummonPage(
    head = { head ->
        head.title("$title - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = { BulkErrorPage(username = username, title = title, message = message, returnHref = returnHref) }
)

private fun bulkResultPage(
    username: String,
    result: BulkOperationResult,
    returnHref: String,
    message: String
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.BULK_RESULT} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = {
        BulkResultPage(
            username = username,
            title = BuildingStrings.BULK_RESULT,
            result = result,
            returnHref = returnHref,
            message = message
        )
    }
)

private fun bulkBuildingsReviewPage(
    username: String,
    action: String,
    buildings: List<Building>,
    cascadePlan: code.yousef.portfolio.building.bulk.BulkCascadePlan?,
    errorMessage: String?
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.BULK_REVIEW} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = {
        BulkBuildingsReviewPage(
            username = username,
            action = action,
            buildings = buildings,
            cascadePlan = cascadePlan,
            errorMessage = errorMessage
        )
    }
)

private fun bulkApartmentsReviewPage(
    username: String,
    building: Building,
    action: String,
    apartments: List<ApartmentWithDetails>,
    cascadePlan: code.yousef.portfolio.building.bulk.BulkCascadePlan?,
    errorMessage: String?
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.BULK_REVIEW} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = {
        BulkApartmentsReviewPage(
            username = username,
            building = building,
            action = action,
            apartments = apartments,
            cascadePlan = cascadePlan,
            errorMessage = errorMessage
        )
    }
)

private fun bulkPaymentsReviewPage(
    username: String,
    action: String,
    payments: List<PaymentWithDetails>,
    cascadePlan: code.yousef.portfolio.building.bulk.BulkCascadePlan?,
    errorMessage: String?
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.BULK_REVIEW} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = {
        BulkPaymentsReviewPage(
            username = username,
            action = action,
            payments = payments,
            cascadePlan = cascadePlan,
            errorMessage = errorMessage
        )
    }
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

private fun editApartmentPage(
    username: String,
    building: Building,
    apartment: code.yousef.portfolio.building.model.Apartment,
    tenant: code.yousef.portfolio.building.model.Tenant?,
    lease: code.yousef.portfolio.building.model.Lease?,
    errorMessage: String?
): SummonPage = SummonPage(
    head = { head ->
        head.title("${BuildingStrings.EDIT_APARTMENT} - ${BuildingStrings.APP_TITLE}")
        head.meta("viewport", null, "width=device-width, initial-scale=1", null, null)
        head.meta("robots", "noindex", null, null, null)
        head.style(BuildingTheme.globalStyles)
        head.link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;600;700&display=swap"
        )
    },
    content = {
        EditApartmentPage(
            username = username,
            building = building,
            apartment = apartment,
            tenant = tenant,
            lease = lease,
            errorMessage = errorMessage
        )
    }
)

private data class ApartmentBulkUpdatePayload(
    val apartments: List<Apartment>,
    val tenants: List<Tenant>,
    val leases: List<Lease>
)

private fun normalizeBulkAction(action: String?, allowDates: Boolean): String {
    return when (action) {
        "delete" -> "delete"
        "dates" -> if (allowDates) "dates" else "edit"
        else -> "edit"
    }
}

private fun selectedDateFields(params: Map<String, String>): Set<String> = params.keys
    .filter { it.startsWith("field_") }
    .map { it.removePrefix("field_") }
    .filter { it.isNotBlank() }
    .toSet()

private fun safeRedirectPath(value: String?): String {
    val redirect = value?.trim().orEmpty()
    return if (redirect.startsWith("/") && !redirect.startsWith("//")) redirect else "/payments"
}

private fun BulkOperationResult.withRequestedIds(ids: Collection<String>): BulkOperationResult {
    val requested = ids.toSet().size
    return copy(
        requested = requested,
        skipped = (requested - applied).coerceAtLeast(0)
    )
}

private fun parseBulkDateUpdate(params: Map<String, String>): BulkDateUpdate {
    return if (params["dateMode"] == "shift") {
        val amount = params["shiftAmount"]?.trim()?.toLongOrNull()
            ?: throw IllegalArgumentException("${BuildingStrings.SHIFT_AMOUNT} مطلوب كرقم صحيح")
        val unit = when (params["shiftUnit"]) {
            "months" -> BulkDateUnit.MONTHS
            else -> BulkDateUnit.DAYS
        }
        BulkDateUpdate(
            mode = BulkDateMode.SHIFT,
            shiftAmount = amount,
            shiftUnit = unit
        )
    } else {
        BulkDateUpdate(
            mode = BulkDateMode.SET,
            setDate = parseBulkDate(params["setDate"].orEmpty(), BuildingStrings.SET_DATE)
        )
    }
}

private fun buildBuildingUpdates(buildings: List<Building>, params: Map<String, String>): List<Building> =
    buildings.map { building ->
        val name = params["name_${building.id}"]?.trim().orEmpty()
        if (name.isBlank()) throw IllegalArgumentException("اسم العمارة مطلوب")
        building.copy(
            name = name,
            address = params["address_${building.id}"]?.trim().orEmpty()
        )
    }

private fun buildApartmentUpdates(
    details: List<ApartmentWithDetails>,
    params: Map<String, String>
): ApartmentBulkUpdatePayload {
    val apartments = mutableListOf<Apartment>()
    val tenants = mutableListOf<Tenant>()
    val leases = mutableListOf<Lease>()

    details.forEach { detail ->
        val id = detail.apartment.id
        val unitNumber = params["unitNumber_$id"]?.trim().orEmpty()
        if (unitNumber.isBlank()) throw IllegalArgumentException("رقم الشقة مطلوب")

        val floorText = params["floor_$id"]?.trim().orEmpty()
        val floor = if (floorText.isBlank()) {
            null
        } else {
            floorText.toIntOrNull() ?: throw IllegalArgumentException("الدور يجب أن يكون رقماً صحيحاً")
        }

        apartments += detail.apartment.copy(
            unitNumber = unitNumber,
            floor = floor,
            notes = params["apartmentNotes_$id"]?.trim().orEmpty()
        )

        detail.tenant?.let { tenant ->
            val tenantName = params["tenantName_$id"]?.trim().orEmpty()
            if (tenantName.isBlank()) throw IllegalArgumentException("اسم المستأجر مطلوب")
            tenants += tenant.copy(
                name = tenantName,
                phone = params["tenantPhone_$id"]?.trim().orEmpty(),
                email = params["tenantEmail_$id"]?.trim().orEmpty(),
                nationalId = params["tenantNationalId_$id"]?.trim().orEmpty(),
                notes = params["tenantNotes_$id"]?.trim().orEmpty()
            )
        }

        detail.currentLease?.let { lease ->
            val annualRent = params["annualRent_$id"]?.trim()?.toDoubleOrNull()
                ?: throw IllegalArgumentException("الإيجار السنوي يجب أن يكون رقماً")
            if (annualRent < 0) throw IllegalArgumentException("الإيجار السنوي لا يمكن أن يكون سالباً")
            val startDate = params["startDate_$id"]?.trim().orEmpty()
            val endDate = params["endDate_$id"]?.trim().orEmpty()
            parseBulkDate(startDate, BuildingStrings.START_DATE)
            parseBulkDate(endDate, BuildingStrings.END_DATE)

            leases += lease.copy(
                annualRent = annualRent,
                startDate = startDate,
                endDate = endDate,
                notes = params["leaseNotes_$id"]?.trim().orEmpty()
            )
        }
    }

    return ApartmentBulkUpdatePayload(apartments = apartments, tenants = tenants, leases = leases)
}

private fun buildPaymentUpdates(details: List<PaymentWithDetails>, params: Map<String, String>): List<Payment> =
    details.map { detail ->
        val payment = detail.payment
        val id = payment.id
        val paymentNumber = params["paymentNumber_$id"]?.trim()?.toIntOrNull()
            ?: throw IllegalArgumentException("رقم الدفعة يجب أن يكون رقماً صحيحاً")
        if (paymentNumber <= 0) throw IllegalArgumentException("رقم الدفعة يجب أن يكون أكبر من صفر")

        val amount = params["amount_$id"]?.trim()?.toDoubleOrNull()
            ?: throw IllegalArgumentException("مبلغ الدفعة يجب أن يكون رقماً")
        if (amount < 0) throw IllegalArgumentException("مبلغ الدفعة لا يمكن أن يكون سالباً")

        val periodStart = params["periodStart_$id"]?.trim().orEmpty()
        val periodEnd = params["periodEnd_$id"]?.trim().orEmpty()
        val dueDate = params["dueDate_$id"]?.trim().orEmpty()
        val paidDate = params["paidDate_$id"]?.trim().orEmpty().ifBlank { null }
        parseBulkDate(periodStart, "${BuildingStrings.PERIOD} - ${BuildingStrings.FROM}")
        parseBulkDate(periodEnd, "${BuildingStrings.PERIOD} - ${BuildingStrings.TO}")
        parseBulkDate(dueDate, BuildingStrings.DUE_DATE)
        paidDate?.let { parseBulkDate(it, BuildingStrings.PAID_DATE) }

        val status = try {
            PaymentStatus.valueOf(params["status_$id"].orEmpty())
        } catch (_: Exception) {
            throw IllegalArgumentException("حالة الدفعة غير صالحة")
        }

        payment.copy(
            paymentNumber = paymentNumber,
            amount = amount,
            periodStart = periodStart,
            periodEnd = periodEnd,
            dueDate = dueDate,
            paidDate = paidDate,
            status = status,
            notes = params["notes_$id"]?.trim().orEmpty()
        )
    }

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
