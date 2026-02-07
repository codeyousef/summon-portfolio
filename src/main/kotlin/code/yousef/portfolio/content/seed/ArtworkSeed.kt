package code.yousef.portfolio.content.seed

import code.yousef.portfolio.content.model.Artwork
import code.yousef.portfolio.content.model.ArtworkMedium
import code.yousef.portfolio.i18n.LocalizedText

object ArtworkSeed {
    val artworks: List<Artwork> = listOf(
        Artwork(
            id = "neural-dreams-01",
            title = LocalizedText("Neural Dreams I", "أحلام عصبية I"),
            description = LocalizedText(
                "An exploration of latent space landscapes generated through custom diffusion models.",
                "استكشاف لمناظر الفضاء الكامن المولدة عبر نماذج انتشار مخصصة."
            ),
            medium = ArtworkMedium.DIGITAL,
            year = 2024,
            imageUrl = "/static/art/neural-dreams-01.jpg",
            featured = true,
            order = 0,
            tags = listOf("ai", "generative", "landscape")
        ),
        Artwork(
            id = "voxel-cathedral",
            title = LocalizedText("Voxel Cathedral", "كاتدرائية الفوكسل"),
            description = LocalizedText(
                "Procedurally generated gothic architecture rendered in the Hearthshire engine.",
                "هندسة قوطية مولدة إجرائياً مرسومة في محرك هارثشاير."
            ),
            medium = ArtworkMedium.DIGITAL,
            year = 2023,
            imageUrl = "/static/art/voxel-cathedral.jpg",
            featured = true,
            order = 1,
            dimensions = "4K",
            tags = listOf("voxel", "procedural", "architecture")
        ),
        Artwork(
            id = "syntax-trees",
            title = LocalizedText("Syntax Trees", "أشجار النحو"),
            description = LocalizedText(
                "Visual representation of abstract syntax trees from the Seen compiler.",
                "تمثيل بصري لأشجار النحو المجردة من مترجم سين."
            ),
            medium = ArtworkMedium.DIGITAL,
            year = 2024,
            imageUrl = "/static/art/syntax-trees.jpg",
            featured = false,
            order = 2,
            tags = listOf("code", "visualization", "compiler")
        ),
        Artwork(
            id = "desert-silence",
            title = LocalizedText("Desert Silence", "صمت الصحراء"),
            description = LocalizedText(
                "Photography from the Empty Quarter - vast dunes at golden hour.",
                "تصوير من الربع الخالي - كثبان شاسعة في الساعة الذهبية."
            ),
            medium = ArtworkMedium.PHOTOGRAPHY,
            year = 2022,
            imageUrl = "/static/art/desert-silence.jpg",
            featured = true,
            order = 3,
            tags = listOf("photography", "landscape", "desert")
        ),
        Artwork(
            id = "shader-studies-01",
            title = LocalizedText("Shader Studies I", "دراسات التظليل I"),
            description = LocalizedText(
                "Real-time GLSL experiments exploring light diffraction and caustics.",
                "تجارب GLSL في الوقت الفعلي تستكشف انكسار الضوء والمقوسات."
            ),
            medium = ArtworkMedium.DIGITAL,
            year = 2024,
            imageUrl = "/static/art/shader-studies-01.jpg",
            featured = false,
            order = 4,
            tags = listOf("shader", "glsl", "light")
        ),
        Artwork(
            id = "ink-mountains",
            title = LocalizedText("Ink Mountains", "جبال الحبر"),
            description = LocalizedText(
                "Traditional ink wash painting inspired by classical Chinese landscape tradition.",
                "لوحة حبر تقليدية مستوحاة من تقاليد المناظر الطبيعية الصينية الكلاسيكية."
            ),
            medium = ArtworkMedium.TRADITIONAL,
            year = 2021,
            imageUrl = "/static/art/ink-mountains.jpg",
            featured = false,
            order = 5,
            dimensions = "30x40cm",
            tags = listOf("ink", "traditional", "landscape")
        )
    )
}
