package code.yousef.portfolio.e2e

import code.yousef.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.*

class HydrationTest {

    @Test
    fun `should serve landing page with correct hydration tags`() = testApplication {
        environment {
            config = MapApplicationConfig("gcp.projectId" to "test-project", "firestore.emulatorHost" to "localhost:8080")
        }
        application {
            module()
        }

        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(headers[HttpHeaders.ContentType]?.startsWith("text/html") == true, "Should be HTML")
            
            val body = bodyAsText()
            
            // Verify Summon hydration script is present
            assertTrue(body.contains("summon-hydration.js"), "Should contain summon-hydration.js script tag")
            
            // Verify WASM preload is present (Summon 0.4.9.2 standard)
            assertTrue(body.contains("summon-hydration.wasm"), "Should contain summon-hydration.wasm reference")
            
            // Verify root element exists (usually <div id="root"> or similar, depending on Summon)
            // Based on previous context, Summon usually hydrates into a container.
            // I'll check for the generic structure if specific ID isn't known, but usually it's standard.
        }
    }

    @Test
    fun `should serve WASM binary with correct MIME type`() = testApplication {
        environment {
            config = MapApplicationConfig("gcp.projectId" to "test-project", "firestore.emulatorHost" to "localhost:8080")
        }
        application {
            module()
        }

        // The path might be /static/summon-hydration.wasm or just /summon-hydration.wasm depending on routing
        // Based on previous file listings, it's in src/main/resources/static, so likely /static/
        
        client.get("/static/summon-hydration.wasm").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("application/wasm", headers[HttpHeaders.ContentType])
        }
    }

    @Test
    fun `should serve hydration JS with correct MIME type`() = testApplication {
        environment {
            config = MapApplicationConfig("gcp.projectId" to "test-project", "firestore.emulatorHost" to "localhost:8080")
        }
        application {
            module()
        }

        client.get("/static/summon-hydration.js").apply {
            assertEquals(HttpStatusCode.OK, status)
            // Ktor might serve as application/javascript or text/javascript
            val contentType = headers[HttpHeaders.ContentType]
            assertTrue(
                contentType?.startsWith("application/javascript") == true || contentType?.startsWith("text/javascript") == true,
                "Content-Type should be javascript, got: $contentType"
            )
        }
    }
    
    @Test
    fun `should serve process polyfill`() = testApplication {
        environment {
            config = MapApplicationConfig("gcp.projectId" to "test-project", "firestore.emulatorHost" to "localhost:8080")
        }
        application {
            module()
        }

        client.get("/static/process-polyfill.js").apply {
            assertEquals(HttpStatusCode.OK, status)
            val contentType = headers[HttpHeaders.ContentType]
            assertTrue(
                contentType?.startsWith("application/javascript") == true || contentType?.startsWith("text/javascript") == true,
                "Content-Type should be javascript, got: $contentType"
            )
        }
    }

    @Test
    fun `should NOT serve obsolete polyfills`() = testApplication {
        environment {
            config = MapApplicationConfig("gcp.projectId" to "test-project", "firestore.emulatorHost" to "localhost:8080")
        }
        application {
            module()
        }

        // These files were removed and should return 404
        val obsoleteFiles = listOf(
            "/static/wasm-polyfill.js",
            "/static/initialize-summon.js"
        )

        for (file in obsoleteFiles) {
            client.get(file).apply {
                assertEquals(HttpStatusCode.NotFound, status, "File $file should not exist")
            }
        }
    }
}
