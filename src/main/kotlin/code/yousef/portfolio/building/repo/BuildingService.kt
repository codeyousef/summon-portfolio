package code.yousef.portfolio.building.repo

import code.yousef.portfolio.building.bulk.BulkCascadePlan
import code.yousef.portfolio.building.bulk.BulkDateUpdate
import code.yousef.portfolio.building.bulk.BulkOperationResult
import code.yousef.portfolio.building.bulk.applyBulkDateUpdate
import code.yousef.portfolio.building.bulk.normalizePaymentStatusForDueDate
import code.yousef.portfolio.building.bulk.orphanedTenantIdsAfterRemovingLeases
import code.yousef.portfolio.building.bulk.planApartmentCascadeDelete
import code.yousef.portfolio.building.bulk.planBuildingCascadeDelete
import code.yousef.portfolio.building.bulk.planPaymentDelete
import code.yousef.portfolio.building.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Service layer for building management operations.
 * Provides higher-level operations and view models.
 */
class BuildingService(private val repository: BuildingRepository) {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getDashboardSummary(): DashboardSummary {
        val buildings = repository.listBuildings()
        val apartments = repository.listApartments()
        val leases = repository.listLeases()
        val payments = repository.listPayments()
        val tenants = repository.listTenants()

        val today = LocalDate.now()

        // Build lookup maps
        val buildingMap = buildings.associateBy { it.id }
        val apartmentMap = apartments.associateBy { it.id }
        val leaseMap = leases.associateBy { it.id }
        val tenantMap = tenants.associateBy { it.id }

        // Find apartments with active leases
        // A lease is considered active if:
        // 1. endDate is parseable and not before today, OR
        // 2. endDate is empty/unparseable but the lease has rent > 0 (assume active)
        val apartmentsWithLeases = leases
            .filter { lease ->
                try {
                    val endDate = LocalDate.parse(lease.endDate, dateFormatter)
                    !endDate.isBefore(today)
                } catch (_: Exception) {
                    // If we can't parse the date, check if lease has any rent (assume active if so)
                    lease.annualRent > 0 && lease.endDate.isNotBlank()
                }
            }
            .map { it.unitId }
            .toSet()

        // Update payment statuses based on current date
        val updatedPayments = payments.map { payment ->
            if (payment.status == PaymentStatus.PENDING) {
                try {
                    val dueDate = LocalDate.parse(payment.dueDate, dateFormatter)
                    if (dueDate.isBefore(today)) {
                        payment.copy(status = PaymentStatus.OVERDUE)
                    } else payment
                } catch (_: Exception) {
                    payment
                }
            } else payment
        }

        // Get upcoming payments (pending, due within 30 days)
        val upcomingPayments = updatedPayments
            .filter { it.status == PaymentStatus.PENDING }
            .filter { payment ->
                try {
                    val dueDate = LocalDate.parse(payment.dueDate, dateFormatter)
                    !dueDate.isBefore(today) && dueDate.isBefore(today.plusDays(30))
                } catch (_: Exception) {
                    false
                }
            }
            .sortedBy { it.dueDate }
            .map { payment ->
                val lease = leaseMap[payment.leaseId]
                val apartment = lease?.let { apartmentMap[it.unitId] }
                val building = apartment?.let { buildingMap[it.buildingId] }
                val tenant = lease?.let { tenantMap[it.tenantId] }
                PaymentWithDetails(payment, lease, apartment, building, tenant)
            }

        // Get overdue payments
        val overduePayments = updatedPayments
            .filter { it.status == PaymentStatus.OVERDUE }
            .sortedBy { it.dueDate }
            .map { payment ->
                val lease = leaseMap[payment.leaseId]
                val apartment = lease?.let { apartmentMap[it.unitId] }
                val building = apartment?.let { buildingMap[it.buildingId] }
                val tenant = lease?.let { tenantMap[it.tenantId] }
                PaymentWithDetails(payment, lease, apartment, building, tenant)
            }

        // Calculate monthly income (annual rent / 12 for active leases)
        val totalMonthlyIncome = leases
            .filter { it.unitId in apartmentsWithLeases }
            .sumOf { it.annualRent / 12.0 }

