package code.yousef.portfolio.building.ui

/**
 * Arabic UI strings for building management
 * All labels and messages in Arabic for RTL display
 */
object BuildingStrings {
    // App
    const val APP_TITLE = "إدارة العقارات"
    const val DASHBOARD = "لوحة التحكم"
    const val LOGOUT = "تسجيل خروج"
    
    // Login
    const val LOGIN_TITLE = "تسجيل الدخول"
    const val LOGIN_SUBTITLE = "أدخل بيانات الدخول للمتابعة"
    const val USERNAME = "اسم المستخدم"
    const val PASSWORD = "كلمة المرور"
    const val LOGIN_BUTTON = "دخول"
    const val INVALID_CREDENTIALS = "بيانات الدخول غير صحيحة"
    
    // Change Password
    const val CHANGE_PASSWORD_TITLE = "تغيير كلمة المرور"
    const val CHANGE_PASSWORD_SUBTITLE = "يجب تغيير كلمة المرور قبل المتابعة"
    const val NEW_PASSWORD = "كلمة المرور الجديدة"
    const val CONFIRM_PASSWORD = "تأكيد كلمة المرور"
    const val SAVE_PASSWORD = "حفظ"
    const val PASSWORD_EMPTY = "كلمة المرور لا يمكن أن تكون فارغة"
    const val PASSWORDS_DONT_MATCH = "كلمتا المرور غير متطابقتين"
    
    // Password Reset
    const val RESET_PASSWORD_TITLE = "إعادة تعيين كلمة المرور"
    const val RESET_PASSWORD_SUBTITLE = "أدخل كلمة المرور الجديدة"
    const val RESET_LINK_EXPIRED = "رابط إعادة التعيين منتهي الصلاحية أو غير صالح"
    const val PASSWORD_RESET_SUCCESS = "تم تغيير كلمة المرور بنجاح"
    const val GENERATE_RESET_LINK = "إنشاء رابط إعادة تعيين"
    const val RESET_LINK_GENERATED = "تم إنشاء رابط إعادة التعيين"
    const val COPY_LINK = "نسخ الرابط"
    const val LINK_EXPIRES_IN = "ينتهي خلال 24 ساعة"
    const val USER_MANAGEMENT = "إدارة المستخدمين"
    const val SELECT_USER = "اختر المستخدم"
    const val NO_USERS = "لا يوجد مستخدمين"
    
    // Dashboard
    const val TOTAL_BUILDINGS = "إجمالي العمارات"
    const val TOTAL_UNITS = "إجمالي الشقق"
    const val OCCUPIED_UNITS = "الشقق المؤجرة"
    const val VACANT_UNITS = "الشقق الشاغرة"
    const val MONTHLY_INCOME = "الدخل الشهري"
    const val UPCOMING_PAYMENTS = "الدفعات القادمة"
    const val OVERDUE_PAYMENTS = "الدفعات المتأخرة"
    const val NO_UPCOMING_PAYMENTS = "لا توجد دفعات قادمة"
    const val NO_OVERDUE_PAYMENTS = "لا توجد دفعات متأخرة"
    
    // Buildings
    const val BUILDINGS = "العمارات"
    const val BUILDING_NAME = "اسم العمارة"
    const val BUILDING_ADDRESS = "العنوان"
    const val VIEW_UNITS = "عرض الشقق"
    const val NO_BUILDINGS = "لا توجد عمارات مسجلة"
    const val EDIT_BUILDING = "تعديل العمارة"
    const val DELETE_BUILDING = "حذف العمارة"
    const val CONFIRM_DELETE_BUILDING = "هل أنت متأكد من حذف هذه العمارة؟ سيتم حذف جميع الشقق والعقود المرتبطة بها."
    const val BUILDING_DELETED = "تم حذف العمارة بنجاح"
    const val BUILDING_UPDATED = "تم تحديث العمارة بنجاح"
    const val ADD_BUILDING = "إضافة عمارة"
    
