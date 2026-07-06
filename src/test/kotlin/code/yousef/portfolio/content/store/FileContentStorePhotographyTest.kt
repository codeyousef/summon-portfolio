package code.yousef.portfolio.content.store

import code.yousef.portfolio.content.model.PhotographyMediaType
import code.yousef.portfolio.content.model.PhotographySourceKind
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileContentStorePhotographyTest {

    @Test
    fun `missing photography list defaults to empty for older snapshots`() {
        val dir = Files.createTempDirectory("portfolio-content-test")
        val path = dir.resolve("content.json")
        path.writeText("""{"version":1}""")

        val store = FileContentStore(path)

        assertTrue(store.listPhotographyPhotos().isEmpty())
    }

    @Test
    fun `older photo records default to uploaded uncategorized photos`() {
        val dir = Files.createTempDirectory("portfolio-content-test")
        val path = dir.resolve("content.json")
        path.writeText(
            """
            {
              "version": 1,
              "photographyPhotos": [
                {
                  "id": "legacy",
                  "title": "Legacy Frame",
                  "altText": "Legacy alt",
                  "published": true,
                  "storageKey": "legacy.jpg",
                  "contentType": "image/jpeg"
                }
              ]
            }
            """.trimIndent()
        )

        val photo = FileContentStore(path).listPhotographyPhotos().single()

        assertEquals(PhotographyMediaType.PHOTO, photo.mediaType)
        assertEquals(PhotographySourceKind.UPLOAD, photo.sourceKind)
        assertEquals("Uncategorized", photo.category)
        assertEquals(false, photo.featured)
    }
}
