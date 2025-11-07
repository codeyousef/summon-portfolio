package code.yousef.portfolio.content.seed

import code.yousef.portfolio.content.model.*
import code.yousef.portfolio.i18n.LocalizedText
import java.time.LocalDate

object PortfolioContentSeed {
    val hero: HeroContent = HeroContent(
        eyebrow = LocalizedText(
            en = "Scroll ↓ to enter the stack",
            ar = "مرر للأسفل للدخول إلى المكدس"
        ),
        titlePrimary = LocalizedText(
            en = "Engineering",
            ar = "الهندسة"
        ),
        titleSecondary = LocalizedText(
            en = "from First Principles.",
            ar = "من المبادئ الأولى."
        ),
        subtitle = LocalizedText(
            en = "I craft immersive software experiences by layering high-performance systems work, Summon’s composable UI primitives, and expressive storytelling surfaces.",
            ar = "أصمم تجارب برمجية غامرة عبر تكديس هندسة الأنظمة عالية الأداء، ومكونات Summon التركيبية، وسرد قصصي تفاعلي."
        ),
        ctaPrimary = LocalizedText("Explore Work", "استكشف الأعمال"),
        ctaSecondary = LocalizedText("Open Contact Form", "افتح نموذج الاتصال"),
        metrics = listOf(
            HeroMetric(
                value = "10+",
                label = LocalizedText("Years Building Systems", "سنوات في بناء الأنظمة"),
                detail = LocalizedText("From compilers to creative engines.", "من المترجمات إلى محركات الإبداع.")
            ),
            HeroMetric(
                value = "3",
                label = LocalizedText("Product Layers", "طبقات المنتج"),
                detail = LocalizedText("Language → Framework → Experience.", "لغة → إطار → تجربة.")
            ),
            HeroMetric(
                value = "2",
                label = LocalizedText("Live Languages", "لغتان"),
                detail = LocalizedText("English + Arabic with RTL support.", "الإنجليزية + العربية مع دعم الاتجاه من اليمين لليسار.")
            )
        )
    )

    val projects: List<Project> = listOf(
        Project(
            id = "seen",
            slug = "seen-language",
            layerLabel = LocalizedText("LAYER 1", "الطبقة 1"),
            layerName = LocalizedText("THE LANGUAGE", "اللغة"),
            title = LocalizedText("Seen (س)", "سين (س)"),
            description = LocalizedText(
                en = "A revolutionary systems language built on evidence-based syntax. Here, abstract logic and compiler theory take form.",
                ar = "لغة أنظمة ثورية مبنية على بناء جملة قائم على الأدلة. هنا يأخذ المنطق المجرد ونظرية المترجمات شكلاً."
            ),
            category = ProjectCategory.WEB,
            featured = true,
            order = 0,
            technologies = listOf("Rust", "WASM", "Compiler Design")
        ),
        Project(
            id = "summon",
            slug = "summon-framework",
            layerLabel = LocalizedText("LAYER 2", "الطبقة 2"),
            layerName = LocalizedText("THE FRAMEWORK", "الإطار"),
            title = LocalizedText("Summon", "سمون"),
            description = LocalizedText(
                en = "The language provides structure. With Summon, that structure becomes tangible UI components—the building blocks of modern applications.",
                ar = "توفر اللغة البنية. مع سمون، تصبح هذه البنية مكونات واجهة مستخدم ملموسة — اللبنات الأساسية للتطبيقات الحديثة."
            ),
            category = ProjectCategory.WEB,
            featured = true,
            order = 1,
            technologies = listOf("Kotlin", "KMP", "Compose", "Ktor")
        ),
        Project(
            id = "hearthshire",
            slug = "hearthshire-experience",
            layerLabel = LocalizedText("LAYER 3", "الطبقة 3"),
            layerName = LocalizedText("THE EXPERIENCE", "التجربة"),
            title = LocalizedText("Hearthshire", "هارثشاير"),
            description = LocalizedText(
                en = "The final layer, where systems and components breathe life into a world. A custom Rust engine powers a universe of 10 billion voxels, rendered with a neural, painterly touch.",
                ar = "الطبقة الأخيرة، حيث تبث الأنظمة والمكونات الحياة في عالم. محرك Rust مخصص يشغل كونًا من 10 مليارات فوكسل بلمسة فنية عصبية."
            ),
            category = ProjectCategory.GAME,
            featured = true,
            order = 2,
            technologies = listOf("Rust", "Neural Rendering", "Cloud GPU")
        )
    )

    val services: List<Service> = listOf(
        Service(
            id = "systems",
            title = LocalizedText("Systems & Engine Dev", "تطوير الأنظمة والمحركات"),
            description = LocalizedText(
                en = "Custom language design, compiler development, and high-performance engine architecture.",
                ar = "تصميم لغات مخصصة، تطوير المترجمات، وبناء محركات عالية الأداء."
            ),
            featured = true,
            order = 0
        ),
        Service(
            id = "framework",
            title = LocalizedText("Framework Design", "تصميم الأطر"),
            description = LocalizedText(
                en = "Composable UI frameworks, design systems, and integration tooling for cross-platform stacks.",
                ar = "أطر واجهة مستخدم تركيبية، وأنظمة تصميم، وأدوات تكامل لمكدسات متعددة المنصات."
            ),
            featured = true,
            order = 1
        ),
        Service(
            id = "interactive",
            title = LocalizedText("Interactive Experiences", "التجارب التفاعلية"),
            description = LocalizedText(
                en = "Unique WebGL, 3D, and creative web experiences that push technical and artistic boundaries.",
                ar = "تجارب WebGL و3D وتجارب ويب إبداعية فريدة تدفع الحدود التقنية والفنية."
            ),
            featured = true,
            order = 2
        )
    )

    val blogPosts: List<BlogPost> = listOf(
        BlogPost(
            id = "welcome",
            slug = "welcome-to-my-blog",
            title = LocalizedText("Welcome to My Blog", "مرحبًا بك في مدونتي"),
            excerpt = LocalizedText(
                en = "Thoughts on systems engineering, creative coding, and building from first principles.",
                ar = "أفكار حول هندسة الأنظمة والبرمجة الإبداعية والبناء من المبادئ الأولى."
            ),
            content = LocalizedText(
                en = "Full post content coming soon.",
                ar = "المحتوى الكامل للمقال قادم قريبًا."
            ),
            publishedAt = LocalDate.of(2024, 1, 1),
            featured = true,
            author = "Yousef Baitalmal",
            tags = listOf("engineering", "introduction", "coding")
        )
    )
}