        return DashboardSummary(
            totalBuildings = buildings.size,
            totalUnits = apartments.size,
            occupiedUnits = apartmentsWithLeases.size,
            vacantUnits = apartments.size - apartmentsWithLeases.size,
            totalMonthlyIncome = totalMonthlyIncome,
            upcomingPayments = upcomingPayments,
            overduePayments = overduePayments
        )
    }

    fun getApartmentsWithDetails(buildingId: String? = null): List<ApartmentWithDetails> {
        val apartments = if (buildingId != null) {
            repository.listApartmentsByBuilding(buildingId)
        } else {
            repository.listApartments()
        }
        
        val buildings = repository.listBuildings().associateBy { it.id }
        val leases = repository.listLeases()
        val tenants = repository.listTenants().associateBy { it.id }
        val payments = repository.listPayments()

        val today = LocalDate.now()

        return apartments.map { apartment ->
            val building = buildings[apartment.buildingId]
            
            // Find current lease (not expired)
            // If date parsing fails but lease has rent, consider it active
            val currentLease = leases
                .filter { it.unitId == apartment.id }
                .filter { lease ->
                    try {
                        val endDate = LocalDate.parse(lease.endDate, dateFormatter)
                        !endDate.isBefore(today)
                    } catch (_: Exception) {
                        // If we can't parse the date, check if lease has rent (assume active)
                        lease.annualRent > 0
                    }
                }
                .maxByOrNull { it.endDate.ifBlank { "9999-99-99" } }

            val tenant = currentLease?.let { tenants[it.tenantId] }
            val apartmentPayments = currentLease?.let { lease ->
                payments.filter { it.leaseId == lease.id }.sortedBy { it.paymentNumber }
            } ?: emptyList()

            ApartmentWithDetails(apartment, building, currentLease, tenant, apartmentPayments)
        }.sortedBy { it.apartment.unitNumber }
    }

    fun getPaymentsWithDetails(
        buildingId: String? = null,
        statusFilter: PaymentStatus? = null
    ): List<PaymentWithDetails> {
        val payments = repository.listPayments()
        val leases = repository.listLeases().associateBy { it.id }
        val apartments = repository.listApartments().associateBy { it.id }
        val buildings = repository.listBuildings().associateBy { it.id }
        val tenants = repository.listTenants().associateBy { it.id }

        val today = LocalDate.now()

        return payments
            .map { payment ->
                // Update status if overdue
                val updatedPayment = if (payment.status == PaymentStatus.PENDING) {
                    try {
                        val dueDate = LocalDate.parse(payment.dueDate, dateFormatter)
                        if (dueDate.isBefore(today)) {
                            payment.copy(status = PaymentStatus.OVERDUE)
                        } else payment
                    } catch (_: Exception) {
                        payment
                    }
                } else payment

                val lease = leases[updatedPayment.leaseId]
                val apartment = lease?.let { apartments[it.unitId] }
                val building = apartment?.let { buildings[it.buildingId] }
                val tenant = lease?.let { tenants[it.tenantId] }
                PaymentWithDetails(updatedPayment, lease, apartment, building, tenant)
            }
            .filter { detail ->
                if (buildingId != null) {
                    detail.building?.id == buildingId
                } else true
            }
            .filter { detail ->
                if (statusFilter != null) {
                    detail.payment.status == statusFilter
                } else true
            }
            .sortedBy { it.payment.dueDate }
    }

    fun markPaymentAsPaid(paymentId: String, paidDate: String = LocalDate.now().format(dateFormatter)) {
        val payments = repository.listPayments()
        val payment = payments.find { it.id == paymentId } ?: return
        
        repository.upsertPayment(
            payment.copy(
                status = PaymentStatus.PAID,
                paidDate = paidDate
            )
        )
    }

    fun getBuildingsByIds(ids: Collection<String>): List<Building> {
        val requested = ids.toSet()
        return repository.listBuildings().filter { it.id in requested }
    }

