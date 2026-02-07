package code.yousef.portfolio.content.seed

import code.yousef.portfolio.content.model.MusicGenre
import code.yousef.portfolio.content.model.MusicTrack
import code.yousef.portfolio.i18n.LocalizedText

object MusicSeed {
    val tracks: List<MusicTrack> = listOf(
        MusicTrack(
            id = "lattice-dreams",
            title = LocalizedText("Lattice Dreams", "أحلام الشبكة"),
            description = LocalizedText(
                "Ambient piece exploring polyrhythmic structures and granular synthesis.",
                "مقطوعة محيطة تستكشف الهياكل متعددة الإيقاعات والتركيب الحبيبي."
            ),
            genre = MusicGenre.AMBIENT,
            year = 2024,
            audioUrl = "/static/music/lattice-dreams.mp3",
            coverArtUrl = "/static/music/covers/lattice-dreams.jpg",
            duration = 324, // 5:24
            featured = true,
            order = 0,
            bpm = 72,
            tags = listOf("ambient", "granular", "meditation")
        ),
        MusicTrack(
            id = "compiler-error",
            title = LocalizedText("Compiler Error", "خطأ في المترجم"),
            description = LocalizedText(
                "Glitchy electronic track built from sonified error messages and stack traces.",
                "مسار إلكتروني متقطع مبني من رسائل الخطأ وتتبعات المكدس المحولة إلى صوت."
            ),
            genre = MusicGenre.ELECTRONIC,
            year = 2024,
            audioUrl = "/static/music/compiler-error.mp3",
            coverArtUrl = "/static/music/covers/compiler-error.jpg",
            duration = 248, // 4:08
            featured = true,
            order = 1,
            bpm = 140,
            tags = listOf("glitch", "electronic", "data-driven")
        ),
        MusicTrack(
            id = "hearthshire-main-theme",
            title = LocalizedText("Hearthshire - Main Theme", "هارثشاير - اللحن الرئيسي"),
            description = LocalizedText(
                "Orchestral theme composed for the Hearthshire game project.",
                "لحن أوركسترالي مؤلف لمشروع لعبة هارثشاير."
            ),
            genre = MusicGenre.ORCHESTRAL,
            year = 2023,
            audioUrl = "/static/music/hearthshire-theme.mp3",
            coverArtUrl = "/static/music/covers/hearthshire-theme.jpg",
            duration = 187, // 3:07
            featured = true,
            order = 2,
            tags = listOf("orchestral", "game", "epic")
        ),
        MusicTrack(
            id = "recursive-descent",
            title = LocalizedText("Recursive Descent", "النزول التكراري"),
            description = LocalizedText(
                "Experimental piece using self-similar melodic structures.",
                "مقطوعة تجريبية تستخدم هياكل لحنية متشابهة ذاتياً."
            ),
            genre = MusicGenre.EXPERIMENTAL,
            year = 2024,
            audioUrl = "/static/music/recursive-descent.mp3",
            coverArtUrl = "/static/music/covers/recursive-descent.jpg",
            duration = 412, // 6:52
            featured = false,
            order = 3,
            bpm = 95,
            tags = listOf("experimental", "fractal", "generative")
        ),
        MusicTrack(
            id = "desert-wind",
            title = LocalizedText("Desert Wind", "رياح الصحراء"),
            description = LocalizedText(
                "Field recordings from the Arabian desert processed through custom reverbs.",
                "تسجيلات ميدانية من الصحراء العربية معالجة عبر صدى مخصص."
            ),
            genre = MusicGenre.AMBIENT,
            year = 2022,
            audioUrl = "/static/music/desert-wind.mp3",
            coverArtUrl = "/static/music/covers/desert-wind.jpg",
            duration = 518, // 8:38
            featured = false,
            order = 4,
            tags = listOf("field-recording", "ambient", "nature")
        )
    )
}
