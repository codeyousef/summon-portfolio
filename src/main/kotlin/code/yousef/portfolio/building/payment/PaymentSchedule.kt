package code.yousef.portfolio.building.payment

import code.yousef.portfolio.building.model.Lease
import code.yousef.portfolio.building.model.Payment
import code.yousef.portfolio.building.model.PaymentStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val paymentDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

data class ScheduledPayment(
    val leaseId: String,
    val paymentNumber: Int,
    val amount: Double,
    val periodStart: String,
    val periodEnd: String,
    val dueDate: String
) {
    fun newPayment(id: String): Payment = Payment(
        id = id,
        leaseId = leaseId,
        paymentNumber = paymentNumber,
        amount = amount,
        periodStart = periodStart,
        periodEnd = periodEnd,
        dueDate = dueDate,
        paidDate = null,
        status = PaymentStatus.PENDING
    )

    fun applyTo(existing: Payment): Payment = existing.copy(
        leaseId = leaseId,
        paymentNumber = paymentNumber,
        amount = amount,
        periodStart = periodStart,
        periodEnd = periodEnd,
        dueDate = dueDate
    )
}

fun buildSemiannualPaymentSchedule(lease: Lease, year: Int): List<ScheduledPayment> {
    if (lease.annualRent <= 0.0) return emptyList()
    val leaseStart = parsePaymentDate(lease.startDate) ?: return emptyList()
    val leaseEnd = parsePaymentDate(lease.endDate) ?: return emptyList()
    if (leaseEnd.isBefore(leaseStart)) return emptyList()

    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)
    val halfAnnualAmount = lease.annualRent / 2.0
    val scheduled = mutableListOf<ScheduledPayment>()
    var dueDate = leaseStart
    var paymentNumber = 1

    while (dueDate.isBefore(yearStart)) {
        dueDate = dueDate.plusMonths(6)
        paymentNumber++
    }

    while (!dueDate.isAfter(yearEnd) && !dueDate.isAfter(leaseEnd)) {
        val nextPeriodEnd = dueDate.plusMonths(6).minusDays(1)
        val periodEnd = if (nextPeriodEnd.isBefore(leaseEnd)) nextPeriodEnd else leaseEnd
        scheduled += ScheduledPayment(
            leaseId = lease.id,
            paymentNumber = paymentNumber,
            amount = halfAnnualAmount,
            periodStart = dueDate.format(paymentDateFormatter),
            periodEnd = periodEnd.format(paymentDateFormatter),
            dueDate = dueDate.format(paymentDateFormatter)
        )
        dueDate = dueDate.plusMonths(6)
        paymentNumber++
    }

    return scheduled
}

fun buildPaymentSyncUpdates(
    lease: Lease,
    existingPayments: List<Payment>,
    year: Int,
    idFactory: () -> String
): List<Payment> {
    val existingByPaymentNumber = existingPayments
        .filter { it.leaseId == lease.id }
        .sortedBy { it.paymentNumber }
        .groupBy { it.paymentNumber }
        .mapValues { (_, payments) -> payments.first() }

    return buildSemiannualPaymentSchedule(lease, year).map { scheduled ->
        val existing = existingByPaymentNumber[scheduled.paymentNumber]
        if (existing == null) {
            scheduled.newPayment(idFactory())
        } else {
            scheduled.applyTo(existing)
        }
    }
}

fun isUnpaidPastDue(payment: Payment, today: LocalDate = LocalDate.now()): Boolean {
    if (payment.status != PaymentStatus.PENDING) return false
    val dueDate = parsePaymentDate(payment.dueDate) ?: return false
    return dueDate.isBefore(today)
}

fun withManualPaymentStatus(
    payment: Payment,
    status: PaymentStatus,
    paidDate: LocalDate = LocalDate.now()
): Payment {
    return when (status) {
        PaymentStatus.PAID -> payment.copy(
            status = PaymentStatus.PAID,
            paidDate = payment.paidDate?.takeIf { it.isNotBlank() } ?: paidDate.format(paymentDateFormatter)
        )
        PaymentStatus.PENDING,
        PaymentStatus.OVERDUE -> payment.copy(
            status = status,
            paidDate = null
        )
    }
}

private fun parsePaymentDate(value: String): LocalDate? {
    return try {
        LocalDate.parse(value, paymentDateFormatter)
    } catch (_: DateTimeParseException) {
        null
    }
}
