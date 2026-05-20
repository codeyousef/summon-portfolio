package code.yousef.portfolio.building.payment

import code.yousef.portfolio.building.model.Lease
import code.yousef.portfolio.building.model.Payment
import code.yousef.portfolio.building.model.PaymentStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentScheduleTest {

    @Test
    fun `one-year lease produces two current calendar year payments`() {
        val schedule = buildSemiannualPaymentSchedule(
            lease = lease(
                annualRent = 30000.0,
                startDate = "2026-01-17",
                endDate = "2027-01-16"
            ),
            year = 2026
        )

        assertEquals(2, schedule.size)
        assertEquals(1, schedule[0].paymentNumber)
        assertEquals(15000.0, schedule[0].amount)
        assertEquals("2026-01-17", schedule[0].dueDate)
        assertEquals("2026-01-17", schedule[0].periodStart)
        assertEquals("2026-07-16", schedule[0].periodEnd)
        assertEquals(2, schedule[1].paymentNumber)
        assertEquals("2026-07-17", schedule[1].dueDate)
        assertEquals("2027-01-16", schedule[1].periodEnd)
    }

    @Test
    fun `cross-year lease only produces due dates in selected calendar year`() {
        val schedule = buildSemiannualPaymentSchedule(
            lease = lease(
                annualRent = 24000.0,
                startDate = "2025-10-01",
                endDate = "2026-09-30"
            ),
            year = 2026
        )

        assertEquals(1, schedule.size)
        assertEquals(2, schedule.single().paymentNumber)
        assertEquals(12000.0, schedule.single().amount)
        assertEquals("2026-04-01", schedule.single().dueDate)
        assertEquals("2026-09-30", schedule.single().periodEnd)
    }

    @Test
    fun `new sync payments default to unpaid`() {
        var nextId = 0
        val updates = buildPaymentSyncUpdates(
            lease = lease(annualRent = 18000.0),
            existingPayments = emptyList(),
            year = 2026,
            idFactory = { "generated-payment-${++nextId}" }
        )

        assertEquals(2, updates.size)
        assertEquals("generated-payment-1", updates.first().id)
        assertEquals(9000.0, updates.first().amount)
        assertEquals(PaymentStatus.PENDING, updates.first().status)
        assertEquals(null, updates.first().paidDate)
    }

    @Test
    fun `sync preserves manual status paid date and notes`() {
        val existing = payment(
            id = "existing-payment",
            paymentNumber = 1,
            amount = 1.0,
            periodStart = "2026-01-01",
            periodEnd = "2026-06-30",
            dueDate = "2026-01-01",
            paidDate = "2026-01-20",
            status = PaymentStatus.PAID,
            notes = "manual note"
        )

        val updates = buildPaymentSyncUpdates(
            lease = lease(annualRent = 42000.0, startDate = "2026-02-10", endDate = "2027-02-09"),
            existingPayments = listOf(existing),
            year = 2026,
            idFactory = { "new-payment" }
        )

        val updatedExisting = updates.first { it.id == "existing-payment" }
        assertEquals(21000.0, updatedExisting.amount)
        assertEquals("2026-02-10", updatedExisting.dueDate)
        assertEquals(PaymentStatus.PAID, updatedExisting.status)
        assertEquals("2026-01-20", updatedExisting.paidDate)
        assertEquals("manual note", updatedExisting.notes)
    }

    @Test
    fun `unpaid past due warning does not change payment status`() {
        val payment = payment(dueDate = "2026-01-01", status = PaymentStatus.PENDING)

        assertTrue(isUnpaidPastDue(payment, LocalDate.parse("2026-05-05")))
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertFalse(isUnpaidPastDue(payment.copy(status = PaymentStatus.OVERDUE), LocalDate.parse("2026-05-05")))
        assertFalse(isUnpaidPastDue(payment.copy(status = PaymentStatus.PAID), LocalDate.parse("2026-05-05")))
    }

    @Test
    fun `manual status updates set and clear paid dates`() {
        val payment = payment(status = PaymentStatus.PENDING, paidDate = null)
        val paid = withManualPaymentStatus(payment, PaymentStatus.PAID, LocalDate.parse("2026-05-05"))

        assertEquals(PaymentStatus.PAID, paid.status)
        assertEquals("2026-05-05", paid.paidDate)

        val blankPaidDate = withManualPaymentStatus(
            payment.copy(paidDate = ""),
            PaymentStatus.PAID,
            LocalDate.parse("2026-05-05")
        )
        assertEquals("2026-05-05", blankPaidDate.paidDate)

        val unpaid = withManualPaymentStatus(paid, PaymentStatus.PENDING, LocalDate.parse("2026-05-06"))
        assertEquals(PaymentStatus.PENDING, unpaid.status)
        assertEquals(null, unpaid.paidDate)

        val late = withManualPaymentStatus(paid, PaymentStatus.OVERDUE, LocalDate.parse("2026-05-06"))
        assertEquals(PaymentStatus.OVERDUE, late.status)
        assertEquals(null, late.paidDate)
    }

    private fun lease(
        id: String = "lease-1",
        annualRent: Double = 12000.0,
        startDate: String = "2026-01-01",
        endDate: String = "2026-12-31"
    ) = Lease(
        id = id,
        unitId = "unit-1",
        tenantId = "tenant-1",
        annualRent = annualRent,
        startDate = startDate,
        endDate = endDate
    )

    private fun payment(
        id: String = "payment-1",
        leaseId: String = "lease-1",
        paymentNumber: Int = 1,
        amount: Double = 6000.0,
        periodStart: String = "2026-01-01",
        periodEnd: String = "2026-06-30",
        dueDate: String = "2026-01-01",
        paidDate: String? = null,
        status: PaymentStatus = PaymentStatus.PENDING,
        notes: String = ""
    ) = Payment(
        id = id,
        leaseId = leaseId,
        paymentNumber = paymentNumber,
        amount = amount,
        periodStart = periodStart,
        periodEnd = periodEnd,
        dueDate = dueDate,
        paidDate = paidDate,
        status = status,
        notes = notes
    )
}
