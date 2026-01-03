package code.yousef.portfolio.building.repo

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
        val apartmentsWithLeases = leases
            .filter { lease ->
                try {
                    val endDate = LocalDate.parse(lease.endDate, dateFormatter)
                    !endDate.isBefore(today)
                } catch (_: Exception) {
                    false
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
            val currentLease = leases
                .filter { it.unitId == apartment.id }
                .filter { lease ->
                    try {
                        val endDate = LocalDate.parse(lease.endDate, dateFormatter)
                        !endDate.isBefore(today)
                    } catch (_: Exception) {
                        false
                    }
                }
                .maxByOrNull { it.endDate }

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
}
