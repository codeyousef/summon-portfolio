package code.yousef.portfolio.building.repo

import code.yousef.portfolio.building.model.*
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Firestore repository for building management data.
 * Collections: buildings, units, tenants, leases, payments
 */
class BuildingRepository(private val firestore: Firestore) {
    private val log = LoggerFactory.getLogger(BuildingRepository::class.java)
    private val lock = ReentrantLock()

    private val buildingsCollection = firestore.collection("buildings")
    private val unitsCollection = firestore.collection("building_units")
    private val tenantsCollection = firestore.collection("building_tenants")
    private val leasesCollection = firestore.collection("building_leases")
    private val paymentsCollection = firestore.collection("building_payments")

    // ============ Buildings ============
    
    fun listBuildings(): List<Building> = runBlocking {
        withContext(Dispatchers.IO) {
            buildingsCollection.get().get().documents.mapNotNull { doc ->
                try {
                    Building(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        address = doc.getString("address") ?: "",
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    log.error("Failed to parse building ${doc.id}", e)
                    null
                }
            }
        }
    }

    fun getBuilding(id: String): Building? = runBlocking {
        withContext(Dispatchers.IO) {
            val doc = buildingsCollection.document(id).get().get()
            if (doc.exists()) {
                Building(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    address = doc.getString("address") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            } else null
        }
    }

    fun upsertBuilding(building: Building) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    buildingsCollection.document(building.id).set(
                        mapOf(
                            "name" to building.name,
                            "address" to building.address,
                            "createdAt" to building.createdAt
                        )
                    ).get()
                }
            }
        }
    }

    fun deleteBuilding(id: String) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    buildingsCollection.document(id).delete().get()
                }
            }
        }
    }

    // ============ Apartments (Units) ============
    
    fun listApartments(): List<Apartment> = runBlocking {
        withContext(Dispatchers.IO) {
            unitsCollection.get().get().documents.mapNotNull { doc ->
                try {
                    Apartment(
                        id = doc.id,
                        buildingId = doc.getString("buildingId") ?: "",
                        unitNumber = doc.getString("unitNumber") ?: "",
                        floor = doc.getLong("floor")?.toInt(),
                        notes = doc.getString("notes") ?: ""
                    )
                } catch (e: Exception) {
                    log.error("Failed to parse apartment ${doc.id}", e)
                    null
                }
            }
        }
    }

    fun listApartmentsByBuilding(buildingId: String): List<Apartment> = runBlocking {
        withContext(Dispatchers.IO) {
            unitsCollection.whereEqualTo("buildingId", buildingId).get().get().documents.mapNotNull { doc ->
                try {
                    Apartment(
                        id = doc.id,
                        buildingId = doc.getString("buildingId") ?: "",
                        unitNumber = doc.getString("unitNumber") ?: "",
                        floor = doc.getLong("floor")?.toInt(),
                        notes = doc.getString("notes") ?: ""
                    )
                } catch (e: Exception) {
                    log.error("Failed to parse apartment ${doc.id}", e)
                    null
                }
            }
        }
    }

    fun getApartment(id: String): Apartment? = runBlocking {
        withContext(Dispatchers.IO) {
            val doc = unitsCollection.document(id).get().get()
            if (doc.exists()) {
                Apartment(
                    id = doc.id,
                    buildingId = doc.getString("buildingId") ?: "",
                    unitNumber = doc.getString("unitNumber") ?: "",
                    floor = doc.getLong("floor")?.toInt(),
                    notes = doc.getString("notes") ?: ""
                )
            } else null
        }
    }

    fun upsertApartment(apartment: Apartment) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    unitsCollection.document(apartment.id).set(
                        mapOf(
                            "buildingId" to apartment.buildingId,
                            "unitNumber" to apartment.unitNumber,
                            "floor" to apartment.floor,
                            "notes" to apartment.notes
                        )
                    ).get()
                }
            }
        }
    }

    fun deleteApartment(id: String) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    unitsCollection.document(id).delete().get()
                }
            }
        }
    }

    // ============ Tenants ============
    
    fun listTenants(): List<Tenant> = runBlocking {
        withContext(Dispatchers.IO) {
            tenantsCollection.get().get().documents.mapNotNull { doc ->
                try {
                    Tenant(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        phone = doc.getString("phone") ?: "",
                        email = doc.getString("email") ?: "",
                        nationalId = doc.getString("nationalId") ?: "",
                        notes = doc.getString("notes") ?: ""
                    )
                } catch (e: Exception) {
                    log.error("Failed to parse tenant ${doc.id}", e)
                    null
                }
            }
        }
    }

    fun getTenant(id: String): Tenant? = runBlocking {
        withContext(Dispatchers.IO) {
            val doc = tenantsCollection.document(id).get().get()
            if (doc.exists()) {
                Tenant(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    phone = doc.getString("phone") ?: "",
                    email = doc.getString("email") ?: "",
                    nationalId = doc.getString("nationalId") ?: "",
                    notes = doc.getString("notes") ?: ""
                )
            } else null
        }
    }

    fun upsertTenant(tenant: Tenant) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    tenantsCollection.document(tenant.id).set(
                        mapOf(
                            "name" to tenant.name,
                            "phone" to tenant.phone,
                            "email" to tenant.email,
                            "nationalId" to tenant.nationalId,
                            "notes" to tenant.notes
                        )
                    ).get()
                }
            }
        }
    }

    fun deleteTenant(id: String) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    tenantsCollection.document(id).delete().get()
                }
            }
        }
    }

    // ============ Leases ============
    
    fun listLeases(): List<Lease> = runBlocking {
        withContext(Dispatchers.IO) {
            leasesCollection.get().get().documents.mapNotNull { doc ->
                try {
                    Lease(
                        id = doc.id,
                        unitId = doc.getString("unitId") ?: "",
                        tenantId = doc.getString("tenantId") ?: "",
                        annualRent = doc.getDouble("annualRent") ?: 0.0,
                        startDate = doc.getString("startDate") ?: "",
                        endDate = doc.getString("endDate") ?: "",
                        notes = doc.getString("notes") ?: ""
                    )
                } catch (e: Exception) {
                    log.error("Failed to parse lease ${doc.id}", e)
                    null
                }
            }
        }
    }

    fun getLeaseByUnit(unitId: String): Lease? = runBlocking {
        withContext(Dispatchers.IO) {
            leasesCollection.whereEqualTo("unitId", unitId).get().get().documents
                .sortedByDescending { it.getString("endDate") ?: "" }
                .firstOrNull()?.let { doc ->
                    Lease(
                        id = doc.id,
                        unitId = doc.getString("unitId") ?: "",
                        tenantId = doc.getString("tenantId") ?: "",
                        annualRent = doc.getDouble("annualRent") ?: 0.0,
                        startDate = doc.getString("startDate") ?: "",
                        endDate = doc.getString("endDate") ?: "",
                        notes = doc.getString("notes") ?: ""
                    )
                }
        }
    }

    fun upsertLease(lease: Lease) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    leasesCollection.document(lease.id).set(
                        mapOf(
                            "unitId" to lease.unitId,
                            "tenantId" to lease.tenantId,
                            "annualRent" to lease.annualRent,
                            "startDate" to lease.startDate,
                            "endDate" to lease.endDate,
                            "notes" to lease.notes
                        )
                    ).get()
                }
            }
        }
    }

    fun deleteLease(id: String) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    leasesCollection.document(id).delete().get()
                }
            }
        }
    }

    // ============ Payments ============
    
    fun listPayments(): List<Payment> = runBlocking {
        withContext(Dispatchers.IO) {
            paymentsCollection.get().get().documents.mapNotNull { doc ->
                try {
                    Payment(
                        id = doc.id,
                        leaseId = doc.getString("leaseId") ?: "",
                        paymentNumber = doc.getLong("paymentNumber")?.toInt() ?: 1,
                        amount = doc.getDouble("amount") ?: 0.0,
                        periodStart = doc.getString("periodStart") ?: "",
                        periodEnd = doc.getString("periodEnd") ?: "",
                        dueDate = doc.getString("dueDate") ?: "",
                        paidDate = doc.getString("paidDate"),
                        status = try {
                            PaymentStatus.valueOf(doc.getString("status") ?: "PENDING")
                        } catch (_: Exception) {
                            PaymentStatus.PENDING
                        },
                        notes = doc.getString("notes") ?: ""
                    )
                } catch (e: Exception) {
                    log.error("Failed to parse payment ${doc.id}", e)
                    null
                }
            }
        }
    }

    fun listPaymentsByLease(leaseId: String): List<Payment> = runBlocking {
        withContext(Dispatchers.IO) {
            paymentsCollection.whereEqualTo("leaseId", leaseId).get().get().documents.mapNotNull { doc ->
                try {
                    Payment(
                        id = doc.id,
                        leaseId = doc.getString("leaseId") ?: "",
                        paymentNumber = doc.getLong("paymentNumber")?.toInt() ?: 1,
                        amount = doc.getDouble("amount") ?: 0.0,
                        periodStart = doc.getString("periodStart") ?: "",
                        periodEnd = doc.getString("periodEnd") ?: "",
                        dueDate = doc.getString("dueDate") ?: "",
                        paidDate = doc.getString("paidDate"),
                        status = try {
                            PaymentStatus.valueOf(doc.getString("status") ?: "PENDING")
                        } catch (_: Exception) {
                            PaymentStatus.PENDING
                        },
                        notes = doc.getString("notes") ?: ""
                    )
                } catch (e: Exception) {
                    log.error("Failed to parse payment ${doc.id}", e)
                    null
                }
            }.sortedBy { it.paymentNumber }
        }
    }

    fun upsertPayment(payment: Payment) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    paymentsCollection.document(payment.id).set(
                        mapOf(
                            "leaseId" to payment.leaseId,
                            "paymentNumber" to payment.paymentNumber,
                            "amount" to payment.amount,
                            "periodStart" to payment.periodStart,
                            "periodEnd" to payment.periodEnd,
                            "dueDate" to payment.dueDate,
                            "paidDate" to payment.paidDate,
                            "status" to payment.status.name,
                            "notes" to payment.notes
                        )
                    ).get()
                }
            }
        }
    }

    fun deletePayment(id: String) {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    paymentsCollection.document(id).delete().get()
                }
            }
        }
    }

    // ============ Batch operations ============

    fun clearAllData() {
        lock.withLock {
            runBlocking {
                withContext(Dispatchers.IO) {
                    // Delete all payments
                    paymentsCollection.get().get().documents.forEach { doc ->
                        doc.reference.delete().get()
                    }
                    // Delete all leases
                    leasesCollection.get().get().documents.forEach { doc ->
                        doc.reference.delete().get()
                    }
                    // Delete all tenants
                    tenantsCollection.get().get().documents.forEach { doc ->
                        doc.reference.delete().get()
                    }
                    // Delete all units
                    unitsCollection.get().get().documents.forEach { doc ->
                        doc.reference.delete().get()
                    }
                    // Delete all buildings
                    buildingsCollection.get().get().documents.forEach { doc ->
                        doc.reference.delete().get()
                    }
                }
            }
        }
    }
}
