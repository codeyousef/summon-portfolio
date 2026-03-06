package code.yousef.portfolio.content.seed

import code.yousef.portfolio.content.model.*
import code.yousef.portfolio.i18n.LocalizedText
import java.time.LocalDate

object PortfolioContentSeed {
    val hero: HeroContent = HeroContent(
        eyebrow = LocalizedText(
            en = "Principal Engineer & Creative Director",
            ar = "مهندس أول ومبدع تجارب"
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
            en = "I craft immersive software experiences by layering languages, frameworks, and storytelling surfaces for teams who need expressive, high-performance products.",
            ar = "أصمم تجارب برمجية غامرة عبر تكديس اللغات والأطر وطبقات السرد لفِرق تحتاج إلى منتجات معبّرة وعالية الأداء."
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
                detail = LocalizedText(
                    "English + Arabic with RTL support.",
                    "الإنجليزية + العربية مع دعم الاتجاه من اليمين لليسار."
                )
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
                en = "The language provides structure. With %SUMMON%, that structure becomes tangible UI components—the building blocks of modern applications.",
                ar = "توفر اللغة البنية. مع %SUMMON%، تصبح هذه البنية مكونات واجهة مستخدم ملموسة — اللبنات الأساسية للتطبيقات الحديثة."
            ),
            category = ProjectCategory.WEB,
            featured = true,
            order = 1,
            technologies = listOf("Kotlin", "KMP", "Compose", "Aether")
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
            id = "why-i-built-seen",
            slug = "why-i-built-seen",
            title = "Why I Built a Programming Language",
            excerpt = "What if a programmer in Cairo didn't have to think in English to write code? That question led me to build Seen.",
            content = """What if a programmer in Cairo didn't have to think in English to write code? What if someone in Tokyo could use `関数` instead of `fn`, or someone in Riyadh could write `متغير` instead of `let`, with zero performance cost?

That question is what led me to build Seen, a systems programming language with Vale-style region-based memory safety, Kotlin-inspired syntax, and native multilingual keyword support. It ships with English, Arabic, Spanish, Japanese, Russian, and Chinese out of the box, with more languages coming.

But the question came later. The story starts with a game.

I've been working on a dream game for a while now, and I quickly realized I needed a custom engine. None of the existing ones could handle what I needed, and performance was critical. So I picked up Rust.

Coming from Kotlin, the learning curve wasn't the problem. The problem was that I just wasn't having fun. Between the Vulkan API and Rust's own friction, all it did was push me to do something I've always wanted to do: create my own programming language.

The idea started simple. Rust's features with Kotlin's syntax and idioms. That quickly felt very pointless, so I took a step back and actually researched language and syntax design. What's the most "fun" to write? What feels like a pleasure? Can Rust's memory model be improved?

That last question led me to Vale, with its region-based memory safety. So now I had my syntax, grammar, and memory model. And just so I wouldn't feel like a quitter, I decided to use Rust to build the compiler.

Then, early in the process, I had a thought. Why are all programming languages in English? I mean, I could guess why. It's probably the most spoken language in the world, at least as a second language. But why only English? What would it take to create a programming language that anyone can use in their native tongue, without the overhead of translating or memorizing keywords?

The first idea I had was having the keywords in an external file, like a TOML file, one for each language. That way, all anyone would need to do to add their language is copy the English file and swap the keywords. Then the compiler loads the right set at compile time.

That sounded perfect, so I didn't need any more ideas.

Luckily for me, I'm fluent in two languages already, so I'm the perfect test subject. Along with the language you're reading right now, I also speak Arabic. Those were the first two I built and tested with. From there I added Spanish, Japanese, Russian, and Chinese, with more on the way.

A simple language field in the `Seen.toml` project configuration file tells the compiler which TOML to load, and it worked. No performance issues either. And keep in mind, when I say something "worked," I'm never implying it worked on the first try or that it was easy. I mean eventually.

Oh, and the name. "Seen" as in to be seen, because the whole point is that programmers who don't speak English shouldn't be invisible. It's also the name of س, a letter in the Arabic alphabet, which felt right for a language born from the question of why code only speaks one language.

From there it was design decisions. Every project is restricted to a single language, no mixing and matching, for reasons I hope are obvious. I added a translate function, so if you clone a project that's written in Spanish, you can automatically translate it to your language based on the TOMLs. That also meant I had to translate the entire standard library, not just the keywords. But hey, that's what I signed up for.

So that's the story on why any of this exists. Next post, I'm going to get into the memory model and how Seen handles regions differently from Vale. If the multilingual stuff is what got your attention, I'll be writing about that too, specifically how the translation pipeline works and what it took to make Arabic feel native and not bolted on.

See you there.""".trimIndent(),
            publishedAt = LocalDate.of(2026, 3, 6),
            featured = true,
            author = "Yousef",
            tags = listOf("seen", "programming-languages", "multilingual", "rust", "compilers")
        ),
        BlogPost(
            id = "welcome",
            slug = "welcome-to-my-blog",
            title = "Welcome to My Blog",
            excerpt = "Thoughts on systems engineering, creative coding, and building from first principles.",
            content = "Full post content coming soon.",
            publishedAt = LocalDate.of(2024, 1, 1),
            featured = true,
            author = "Yousef",
            tags = listOf("engineering", "introduction", "coding")
        )
    )

    val testimonials: List<Testimonial> = emptyList()
}
