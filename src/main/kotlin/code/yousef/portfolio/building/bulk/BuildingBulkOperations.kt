package code.yousef.portfolio.building.bulk

import code.yousef.portfolio.building.model.Apartment
import code.yousef.portfolio.building.model.Building
import code.yousef.portfolio.building.model.Lease
import code.yousef.portfolio.building.model.Payment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val bulkDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Aether's form helper returns a Map<String, String>, so bulk forms use unique
 * key prefixes instead of repeated field names.
 */
object BuildingBulkFormParser {
    fun selectedIds(params: Map<String, String>): List<String> = params.keys
        .filter { it.startsWith("select_") }
        .map { it.removePrefix("select_") }
        .filter { it.isNotBlank() }
        .distinct()

    fun recordIds(params: Map<String, String>): List<String> = params.entries
        .filter { (key, value) -> key.startsWith("record_") && value.isNotBlank() }
        .sortedBy { (key, _) -> key.removePrefix("record_").toIntOrNull() ?: Int.MAX_VALUE }
        .map { it.value }
        .distinct()
}

enum class BulkDateMode { SET, SHIFT }

enum class BulkDateUnit { DAYS, MONTHS }

data class BulkDateUpdate(
    val mode: BulkDateMode,
    val setDate: LocalDate? = null,
    val shiftAmount: Long = 0,
    val shiftUnit: BulkDateUnit = BulkDateUnit.DAYS
)

data class BulkCascadePlan(
    val requested: Int,
    val existing: Int,
    val buildings: Int = 0,
    val apartments: Int = 0,
    val leases: Int = 0,
    val payments: Int = 0,
    val tenants: Int = 0
) {
    val skipped: Int get() = (requested - existing).coerceAtLeast(0)
}

data class BulkOperationResult(
    val requested: Int,
    val applied: Int,
    val skipped: Int
)

fun parseBulkDate(value: String, label: String): LocalDate {
    val normalized = value.trim()
    if (normalized.isBlank()) {
        throw IllegalArgumentException("$label مطلوب بصيغة yyyy-MM-dd")
    }
    return try {
        LocalDate.parse(normalized, bulkDateFormatter)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("$label يجب أن يكون بصيغة yyyy-MM-dd")
    }
}

fun applyBulkDateUpdate(currentValue: String?, update: BulkDateUpdate, label: String): String {
    return when (update.mode) {
        BulkDateMode.SET -> {
            val setDate = update.setDate ?: throw IllegalArgumentException("$label مطلوب بصيغة yyyy-MM-dd")
            setDate.format(bulkDateFormatter)
        }
        BulkDateMode.SHIFT -> {
            val currentDate = parseBulkDate(currentValue.orEmpty(), label)
            val shifted = when (update.shiftUnit) {
                BulkDateUnit.DAYS -> currentDate.plusDays(update.shiftAmount)
                BulkDateUnit.MONTHS -> currentDate.plusMonths(update.shiftAmount)
            }
            shifted.format(bulkDateFormatter)
        }
    }
}

fun planBuildingCascadeDelete(
    selectedIds: Collection<String>,
    buildings: List<Building>,
    apartments: List<Apartment>,
    leases: List<Lease>,
    payments: List<Payment>
): BulkCascadePlan {
    val selected = selectedIds.toSet()
    val existingBuildings = buildings.filter { it.id in selected }
    val removedApartmentIds = apartments.filter { it.buildingId in existingBuildings.map(Building::id).toSet() }.map(Apartment::id).toSet()
    val removedLeases = leases.filter { it.unitId in removedApartmentIds }
    val removedLeaseIds = removedLeases.map(Lease::id).toSet()
    val removedPayments = payments.filter { it.leaseId in removedLeaseIds }
    val removedTenants = orphanedTenantIdsAfterRemovingLeases(removedLeases, leases)

    return BulkCascadePlan(
        requested = selected.size,
        existing = existingBuildings.size,
        buildings = existingBuildings.size,
        apartments = removedApartmentIds.size,
        leases = removedLeases.size,
        payments = removedPayments.size,
        tenants = removedTenants.size
    )
}

fun planApartmentCascadeDelete(
    selectedIds: Collection<String>,
    apartments: List<Apartment>,
    leases: List<Lease>,
    payments: List<Payment>
): BulkCascadePlan {
    val selected = selectedIds.toSet()
    val existingApartments = apartments.filter { it.id in selected }
    val removedApartmentIds = existingApartments.map(Apartment::id).toSet()
    val removedLeases = leases.filter { it.unitId in removedApartmentIds }
    val removedLeaseIds = removedLeases.map(Lease::id).toSet()
    val removedPayments = payments.filter { it.leaseId in removedLeaseIds }
    val removedTenants = orphanedTenantIdsAfterRemovingLeases(removedLeases, leases)

    return BulkCascadePlan(
        requested = selected.size,
        existing = existingApartments.size,
        apartments = existingApartments.size,
        leases = removedLeases.size,
        payments = removedPayments.size,
        tenants = removedTenants.size
    )
}

fun planPaymentDelete(selectedIds: Collection<String>, payments: List<Payment>): BulkCascadePlan {
    val selected = selectedIds.toSet()
    val existingPayments = payments.count { it.id in selected }
    return BulkCascadePlan(
        requested = selected.size,
        existing = existingPayments,
        payments = existingPayments
    )
}

fun orphanedTenantIdsAfterRemovingLeases(removedLeases: List<Lease>, allLeases: List<Lease>): Set<String> {
    val removedLeaseIds = removedLeases.map(Lease::id).toSet()
    val removedTenantIds = removedLeases.map(Lease::tenantId).filter { it.isNotBlank() }.toSet()
    val remainingTenantIds = allLeases
        .filter { it.id !in removedLeaseIds }
        .map(Lease::tenantId)
        .filter { it.isNotBlank() }
        .toSet()
    return removedTenantIds - remainingTenantIds
}
