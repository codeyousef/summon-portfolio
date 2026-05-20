package code.yousef.portfolio.building.bulk

import code.yousef.portfolio.building.model.Apartment
import code.yousef.portfolio.building.model.Building
import code.yousef.portfolio.building.model.Lease
import code.yousef.portfolio.building.model.Payment
import code.yousef.portfolio.building.model.PaymentStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildingBulkOperationsTest {

    @Test
    fun `selected IDs are extracted from select-prefixed fields`() {
        val params = linkedMapOf(
            "select_building-1" to "1",
            "csrf" to "ignored",
            "select_building-2" to "on",
            "select_" to "1",
            "select_building-1" to "duplicate"
        )

        assertEquals(listOf("building-1", "building-2"), BuildingBulkFormParser.selectedIds(params))
    }

    @Test
    fun `record IDs are extracted in numeric index order`() {
        val params = linkedMapOf(
            "record_2" to "third",
            "record_0" to "first",
            "record_1" to "second",
            "record_3" to "second",
            "record_bad" to "last",
            "record_4" to ""
        )

        assertEquals(listOf("first", "second", "third", "last"), BuildingBulkFormParser.recordIds(params))
    }

    @Test
    fun `blank and invalid dates are rejected`() {
        assertFailsWith<IllegalArgumentException> { parseBulkDate("", "Due date") }
        assertFailsWith<IllegalArgumentException> { parseBulkDate("05-04-2026", "Due date") }
    }

    @Test
    fun `date updates can set exact dates`() {
        val update = BulkDateUpdate(mode = BulkDateMode.SET, setDate = LocalDate.parse("2026-06-15"))

        assertEquals("2026-06-15", applyBulkDateUpdate("2026-05-04", update, "Due date"))
    }

    @Test
    fun `date updates can shift by days and months`() {
        val dayShift = BulkDateUpdate(mode = BulkDateMode.SHIFT, shiftAmount = 10, shiftUnit = BulkDateUnit.DAYS)
        val monthShift = BulkDateUpdate(mode = BulkDateMode.SHIFT, shiftAmount = 2, shiftUnit = BulkDateUnit.MONTHS)

        assertEquals("2026-05-14", applyBulkDateUpdate("2026-05-04", dayShift, "Due date"))
        assertEquals("2026-07-04", applyBulkDateUpdate("2026-05-04", monthShift, "Due date"))
    }

    @Test
    fun `shifting a blank date is rejected`() {
        val update = BulkDateUpdate(mode = BulkDateMode.SHIFT, shiftAmount = 1, shiftUnit = BulkDateUnit.DAYS)

        assertFailsWith<IllegalArgumentException> { applyBulkDateUpdate(null, update, "Paid date") }
    }

    @Test
    fun `building delete cascade counts related apartments leases payments and only orphan tenants`() {
        val plan = planBuildingCascadeDelete(
            selectedIds = listOf("building-1", "missing"),
            buildings = listOf(building("building-1"), building("building-2")),
            apartments = listOf(apartment("unit-1", "building-1"), apartment("unit-2", "building-2")),
            leases = listOf(
                lease("lease-1", "unit-1", "tenant-1"),
                lease("lease-2", "unit-2", "tenant-1")
            ),
            payments = listOf(payment("payment-1", "lease-1"), payment("payment-2", "lease-2"))
        )

        assertEquals(2, plan.requested)
        assertEquals(1, plan.existing)
        assertEquals(1, plan.buildings)
        assertEquals(1, plan.apartments)
        assertEquals(1, plan.leases)
        assertEquals(1, plan.payments)
        assertEquals(0, plan.tenants)
        assertEquals(1, plan.skipped)
    }

    @Test
    fun `apartment delete cascade counts orphan tenants`() {
        val plan = planApartmentCascadeDelete(
            selectedIds = listOf("unit-1"),
            apartments = listOf(apartment("unit-1", "building-1"), apartment("unit-2", "building-1")),
            leases = listOf(lease("lease-1", "unit-1", "tenant-1"), lease("lease-2", "unit-2", "tenant-2")),
            payments = listOf(payment("payment-1", "lease-1"), payment("payment-2", "lease-2"))
        )

        assertEquals(1, plan.existing)
        assertEquals(1, plan.apartments)
        assertEquals(1, plan.leases)
        assertEquals(1, plan.payments)
        assertEquals(1, plan.tenants)
    }

    @Test
    fun `payment delete plan counts selected existing payments only`() {
        val plan = planPaymentDelete(
            selectedIds = listOf("payment-1", "missing"),
            payments = listOf(payment("payment-1", "lease-1"), payment("payment-2", "lease-1"))
        )

        assertEquals(2, plan.requested)
        assertEquals(1, plan.existing)
        assertEquals(1, plan.payments)
        assertEquals(1, plan.skipped)
    }

    private fun building(id: String) = Building(id = id, name = id)

    private fun apartment(id: String, buildingId: String) = Apartment(
        id = id,
        buildingId = buildingId,
        unitNumber = id
    )

    private fun lease(id: String, unitId: String, tenantId: String) = Lease(
        id = id,
        unitId = unitId,
        tenantId = tenantId,
        annualRent = 12000.0,
        startDate = "2026-01-01",
        endDate = "2026-12-31"
    )

    private fun payment(
        id: String,
        leaseId: String,
        dueDate: String = "2026-05-04",
        status: PaymentStatus = PaymentStatus.PENDING
    ) = Payment(
        id = id,
        leaseId = leaseId,
        paymentNumber = 1,
        amount = 1000.0,
        periodStart = "2026-01-01",
        periodEnd = "2026-01-31",
        dueDate = dueDate,
        status = status
    )
}
