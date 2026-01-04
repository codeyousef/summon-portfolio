package code.yousef.portfolio.docs

import codes.yousef.aether.core.Exchange
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

    suspend fun handle(exchange: Exchange) {
        val payload = exchange.request.bodyText()
        val event = runCatching { json.decodeFromString<GithubPushEvent>(payload) }.getOrNull()
        if (event?.ref?.endsWith("/${config.defaultBranch}") == true) {
            val prefix = "${config.defaultBranch}:${config.normalizedDocsRoot}/"
            cache.invalidatePrefix(prefix)
            docsService.invalidateNavTree()
            docsCatalog.reload()
            logger.info("Docs cache invalidated due to webhook for ${event.ref}")
        }
        exchange.response.setHeader("Content-Type", "application/json")
        exchange.respond(200, "{\"status\": \"ok\"}")
    }
}

@Serializable
data class GithubPushEvent(val ref: String)
