package code.yousef.portfolio.routes

import code.yousef.portfolio.admin.auth.AdminSession
import code.yousef.portfolio.ssr.EnvironmentLinksRegistry
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.ssr.SummonRenderLock
import code.yousef.portfolio.ssr.resolveEnvironmentLinks
import code.yousef.portfolio.ui.foundation.LocalPageChrome
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.integration.ktor.KtorRenderer.Companion.respondSummonHydrated
import codes.yousef.summon.runtime.getPlatformRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*

import codes.yousef.summon.integration.ktor.KtorRenderer.Companion.respondSummonHydrated

suspend fun ApplicationCall.respondSummonPage(
    page: SummonPage,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    SummonRenderLock.withLock {
        val host = request.host()
        val links = resolveEnvironmentLinks(host)
        EnvironmentLinksRegistry.withLinks(links) {
            // Manually render to intercept and clean up hydration data
            val renderer = getPlatformRenderer()
            val html = renderer.renderComposableRootWithHydration {
                val session = sessions.get<AdminSession>()
                val chrome =
                    session?.let { page.chrome.copy(isAdminSession = true, adminUsername = it.username) } ?: page.chrome
                
                // Render head elements
                renderer.renderHeadElements(page.head)

                // TODO: Use CompositionLocalProvider(LocalPageChrome provides chrome) when available
                page.content()
            }

            // Clean up whitespace in hydration data to prevent parsing errors
            val marker = "id=\"summon-hydration-data\">"
            val cleanedHtml = if (html.contains(marker)) {
                val before = html.substringBefore(marker)
                val afterMarker = html.substringAfter(marker)
                val content = afterMarker.substringBefore("</script>")
                val afterScript = afterMarker.substringAfter("</script>")
                
                before + marker + content.trim() + "</script>" + afterScript
            } else {
                html
            }

            this@respondSummonPage.respondText(cleanedHtml, ContentType.Text.Html, status)
        }
    }
}
