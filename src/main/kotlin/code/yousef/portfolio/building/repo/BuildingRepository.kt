package code.yousef.portfolio.building.repo

import code.yousef.firestore.PortfolioFirestoreCollections
import code.yousef.firestore.PortfolioFirestoreDocument
import code.yousef.firestore.PortfolioFirestoreStore
import code.yousef.firestore.sourcePortfolioFirestoreStore
import code.yousef.portfolio.building.model.*
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Migration-aware Firestore repository for building management data.
 * Reads use the configured authority; every mutation is durably mirrored by the shared
 * Portfolio Firestore migration store.
 */
class BuildingRepository(
    private val store: PortfolioFirestoreStore,
) {
    constructor(firestore: Firestore) : this(sourcePortfolioFirestoreStore(firestore))

    private val log = LoggerFactory.getLogger(BuildingRepository::class.java)
    private val lock = ReentrantLock()

    fun listBuildings(): List<Building> = read {
        store.list(PortfolioFirestoreCollections.BUILDINGS).mapNotNull { it.toBuilding() }
    }

    fun getBuilding(id: String): Building? = read {
        store.get(PortfolioFirestoreCollections.BUILDINGS, id)?.toBuilding()
    }

    fun upsertBuilding(building: Building) = mutate {
        store.upsert(
            PortfolioFirestoreCollections.BUILDINGS,
            building.id,
            mapOf(
                "name" to building.name,
                "address" to building.address,
                "createdAt" to building.createdAt,
            ),
        )
    }

    fun deleteBuilding(id: String) = mutate {
        store.delete(PortfolioFirestoreCollections.BUILDINGS, id)
    }

    fun listApartments(): List<Apartment> = read {
        store.list(PortfolioFirestoreCollections.BUILDING_UNITS).mapNotNull { it.toApartment() }
    }

    fun listApartmentsByBuilding(buildingId: String): List<Apartment> = read {
        store.whereEqualTo(
            collection = PortfolioFirestoreCollections.BUILDING_UNITS,
            filters = mapOf("buildingId" to buildingId),
        ).mapNotNull { it.toApartment() }
    }

    fun getApartment(id: String): Apartment? = read {
        store.get(PortfolioFirestoreCollections.BUILDING_UNITS, id)?.toApartment()
    }

    fun upsertApartment(apartment: Apartment) = mutate {
        store.upsert(
            PortfolioFirestoreCollections.BUILDING_UNITS,
            apartment.id,
            mapOf(
                "buildingId" to apartment.buildingId,
                "unitNumber" to apartment.unitNumber,
                "floor" to apartment.floor,
                "notes" to apartment.notes,
            ),
        )
    }

    fun deleteApartment(id: String) = mutate {
        store.delete(PortfolioFirestoreCollections.BUILDING_UNITS, id)
    }

    fun listTenants(): List<Tenant> = read {
        store.list(PortfolioFirestoreCollections.BUILDING_TENANTS).mapNotNull { it.toTenant() }
    }

    fun getTenant(id: String): Tenant? = read {
        store.get(PortfolioFirestoreCollections.BUILDING_TENANTS, id)?.toTenant()
    }

    fun upsertTenant(tenant: Tenant) = mutate {
        store.upsert(
            PortfolioFirestoreCollections.BUILDING_TENANTS,
            tenant.id,
            mapOf(
                "name" to tenant.name,
                "phone" to tenant.phone,
                "email" to tenant.email,
                "nationalId" to tenant.nationalId,
                "notes" to tenant.notes,
            ),
        )
    }

    fun deleteTenant(id: String) = mutate {
        store.delete(PortfolioFirestoreCollections.BUILDING_TENANTS, id)
    }

    fun listLeases(): List<Lease> = read {
        store.list(PortfolioFirestoreCollections.BUILDING_LEASES).mapNotNull { it.toLease() }
    }

    fun getLeaseByUnit(unitId: String): Lease? = read {
        store.whereEqualTo(
            collection = PortfolioFirestoreCollections.BUILDING_LEASES,
            filters = mapOf("unitId" to unitId),
        ).mapNotNull { it.toLease() }
            .maxByOrNull(Lease::endDate)
    }

    fun upsertLease(lease: Lease) = mutate {
        store.upsert(
            PortfolioFirestoreCollections.BUILDING_LEASES,
            lease.id,
            mapOf(
                "unitId" to lease.unitId,
                "tenantId" to lease.tenantId,
                "annualRent" to lease.annualRent,
                "startDate" to lease.startDate,
                "endDate" to lease.endDate,
                "notes" to lease.notes,
            ),
        )
    }

    fun deleteLease(id: String) = mutate {
        store.delete(PortfolioFirestoreCollections.BUILDING_LEASES, id)
    }

    fun listPayments(): List<Payment> = read {
        store.list(PortfolioFirestoreCollections.BUILDING_PAYMENTS).mapNotNull { it.toPayment() }
    }

    fun listPaymentsByLease(leaseId: String): List<Payment> = read {
        store.whereEqualTo(
            collection = PortfolioFirestoreCollections.BUILDING_PAYMENTS,
            filters = mapOf("leaseId" to leaseId),
        ).mapNotNull { it.toPayment() }
            .sortedBy(Payment::paymentNumber)
    }

    fun upsertPayment(payment: Payment) = mutate {
        store.upsert(
            PortfolioFirestoreCollections.BUILDING_PAYMENTS,
            payment.id,
            mapOf(
                "leaseId" to payment.leaseId,
                "paymentNumber" to payment.paymentNumber,
                "amount" to payment.amount,
                "periodStart" to payment.periodStart,
                "periodEnd" to payment.periodEnd,
                "dueDate" to payment.dueDate,
                "paidDate" to payment.paidDate,
                "status" to payment.status.name,
                "notes" to payment.notes,
            ),
        )
    }

    fun deletePayment(id: String) = mutate {
        store.delete(PortfolioFirestoreCollections.BUILDING_PAYMENTS, id)
    }

    fun clearAllData() = mutate {
        // Preserve dependency order while making every delete replayable and idempotent.
        listOf(
            PortfolioFirestoreCollections.BUILDING_PAYMENTS,
            PortfolioFirestoreCollections.BUILDING_LEASES,
            PortfolioFirestoreCollections.BUILDING_TENANTS,
            PortfolioFirestoreCollections.BUILDING_UNITS,
            PortfolioFirestoreCollections.BUILDINGS,
        ).forEach { collection ->
            store.list(collection).forEach { document -> store.delete(collection, document.id) }
        }
    }

    private fun mutate(block: () -> Unit) = lock.withLock {
        runBlocking { withContext(Dispatchers.IO) { block() } }
    }

    private fun <T> read(block: () -> T): T = runBlocking {
        withContext(Dispatchers.IO) { block() }
    }

    private fun PortfolioFirestoreDocument.toBuilding(): Building? = parse("building") {
        Building(
            id = id,
            name = string("name"),
            address = string("address"),
            createdAt = long("createdAt") ?: System.currentTimeMillis(),
        )
    }

    private fun PortfolioFirestoreDocument.toApartment(): Apartment? = parse("apartment") {
        Apartment(
            id = id,
            buildingId = string("buildingId"),
            unitNumber = string("unitNumber"),
            floor = long("floor")?.toInt(),
            notes = string("notes"),
        )
    }

    private fun PortfolioFirestoreDocument.toTenant(): Tenant? = parse("tenant") {
        Tenant(
            id = id,
            name = string("name"),
            phone = string("phone"),
            email = string("email"),
            nationalId = string("nationalId"),
            notes = string("notes"),
        )
    }

    private fun PortfolioFirestoreDocument.toLease(): Lease? = parse("lease") {
        Lease(
            id = id,
            unitId = string("unitId"),
            tenantId = string("tenantId"),
            annualRent = number("annualRent")?.toDouble() ?: 0.0,
            startDate = string("startDate"),
            endDate = string("endDate"),
            notes = string("notes"),
        )
    }

    private fun PortfolioFirestoreDocument.toPayment(): Payment? = parse("payment") {
        Payment(
            id = id,
            leaseId = string("leaseId"),
            paymentNumber = number("paymentNumber")?.toInt() ?: 1,
            amount = number("amount")?.toDouble() ?: 0.0,
            periodStart = string("periodStart"),
            periodEnd = string("periodEnd"),
            dueDate = string("dueDate"),
            paidDate = data["paidDate"] as? String,
            status = runCatching {
                PaymentStatus.valueOf(string("status", PaymentStatus.PENDING.name))
            }.getOrDefault(PaymentStatus.PENDING),
            notes = string("notes"),
        )
    }

    private inline fun <T> PortfolioFirestoreDocument.parse(
        kind: String,
        block: PortfolioFirestoreDocument.() -> T,
    ): T? = try {
        block()
    } catch (failure: Exception) {
        log.error("Failed to parse $kind $id", failure)
        null
    }

    private fun PortfolioFirestoreDocument.string(key: String, default: String = ""): String =
        data[key] as? String ?: default

    private fun PortfolioFirestoreDocument.long(key: String): Long? = number(key)?.toLong()

    private fun PortfolioFirestoreDocument.number(key: String): Number? = data[key] as? Number
}
