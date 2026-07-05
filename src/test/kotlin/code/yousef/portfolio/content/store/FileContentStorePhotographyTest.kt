package code.yousef.portfolio.content.store

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
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
}
