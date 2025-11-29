package code.yousef.portfolio.i18n.strings

import code.yousef.portfolio.i18n.LocalizedText

object BlogStrings {
    // Blog teaser section
    object Teaser {
        val title = LocalizedText("Latest Writing", "أحدث المقالات")
        val subtitle = LocalizedText(
            en = "Deep dives on systems design, tools, and creative engineering. New essays ship as soon as they're battle-tested.",
            ar = "مقالات متعمقة حول تصميم الأنظمة والأدوات والهندسة الإبداعية."
        )
        val readMore = LocalizedText("Read more →", "اقرأ المزيد ←")
        val viewAll = LocalizedText("View all posts", "عرض جميع المقالات")
    }

    // Blog list page
    object List {
        val title = LocalizedText("Thoughts & Writing", "أفكار وكتابات")
        val subtitle = LocalizedText(
            en = "Deep dives on systems, frameworks, and creative engineering.",
            ar = "مقالات متعمقة حول الأنظمة والأطر والهندسة الإبداعية."
        )
        val readMore = LocalizedText("Read more →", "اقرأ المزيد ←")
        val byLabel = LocalizedText("By", "بواسطة")
    }

    // Blog detail page
    object Detail {
        val byLabel = LocalizedText("By", "بواسطة")
        val featured = LocalizedText("Featured", "مميز")
        val back = LocalizedText("← Back to Blog", "← العودة إلى المدونة")
        val notFoundTitle = LocalizedText("Post not found", "المقال غير موجود")
        val notFoundBody = LocalizedText("Check other posts for more insights.", "اطلع على مقالات أخرى للمزيد من الأفكار.")
    }
}