    fun getApartmentsWithDetailsByIds(ids: Collection<String>): List<ApartmentWithDetails> {
        val requested = ids.toSet()
        val apartments = repository.listApartments().filter { it.id in requested }
        val buildings = repository.listBuildings().associateBy { it.id }
        val leases = repository.listLeases()
        val tenants = repository.listTenants().associateBy { it.id }
        val payments = repository.listPayments()
        val today = LocalDate.now()

        return apartments.map { apartment ->
            val currentLease = leases
                .filter { it.unitId == apartment.id }
                .filter { lease ->
                    try {
                        val endDate = LocalDate.parse(lease.endDate, dateFormatter)
                        !endDate.isBefore(today)
                    } catch (_: Exception) {
                        lease.annualRent > 0
                    }
                }
                .maxByOrNull { it.endDate.ifBlank { "9999-99-99" } }
            ApartmentWithDetails(
                apartment = apartment,
                building = buildings[apartment.buildingId],
                currentLease = currentLease,
                tenant = currentLease?.let { tenants[it.tenantId] },
                payments = currentLease?.let { lease ->
                    payments.filter { it.leaseId == lease.id }.sortedBy { it.paymentNumber }
                } ?: emptyList()
            )
        }.sortedBy { it.apartment.unitNumber }
    }

    fun getPaymentsWithDetailsByIds(ids: Collection<String>): List<PaymentWithDetails> {
        val requested = ids.toSet()
        return getPaymentsWithDetails().filter { it.payment.id in requested }
    }

    fun planBuildingDeletion(ids: Collection<String>): BulkCascadePlan =
        planBuildingCascadeDelete(
            selectedIds = ids,
            buildings = repository.listBuildings(),
            apartments = repository.listApartments(),
            leases = repository.listLeases(),
            payments = repository.listPayments()
        )

    fun planApartmentDeletion(ids: Collection<String>): BulkCascadePlan =
        planApartmentCascadeDelete(
            selectedIds = ids,
            apartments = repository.listApartments(),
            leases = repository.listLeases(),
            payments = repository.listPayments()
        )

    fun planPaymentDeletion(ids: Collection<String>): BulkCascadePlan =
        planPaymentDelete(ids, repository.listPayments())

    fun bulkUpdateBuildings(updates: List<Building>): BulkOperationResult {
        val existingIds = repository.listBuildings().map { it.id }.toSet()
        val applicable = updates.filter { it.id in existingIds }
        applicable.forEach(repository::upsertBuilding)
        return BulkOperationResult(
            requested = updates.map { it.id }.distinct().size,
            applied = applicable.size,
            skipped = updates.map { it.id }.distinct().size - applicable.size
        )
    }

    fun bulkUpdateApartments(
        apartments: List<Apartment>,
        tenants: List<Tenant>,
        leases: List<Lease>
    ): BulkOperationResult {
        val existingApartmentIds = repository.listApartments().map { it.id }.toSet()
        val applicableApartments = apartments.filter { it.id in existingApartmentIds }
        val existingTenantIds = repository.listTenants().map { it.id }.toSet()
        val existingLeaseIds = repository.listLeases().map { it.id }.toSet()

        applicableApartments.forEach(repository::upsertApartment)
        tenants.filter { it.id in existingTenantIds }.forEach(repository::upsertTenant)
        leases.filter { it.id in existingLeaseIds }.forEach(repository::upsertLease)

        return BulkOperationResult(
            requested = apartments.map { it.id }.distinct().size,
            applied = applicableApartments.size,
            skipped = apartments.map { it.id }.distinct().size - applicableApartments.size
        )
    }

    fun bulkUpdatePayments(updates: List<Payment>): BulkOperationResult {
        val existingIds = repository.listPayments().map { it.id }.toSet()
        val applicable = updates.filter { it.id in existingIds }
        applicable.forEach(repository::upsertPayment)
        return BulkOperationResult(
            requested = updates.map { it.id }.distinct().size,
            applied = applicable.size,
            skipped = updates.map { it.id }.distinct().size - applicable.size
        )
    }

