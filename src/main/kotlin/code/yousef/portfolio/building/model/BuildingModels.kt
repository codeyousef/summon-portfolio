package code.yousef.portfolio.building.model

import kotlinx.serialization.Serializable

/**
 * عمارة - Building entity representing a property
 */
@Serializable
data class Building(
    val id: String,
    val name: String,           // e.g., "عمارة ريم"
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * شقة - Apartment/unit within a building
 * Named "Apartment" to avoid conflict with Kotlin's Unit type
 */
@Serializable
data class Apartment(
    val id: String,
    val buildingId: String,
    val unitNumber: String,     // e.g., "شقة 1", "شقة 2"
    val floor: Int? = null,
    val notes: String = ""
)

/**
 * مستأجر - Tenant information
 */
@Serializable
data class Tenant(
    val id: String,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val nationalId: String = "",
    val notes: String = ""
)

/**
 * عقد إيجار - Lease/rental contract
 */
@Serializable
data class Lease(
    val id: String,
    val unitId: String,
    val tenantId: String,
    val annualRent: Double,     // الإيجار السنوي
    val startDate: String,      // yyyy-MM-dd format
    val endDate: String,        // yyyy-MM-dd format
    val notes: String = ""
)

/**
 * دفعة - Payment record
 */
@Serializable
data class Payment(
    val id: String,
    val leaseId: String,
    val paymentNumber: Int,     // 1 = الدفعة الأولى, 2 = الدفعة الثانية
    val amount: Double,         // مبلغ الدفعة
    val periodStart: String,    // بداية فترة الدفعة (yyyy-MM-dd)
    val periodEnd: String,      // نهاية فترة الدفعة (yyyy-MM-dd)
    val dueDate: String,        // تاريخ الاستحقاق (yyyy-MM-dd)
    val paidDate: String? = null,   // تاريخ السداد (null if not paid)
    val status: PaymentStatus,
    val notes: String = ""
)

@Serializable
enum class PaymentStatus {
    PAID,       // تم السداد
    PENDING,    // لم يسدد بعد
    OVERDUE     // متأخر
}

/**
 * View model combining data for display
 */
@Serializable
data class ApartmentWithDetails(
    val apartment: Apartment,
    val building: Building?,
    val currentLease: Lease?,
    val tenant: Tenant?,
    val payments: List<Payment>
)

@Serializable
data class PaymentWithDetails(
    val payment: Payment,
    val lease: Lease?,
    val apartment: Apartment?,
    val building: Building?,
    val tenant: Tenant?
)

/**
 * Dashboard summary data
 */
@Serializable
data class DashboardSummary(
    val totalBuildings: Int,
    val totalUnits: Int,
    val occupiedUnits: Int,
    val vacantUnits: Int,
    val totalMonthlyIncome: Double,
    val upcomingPayments: List<PaymentWithDetails>,
    val overduePayments: List<PaymentWithDetails>
)
