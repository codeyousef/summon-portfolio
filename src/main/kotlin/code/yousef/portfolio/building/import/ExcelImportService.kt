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
 * Expected Excel format (based on كشف تفصيلي عمارة ريم):
 * Row 0: Title row with building name (e.g., "كشف تفصيلي عمارة ريم")
 * Row 1: Headers - الشقة, المستأجر, الإيجار السنوي, من, إلى, مبلغ الدفعة, تاريخ الدفع, الملاحظات
 * Row 2: Sub-headers (skip)
 * Row 3+: Data rows
 * 
 * Column mapping:
 * 0 - الشقة (Unit number, e.g., "شقة 1")
 * 1 - المستأجر (Tenant/Payment type: الدفعة الأولى, الدفعة الثانية)
 * 2 - الإيجار السنوي (Annual rent)
 * 3 - من (Period start date)
 * 4 - إلى (Period end date)  
 * 5 - مبلغ الدفعة (Payment amount)
 * 6 - تاريخ الدفع (Payment date / Due date)
 * 7 - الملاحظات (Notes - status like تم السداد, لم يسدد)
 */
class ExcelImportService(
    private val repository: BuildingRepository
) {
    private val log = LoggerFactory.getLogger(ExcelImportService::class.java)
    
    // Date formatters for various formats in the Excel
    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd/M/yyyy")
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
        var unitsImported = 0
        var paymentsImported = 0

        try {
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            
            // Extract building name from first row
            val titleRow = sheet.getRow(0)
            if (titleRow != null) {
                buildingName = extractBuildingName(getCellStringValue(titleRow.getCell(0)))
            }
            
            if (buildingName.isNullOrBlank()) {
                buildingName = "عمارة ${System.currentTimeMillis()}"
            }
            
            log.info("Importing building: $buildingName")
            
            // Create building
            val buildingId = UUID.randomUUID().toString()
            val building = Building(
                id = buildingId,
                name = buildingName,
                address = ""
            )
            repository.upsertBuilding(building)
            
            // Track current unit for continuation rows
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
                    val isPaymentContinuation = unitCell.isBlank() && 
                        (tenantOrPaymentType.contains("الثانية") || tenantOrPaymentType.contains("الدفعة"))
                    
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
            
            workbook.close()
            
            log.info("Import complete: $unitsImported units, $paymentsImported payments")
            
            return ImportResult(
                success = true,
                buildingName = buildingName,
                unitsImported = unitsImported,
                paymentsImported = paymentsImported,
                errors = errors
            )
            
        } catch (e: Exception) {
            log.error("Failed to import Excel file", e)
            return ImportResult(
                success = false,
                buildingName = buildingName,
                unitsImported = unitsImported,
                paymentsImported = paymentsImported,
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
}