    fun bulkUpdatePaymentDates(
        ids: Collection<String>,
        fields: Set<String>,
        update: BulkDateUpdate,
        today: LocalDate = LocalDate.now()
    ): BulkOperationResult {
        val requested = ids.toSet()
        val payments = repository.listPayments().filter { it.id in requested }
        val updates = payments.map { payment ->
            var updated = payment
            var dueDateChanged = false
            fields.forEach { field ->
                updated = when (field) {
                    "periodStart" -> updated.copy(periodStart = applyBulkDateUpdate(updated.periodStart, update, "بداية الفترة"))
                    "periodEnd" -> updated.copy(periodEnd = applyBulkDateUpdate(updated.periodEnd, update, "نهاية الفترة"))
                    "dueDate" -> {
                        dueDateChanged = true
                        updated.copy(dueDate = applyBulkDateUpdate(updated.dueDate, update, "تاريخ الاستحقاق"))
                    }
                    "paidDate" -> updated.copy(paidDate = applyBulkDateUpdate(updated.paidDate, update, "تاريخ السداد"))
                    else -> updated
                }
            }
            if (dueDateChanged) {
                updated = updated.copy(status = normalizePaymentStatusForDueDate(updated, today))
            }
            updated
        }
        updates.forEach(repository::upsertPayment)
        return BulkOperationResult(
            requested = requested.size,
            applied = payments.size,
            skipped = requested.size - payments.size
        )
    }

    fun bulkUpdateLeaseDatesForApartments(
        apartmentIds: Collection<String>,
        fields: Set<String>,
        update: BulkDateUpdate
    ): BulkOperationResult {
        val requested = apartmentIds.toSet()
        val leases = repository.listLeases()
            .filter { it.unitId in requested }
            .groupBy { it.unitId }
            .mapNotNull { (_, unitLeases) -> unitLeases.maxByOrNull { it.endDate.ifBlank { "9999-99-99" } } }

        val updates = leases.map { lease ->
            var updated = lease
            fields.forEach { field ->
                updated = when (field) {
                    "startDate" -> updated.copy(startDate = applyBulkDateUpdate(updated.startDate, update, "تاريخ بداية العقد"))
                    "endDate" -> updated.copy(endDate = applyBulkDateUpdate(updated.endDate, update, "تاريخ نهاية العقد"))
                    else -> updated
                }
            }
            updated
        }
        updates.forEach(repository::upsertLease)

        return BulkOperationResult(
            requested = requested.size,
            applied = leases.size,
            skipped = requested.size - leases.size
        )
    }

    fun deleteBuildingsCascade(ids: Collection<String>): BulkOperationResult {
        val requested = ids.toSet()
        val buildings = repository.listBuildings().filter { it.id in requested }
        val apartments = repository.listApartments()
        val leases = repository.listLeases()
        val payments = repository.listPayments()
        val removedApartmentIds = apartments.filter { it.buildingId in buildings.map(Building::id).toSet() }.map(Apartment::id).toSet()
        val removedLeases = leases.filter { it.unitId in removedApartmentIds }
        val removedLeaseIds = removedLeases.map(Lease::id).toSet()
        val removedTenantIds = orphanedTenantIdsAfterRemovingLeases(removedLeases, leases)

        payments.filter { it.leaseId in removedLeaseIds }.forEach { repository.deletePayment(it.id) }
        removedLeases.forEach { repository.deleteLease(it.id) }
        removedApartmentIds.forEach(repository::deleteApartment)
        removedTenantIds.forEach(repository::deleteTenant)
        buildings.forEach { repository.deleteBuilding(it.id) }

        return BulkOperationResult(requested.size, buildings.size, requested.size - buildings.size)
    }

    fun deleteApartmentsCascade(ids: Collection<String>): BulkOperationResult {
        val requested = ids.toSet()
        val apartments = repository.listApartments().filter { it.id in requested }
        val leases = repository.listLeases()
        val payments = repository.listPayments()
        val removedApartmentIds = apartments.map(Apartment::id).toSet()
        val removedLeases = leases.filter { it.unitId in removedApartmentIds }
        val removedLeaseIds = removedLeases.map(Lease::id).toSet()
        val removedTenantIds = orphanedTenantIdsAfterRemovingLeases(removedLeases, leases)

        payments.filter { it.leaseId in removedLeaseIds }.forEach { repository.deletePayment(it.id) }
        removedLeases.forEach { repository.deleteLease(it.id) }
        apartments.forEach { repository.deleteApartment(it.id) }
        removedTenantIds.forEach(repository::deleteTenant)

        return BulkOperationResult(requested.size, apartments.size, requested.size - apartments.size)
    }

    fun deletePayments(ids: Collection<String>): BulkOperationResult {
        val requested = ids.toSet()
        val payments = repository.listPayments().filter { it.id in requested }
        payments.forEach { repository.deletePayment(it.id) }
        return BulkOperationResult(requested.size, payments.size, requested.size - payments.size)
    }
}
