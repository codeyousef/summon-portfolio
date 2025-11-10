package code.yousef.portfolio.docs

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class WebhookHandler(
    private val docsService: DocsService,
    private val cache: DocsCache,
    private val config: DocsConfig,
    private val docsCatalog: DocsCatalog,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val logger = LoggerFactory.getLogger(WebhookHandler::class.java)

    suspend fun handle(call: ApplicationCall) {
        val payload = call.receiveText()
        val event = runCatching { json.decodeFromString<GithubPushEvent>(payload) }.getOrNull()
        if (event?.ref?.endsWith("/${config.defaultBranch}") == true) {
            val prefix = "${config.defaultBranch}:${config.normalizedDocsRoot}/"
            cache.invalidatePrefix(prefix)
            docsService.invalidateNavTree()
            docsCatalog.reload()
            logger.info("Docs cache invalidated due to webhook for ${event.ref}")
        }
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
}

@Serializable
data class GithubPushEvent(
    val ref: String? = null
)
