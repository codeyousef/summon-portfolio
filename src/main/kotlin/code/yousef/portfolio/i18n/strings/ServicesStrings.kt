package code.yousef.portfolio.i18n.strings

import code.yousef.portfolio.i18n.LocalizedText

object ServicesStrings {
    // Services section
    val title = LocalizedText("Services", "الخدمات")
    val subtitle = LocalizedText(
        en = "Bridging imagination with implementation. Each engagement spans research, architecture, and product polish.",
        ar = "أجسر الخيال بالتنفيذ — من البحث إلى البنية إلى الصقل النهائي."
    )
    val viewFeatured = LocalizedText("View featured services", "عرض الخدمات المميزة")

    // Services overlay
    object Overlay {
        val title = LocalizedText("Services", "الخدمات")
        val subtitle = LocalizedText(
            en = "I help teams build foundational, high-performance products—covering research, architecture, and craft.",
            ar = "أساعد الفرق على بناء منتجات عالية الأداء عبر البحث والبنية والصقل."
        )
        val close = LocalizedText("Close", "إغلاق")
        val cta = LocalizedText("Contact me", "تواصل معي")
    }
}