    // Units
    const val UNITS = "الشقق"
    const val UNIT_NUMBER = "رقم الشقة"
    const val TENANT = "المستأجر"
    const val ANNUAL_RENT = "الإيجار السنوي"
    const val LEASE_PERIOD = "فترة العقد"
    const val STATUS = "الحالة"
    const val VIEW_PAYMENTS = "عرض الدفعات"
    const val NO_UNITS = "لا توجد شقق مسجلة"
    const val OCCUPIED = "مؤجرة"
    const val VACANT = "شاغرة"
    
    // Payments
    const val PAYMENTS = "الدفعات"
    const val PAYMENT_NUMBER = "رقم الدفعة"
    const val FIRST_PAYMENT = "الدفعة الأولى"
    const val SECOND_PAYMENT = "الدفعة الثانية"
    const val PAYMENT_AMOUNT = "المبلغ"
    const val PERIOD = "الفترة"
    const val DUE_DATE = "تاريخ الاستحقاق"
    const val PAID_DATE = "تاريخ السداد"
    const val PAYMENT_STATUS = "حالة الدفع"
    const val NOTES = "ملاحظات"
    const val NO_PAYMENTS = "لا توجد دفعات مسجلة"
    const val MARK_AS_PAID = "تسجيل السداد"
    
    // Payment statuses
    const val PAID = "تم السداد"
    const val PENDING = "لم يسدد"
    const val OVERDUE = "متأخر"
    
    // Import
    const val IMPORT_DATA = "استيراد البيانات"
    const val IMPORT_TITLE = "استيراد ملف Excel"
    const val IMPORT_SUBTITLE = "اختر ملف Excel (.xlsx) لاستيراد بيانات العمارة"
    const val SELECT_FILE = "اختر الملف"
    const val IMPORT_BUTTON = "استيراد"
    const val IMPORT_SUCCESS = "تم استيراد البيانات بنجاح"
    const val IMPORT_ERROR = "حدث خطأ أثناء الاستيراد"
    const val CLEAR_DATA = "مسح جميع البيانات"
    const val CLEAR_CONFIRM = "هل أنت متأكد من مسح جميع البيانات؟"
    const val CLEAR_DATA_CONFIRM_DIALOG = "تحذير: سيتم حذف جميع العمارات والشقق والعقود والدفعات بشكل نهائي. هل أنت متأكد؟"
    
    // Currency
    const val SAR = "ر.س"
    
    // Common
    const val FROM = "من"
    const val TO = "إلى"
    const val BACK = "رجوع"
    const val SAVE = "حفظ"
    const val CANCEL = "إلغاء"
    const val DELETE = "حذف"
    const val EDIT = "تعديل"
    const val ADD = "إضافة"
    const val SEARCH = "بحث"
    const val FILTER = "تصفية"
    const val ALL = "الكل"
    const val LOADING = "جاري التحميل..."
    const val ERROR = "حدث خطأ"
    
    // Notifications
    const val NOTIFICATIONS = "التنبيهات"
    const val PAYMENT_DUE_SOON = "دفعة مستحقة قريباً"
    const val PAYMENT_DUE_IN_DAYS = "مستحقة خلال"
    const val DAYS = "يوم"
    const val DAY = "يوم"
    const val TODAY = "اليوم"
    const val TOMORROW = "غداً"
    const val NO_NOTIFICATIONS = "لا توجد تنبيهات"
    
    fun formatDaysRemaining(days: Long): String {
        return when {
            days <= 0 -> TODAY
            days == 1L -> TOMORROW
            else -> "خلال $days $DAYS"
        }
    }
    
    // Format helpers
    fun formatCurrency(amount: Double): String {
        val formatted = String.format("%,.0f", amount)
        return "$formatted $SAR"
    }
    
    fun formatPaymentNumber(number: Int): String {
        return when (number) {
            1 -> FIRST_PAYMENT
            2 -> SECOND_PAYMENT
            else -> "الدفعة $number"
        }
    }
    
    fun formatStatus(status: code.yousef.portfolio.building.model.PaymentStatus): String {
        return when (status) {
            code.yousef.portfolio.building.model.PaymentStatus.PAID -> PAID
            code.yousef.portfolio.building.model.PaymentStatus.PENDING -> PENDING
            code.yousef.portfolio.building.model.PaymentStatus.OVERDUE -> OVERDUE
        }
    }
}
