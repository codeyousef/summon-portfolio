package code.yousef.portfolio.building.import

import code.yousef.portfolio.building.model.*
import code.yousef.portfolio.building.repo.BuildingRepository
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Service for importing building data from Excel (.xlsx) files.
 * 
 * Supports TWO formats:
 * 
 * FORMAT 1 (كشف تفصيلي عمارة ريم style):
 * - Multiple payment rows per unit with الدفعة الأولى/الثانية
 * - Columns: الشقة, المستأجر, الإيجار السنوي, من, إلى, مبلغ الدفعة, تاريخ الدفع, الملاحظات
 * 
 * FORMAT 2 (كشف تفصيلي عن الإيرادات style - NEW):
 * - One row per unit, multiple sheets per building
 * - Columns (RTL): #, البيان, من, إلى, المبلغ, تاريخ التحويل, الملاحظات, الملاحظات
 * - Each sheet tab is a different building name
 */
class ExcelImportService(
    private val repository: BuildingRepository
) {
    private val log = LoggerFactory.getLogger(ExcelImportService::class.java)
    
    // Date formatters for various formats in the Excel
    // Note: Order matters - try more specific formats first
    private val dateFormatters = listOf(
        // Full year formats
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        // Two-digit year formats (common in Arabic Excel sheets)
        DateTimeFormatter.ofPattern("dd-MM-yy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("d-M-yy"),
        DateTimeFormatter.ofPattern("d/M/yy"),
        DateTimeFormatter.ofPattern("yy/MM/dd"),
        DateTimeFormatter.ofPattern("yy-MM-dd")
    )
    
    private val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    data class ImportResult(
        val success: Boolean,
        val buildingName: String?,
        val unitsImported: Int,
        val paymentsImported: Int,
        val errors: List<String>
    )

    fun importFromExcel(inputStream: InputStream): ImportResult {
        val errors = mutableListOf<String>()
        var buildingName: String? = null
        var totalUnitsImported = 0
        var totalPaymentsImported = 0

        try {
            val workbook = XSSFWorkbook(inputStream)
            
            // Process ALL sheets - each sheet can be a different building
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val sheetName = workbook.getSheetName(sheetIndex)
                
                log.info("Processing sheet $sheetIndex: '$sheetName'")
                
                // Detect format from first row
                val titleRow = sheet.getRow(0)
                val titleCell = if (titleRow != null) getCellStringValue(titleRow.getCell(0)) else ""
                
                // Determine building name
                val currentBuildingName = when {
                    // Format 2: Use sheet tab name as building
                    sheetName.isNotBlank() && !sheetName.startsWith("Sheet") -> "عمارة $sheetName"
                    // Format 1: Extract from title row
                    titleCell.contains("عمارة") -> extractBuildingName(titleCell) ?: "عمارة ${System.currentTimeMillis()}"
                    else -> "عمارة ${sheetName.ifBlank { System.currentTimeMillis().toString() }}"
                }
                
                if (buildingName == null) buildingName = currentBuildingName
                
                log.info("Importing building: $currentBuildingName")
                
                // Create building
                val buildingId = UUID.randomUUID().toString()
                val building = Building(
                    id = buildingId,
                    name = currentBuildingName,
                    address = ""
                )
                repository.upsertBuilding(building)
                
                // Detect format: Check header row for format identification
                val headerRow = sheet.getRow(1)
                val isFormat2 = headerRow != null && (
                    getCellStringValue(headerRow.getCell(0)).contains("#") ||
                    getCellStringValue(headerRow.getCell(1)).contains("البيان") ||
                    titleCell.contains("الإيرادات")
                )
                
                val (unitsImported, paymentsImported) = if (isFormat2) {
                    importFormat2(sheet, buildingId, errors)
                } else {
                    importFormat1(sheet, buildingId, errors)
                }
                
                totalUnitsImported += unitsImported
                totalPaymentsImported += paymentsImported
            }
            
            workbook.close()
            
            log.info("Import complete: $totalUnitsImported units, $totalPaymentsImported payments")
            
            return ImportResult(
                success = true,
                buildingName = buildingName,
                unitsImported = totalUnitsImported,
                paymentsImported = totalPaymentsImported,
                errors = errors
            )
            
        } catch (e: Exception) {
            log.error("Failed to import Excel file", e)
            return ImportResult(
                success = false,
                buildingName = buildingName,
                unitsImported = totalUnitsImported,
                paymentsImported = totalPaymentsImported,
                errors = errors + "Import failed: ${e.message}"
            )
        }
    }

    private fun extractBuildingName(title: String): String? {
        // Extract building name from title like "كشف تفصيلي عمارة ريم"
        val patterns = listOf(
            Regex("عمارة\\s+([\\u0600-\\u06FF\\s]+)"),
            Regex("بناية\\s+([\\u0600-\\u06FF\\s]+)"),
            Regex("مبنى\\s+([\\u0600-\\u06FF\\s]+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(title)
            if (match != null) {
                return "عمارة ${match.groupValues[1].trim()}"
            }
        }
        
        // If no pattern matches, try to extract anything after "عمارة"
        val idx = title.indexOf("عمارة")
        if (idx >= 0) {
            return title.substring(idx).trim()
        }
        
        return null
    }

    private fun getCellStringValue(cell: Cell?): String {
        if (cell == null) return ""
        
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        cell.localDateTimeCellValue.toLocalDate().format(outputFormatter)
                    } catch (_: Exception) {
                        cell.numericCellValue.toString()
                    }
                } else {
                    // Format number without decimals if it's a whole number
                    val num = cell.numericCellValue
                    if (num == num.toLong().toDouble()) {
                        num.toLong().toString()
                    } else {
                        num.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (_: Exception) {
                    try {
                        cell.numericCellValue.toString()
                    } catch (_: Exception) {
                        ""
                    }
                }
            }
            else -> ""
        }
    }

    private fun getCellNumericValue(cell: Cell?): Double {
        if (cell == null) return 0.0
        
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> {
                // Try to parse string as number (handles formatted numbers like "30,000 ر.س")
                val cleaned = cell.stringCellValue
                    .replace(",", "")
                    .replace("٬", "")
                    .replace("ر.س", "")
                    .replace("SAR", "")
                    .trim()
                cleaned.toDoubleOrNull() ?: 0.0
            }
            CellType.FORMULA -> {
                try {
                    cell.numericCellValue
                } catch (_: Exception) {
                    0.0
                }
            }
            else -> 0.0
        }
    }

    private fun getCellDateValue(cell: Cell?): String? {
        if (cell == null) return null
        
        return when (cell.cellType) {
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        cell.localDateTimeCellValue.toLocalDate().format(outputFormatter)
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    // Try to interpret as date serial number
                    try {
                        val date = DateUtil.getLocalDateTime(cell.numericCellValue)
                        date.toLocalDate().format(outputFormatter)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            CellType.STRING -> {
                val dateStr = cell.stringCellValue.trim()
                parseDate(dateStr)
            }
            else -> null
        }
    }

    private fun parseDate(dateStr: String): String? {
        if (dateStr.isBlank()) return null
        
        for (formatter in dateFormatters) {
            try {
                val date = LocalDate.parse(dateStr, formatter)
                return date.format(outputFormatter)
            } catch (_: Exception) {
                // Try next formatter
            }
        }
        
        log.warn("Could not parse date: $dateStr")
        return null
    }
    
    /**
     * Format 1: كشف تفصيلي عمارة ريم style
     * Multiple payment rows per unit with الدفعة الأولى/الثانية
     * Columns: الشقة, المستأجر, الإيجار السنوي, من, إلى, مبلغ الدفعة, تاريخ الدفع, الملاحظات
     */
    private fun importFormat1(sheet: Sheet, buildingId: String, errors: MutableList<String>): Pair<Int, Int> {
        var unitsImported = 0
        var paymentsImported = 0
        
        var currentUnitId: String? = null
        var currentLeaseId: String? = null
        var currentTenantId: String? = null
        var currentPaymentNumber = 0
        
        // Skip header rows (0=title, 1=headers, 2=sub-headers), start from row 3
        val dataStartRow = 3
        
        for (rowIndex in dataStartRow..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            
            try {
                val unitCell = getCellStringValue(row.getCell(0))
                val tenantOrPaymentType = getCellStringValue(row.getCell(1))
                val annualRent = getCellNumericValue(row.getCell(2))
                val periodStart = getCellDateValue(row.getCell(3))
                val periodEnd = getCellDateValue(row.getCell(4))
                val paymentAmount = getCellNumericValue(row.getCell(5))
                val paymentDate = getCellDateValue(row.getCell(6))
                val notes = getCellStringValue(row.getCell(7))
                
                // Skip empty rows
                if (unitCell.isBlank() && tenantOrPaymentType.isBlank() && paymentAmount == 0.0) {
                    continue
                }
                
                // Check if this is a new unit or continuation
                val isNewUnit = unitCell.isNotBlank() && unitCell.contains("شقة")
                
                if (isNewUnit) {
                    // Create new apartment
                    currentUnitId = UUID.randomUUID().toString()
                    val apartment = Apartment(
                        id = currentUnitId,
                        buildingId = buildingId,
                        unitNumber = unitCell.trim()
                    )
                    repository.upsertApartment(apartment)
                    unitsImported++
                    
                    // Create tenant (placeholder name based on unit)
                    currentTenantId = UUID.randomUUID().toString()
                    val tenant = Tenant(
                        id = currentTenantId,
                        name = "مستأجر ${unitCell.trim()}"
                    )
                    repository.upsertTenant(tenant)
                    
                    // Create lease if we have rent info
                    if (annualRent > 0 && periodStart != null && periodEnd != null) {
                        currentLeaseId = UUID.randomUUID().toString()
                        val lease = Lease(
                            id = currentLeaseId,
                            unitId = currentUnitId,
                            tenantId = currentTenantId,
                            annualRent = annualRent,
                            startDate = periodStart,
                            endDate = periodEnd
                        )
                        repository.upsertLease(lease)
                    }
                    
                    currentPaymentNumber = 0
                }
                
                // Create payment record
                if (paymentAmount > 0 && currentLeaseId != null) {
                    currentPaymentNumber++
                    
                    // Determine payment status from notes
                    val status = when {
                        notes.contains("تم السداد") || notes.contains("تم") -> PaymentStatus.PAID
                        notes.contains("لم يسدد") || notes.contains("لم") -> PaymentStatus.PENDING
                        else -> PaymentStatus.PENDING
                    }
                    
                    val payment = Payment(
                        id = UUID.randomUUID().toString(),
                        leaseId = currentLeaseId,
                        paymentNumber = currentPaymentNumber,
                        amount = paymentAmount,
                        periodStart = periodStart ?: "",
                        periodEnd = periodEnd ?: "",
                        dueDate = paymentDate ?: periodEnd ?: "",
                        paidDate = if (status == PaymentStatus.PAID) paymentDate else null,
                        status = status,
                        notes = notes
                    )
                    repository.upsertPayment(payment)
                    paymentsImported++
                }
                
            } catch (e: Exception) {
                val error = "Error in row ${rowIndex + 1}: ${e.message}"
                log.warn(error, e)
                errors.add(error)
            }
        }
        
        return Pair(unitsImported, paymentsImported)
    }
    
    /**
     * Format 2: كشف تفصيلي عن الإيرادات style (New format from screenshot)
     * One row per unit, columns (RTL):
     * 0: # (row number)
     * 1: البيان (Unit - شقة 1)
     * 2: من (From date - period start)
     * 3: إلى (To date - period end)
     * 4: المبلغ (Amount)
     * 5: تاريخ التحويل (Payment/transfer date)
     * 6: الملاحظات (Notes - payment method)
     * 7: الملاحظات (Notes - due date يحل بتاريخ)
     */
    private fun importFormat2(sheet: Sheet, buildingId: String, errors: MutableList<String>): Pair<Int, Int> {
        var unitsImported = 0
        var paymentsImported = 0
        
        // Skip header rows (0=title, 1=headers, 2=sub-headers), start from row 3
        val dataStartRow = 3
        
        // Track units to avoid duplicates (same شقة can have multiple rows)
        val unitMap = mutableMapOf<String, String>() // unitNumber -> unitId
        val leaseMap = mutableMapOf<String, String>() // unitId -> leaseId
        val tenantMap = mutableMapOf<String, String>() // unitId -> tenantId
        val paymentCountMap = mutableMapOf<String, Int>() // leaseId -> count
        
        log.info("Format 2 import: Starting at row $dataStartRow, last row = ${sheet.lastRowNum}")
        
        for (rowIndex in dataStartRow..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            
            try {
                // Format 2 columns (reading as they appear in Excel, RTL display)
                val rowNum = getCellStringValue(row.getCell(0)) // # column
                val unitCell = getCellStringValue(row.getCell(1)) // البيان - Unit
                val periodStart = getCellDateValue(row.getCell(2)) // من
                val periodEnd = getCellDateValue(row.getCell(3)) // إلى
                val amount = getCellNumericValue(row.getCell(4)) // المبلغ
                val paymentDate = getCellDateValue(row.getCell(5)) // تاريخ التحويل
                val notesMethod = getCellStringValue(row.getCell(6)) // الملاحظات (method)
                val notesDue = getCellStringValue(row.getCell(7)) // الملاحظات (due date)
                
                log.debug("Row $rowIndex: unit='$unitCell', start='$periodStart', end='$periodEnd', amount=$amount")
                
                // Skip empty rows or header-like rows
                if (unitCell.isBlank() || !unitCell.contains("شقة")) {
                    continue
                }
                
                val unitNumber = unitCell.trim()
                log.info("Processing unit: $unitNumber, period: $periodStart to $periodEnd, amount: $amount")
                
                // Get or create unit
                val unitId = unitMap.getOrPut(unitNumber) {
                    val newUnitId = UUID.randomUUID().toString()
                    val apartment = Apartment(
                        id = newUnitId,
                        buildingId = buildingId,
                        unitNumber = unitNumber
                    )
                    repository.upsertApartment(apartment)
                    unitsImported++
                    
                    // Create tenant
                    val tenantId = UUID.randomUUID().toString()
                    val tenant = Tenant(
                        id = tenantId,
                        name = "مستأجر $unitNumber"
                    )
                    repository.upsertTenant(tenant)
                    tenantMap[newUnitId] = tenantId
                    
                    newUnitId
                }
                
                // Get or create lease for this unit (only if we have valid data)
                // Update the lease if this row has better data (dates, amount)
                val hasValidLeaseData = amount > 0 || (periodStart != null && periodEnd != null)
                
                val leaseId = if (hasValidLeaseData) {
                    val existingLeaseId = leaseMap[unitId]
                    if (existingLeaseId != null) {
                        // Update existing lease if this row has better data
                        val existingLeases = repository.listLeases().filter { it.id == existingLeaseId }
                        if (existingLeases.isNotEmpty()) {
                            val existing = existingLeases.first()
                            // Update if we have new dates or higher amount
                            if ((periodEnd != null && existing.endDate.isBlank()) || 
                                (amount > existing.annualRent)) {
                                val updatedLease = existing.copy(
                                    annualRent = if (amount > existing.annualRent) amount else existing.annualRent,
                                    startDate = periodStart ?: existing.startDate,
                                    endDate = periodEnd ?: existing.endDate
                                )
                                repository.upsertLease(updatedLease)
                                log.info("Updated lease for $unitNumber: rent=${updatedLease.annualRent}, end=${updatedLease.endDate}")
                            }
                        }
                        existingLeaseId
                    } else {
                        // Create new lease
                        val newLeaseId = UUID.randomUUID().toString()
                        val tenantId = tenantMap[unitId] ?: UUID.randomUUID().toString()
                        val lease = Lease(
                            id = newLeaseId,
                            unitId = unitId,
                            tenantId = tenantId,
                            annualRent = amount,
                            startDate = periodStart ?: "",
                            endDate = periodEnd ?: ""
                        )
                        repository.upsertLease(lease)
                        leaseMap[unitId] = newLeaseId
                        log.info("Created lease for $unitNumber: rent=$amount, period=$periodStart to $periodEnd")
                        newLeaseId
                    }
                } else {
                    leaseMap[unitId] // May be null if no valid data yet
                }
                
                // Create payment if we have an amount and a lease
                if (amount > 0 && leaseId != null) {
                    val paymentNumber = paymentCountMap.getOrDefault(leaseId, 0) + 1
                    paymentCountMap[leaseId] = paymentNumber
                    
                    // Determine status from notes
                    val allNotes = "$notesMethod $notesDue"
                    val status = when {
                        allNotes.contains("تم") || paymentDate != null -> PaymentStatus.PAID
                        allNotes.contains("يحل") || allNotes.contains("لم") -> PaymentStatus.PENDING
                        allNotes.contains("منتهي") -> PaymentStatus.PENDING
                        else -> if (paymentDate != null) PaymentStatus.PAID else PaymentStatus.PENDING
                    }
                    
                    // Extract due date from notesDue if present (يحل بتاريخ 2026/01/17)
                    val dueDateFromNotes = extractDueDateFromNotes(notesDue)
                    
                    val payment = Payment(
                        id = UUID.randomUUID().toString(),
                        leaseId = leaseId,
                        paymentNumber = paymentNumber,
                        amount = amount,
                        periodStart = periodStart ?: "",
                        periodEnd = periodEnd ?: "",
                        dueDate = dueDateFromNotes ?: periodEnd ?: "",
                        paidDate = if (status == PaymentStatus.PAID) paymentDate else null,
                        status = status,
                        notes = allNotes.trim()
                    )
                    repository.upsertPayment(payment)
                    paymentsImported++
                }
                
            } catch (e: Exception) {
                val error = "Error in row ${rowIndex + 1}: ${e.message}"
                log.warn(error, e)
                errors.add(error)
            }
        }
        
        return Pair(unitsImported, paymentsImported)
    }
    
    /**
     * Extract due date from notes like "يحل بتاريخ 2026/01/17"
     */
    private fun extractDueDateFromNotes(notes: String): String? {
        val pattern = Regex("(\\d{4}/\\d{1,2}/\\d{1,2})")
        val match = pattern.find(notes)
        return match?.let { parseDate(it.value) }
    }
}
