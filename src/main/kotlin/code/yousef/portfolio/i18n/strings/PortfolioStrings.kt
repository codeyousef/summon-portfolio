package code.yousef.portfolio.i18n.strings

import code.yousef.portfolio.i18n.LocalizedText

object PortfolioStrings {
    // Hero section
    object Hero {
        val headline = LocalizedText(
            en = "Bridging the JVM and the GPU.",
            ar = "الجسر بين JVM و GPU."
        )
        val subheadline = LocalizedText(
            en = "I build high-performance UI frameworks and graphics engines using Kotlin Multiplatform.",
            ar = "أبني أطر واجهات مستخدم عالية الأداء ومحركات رسوميات باستخدام Kotlin Multiplatform."
        )
        val viewStack = LocalizedText(
            en = "View the Stack",
            ar = "عرض الحزمة"
        )
        val sourceCode = LocalizedText(
            en = "⌘ Source Code",
            ar = "⌘ الكود المصدري"
        )
    }

    // Trinity section - library descriptions
    object Trinity {
        // Summon
        val summonTagline = LocalizedText(
            en = "Declarative UI, Purely in Kotlin.",
            ar = "واجهات تصريحية، بالكامل بـ Kotlin."
        )
        val summonDescription = LocalizedText(
            en = "A type-safe frontend framework for JVM backends. Zero JavaScript glue code. Shared data models from database to DOM.",
            ar = "إطار واجهات آمن للأنواع لخوادم JVM. بدون كود JavaScript وسيط. نماذج بيانات مشتركة من قاعدة البيانات إلى DOM."
        )
        
        // Sigil
        val sigilTagline = LocalizedText(
            en = "Declarative 3D for Kotlin.",
            ar = "رسوميات ثلاثية الأبعاد تصريحية لـ Kotlin."
        )
        val sigilDescription = LocalizedText(
            en = "The \"React-Three-Fiber\" for Kotlin Multiplatform. Compose complex 3D scenes using the same declarative syntax as your UI. Manage scene graphs, lights, and meshes as reactive components.",
            ar = "\"React-Three-Fiber\" لـ Kotlin Multiplatform. قم بتركيب مشاهد ثلاثية الأبعاد معقدة باستخدام نفس الصياغة التصريحية لواجهتك. أدر رسوم المشهد والأضواء والشبكات كمكونات تفاعلية."
        )
        
        // Materia
        val materiaTagline = LocalizedText(
            en = "Unified Rendering Core.",
            ar = "نواة رسم موحدة."
        )
        val materiaDescription = LocalizedText(
            en = "The high-performance engine that orchestrates it all. Optimized for battery life on mobile and flexibility on the web.",
            ar = "المحرك عالي الأداء الذي ينسق كل شيء. محسّن لعمر البطارية على الهاتف والمرونة على الويب."
        )
    }

    // Philosophy section
    object Philosophy {
        val title = LocalizedText(
            en = "The 'One Developer' Philosophy.",
            ar = "فلسفة 'المطور الواحد'."
        )
        val body = LocalizedText(
            en = "Modern software has become overly complex. I believe in radical simplification. By using unified languages like Kotlin across the stack, I eliminate the friction between 'backend' and 'frontend' teams. One developer, one language, one codebase — from server logic to mobile UI to web applications.",
            ar = "أصبحت البرمجيات الحديثة معقدة بشكل مفرط. أؤمن بالتبسيط الجذري. باستخدام لغات موحدة مثل Kotlin عبر كل الطبقات، أزيل الاحتكاك بين فرق 'الخادم' و'الواجهة'. مطور واحد، لغة واحدة، قاعدة كود واحدة — من منطق الخادم إلى واجهة الهاتف إلى تطبيقات الويب."
        )
    }

    // Selected Engineering section
    object Engineering {
        val title = LocalizedText(
            en = "Selected Engineering",
            ar = "هندسة مختارة"
        )
        val summonSubtitle = LocalizedText(
            en = "The Frontend Framework",
            ar = "إطار الواجهات الأمامية"
        )
        val sigilSubtitle = LocalizedText(
            en = "The 3D Composition Tool",
            ar = "أداة تركيب ثلاثية الأبعاد"
        )
        val materiaSubtitle = LocalizedText(
            en = "The Rendering Engine",
            ar = "محرك الرسم"
        )
        val docs = LocalizedText(
            en = "Read the Docs",
            ar = "اقرأ الوثائق"
        )
        val github = LocalizedText(
            en = "View on GitHub",
            ar = "عرض على GitHub"
        )
    }
}
