package code.yousef.portfolio.routes

import code.yousef.portfolio.admin.auth.AdminSession
import code.yousef.portfolio.ssr.EnvironmentLinksRegistry
import code.yousef.portfolio.ssr.SummonPage
import code.yousef.portfolio.ssr.SummonRenderLock
import code.yousef.portfolio.ssr.resolveEnvironmentLinks
import code.yousef.portfolio.ui.foundation.LocalPageChrome
import codes.yousef.summon.integration.ktor.KtorRenderer.Companion.respondSummonHydrated
import codes.yousef.summon.runtime.getPlatformRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*

suspend fun ApplicationCall.respondSummonPage(
    page: SummonPage,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    SummonRenderLock.withLock {
        val host = request.host()
        val links = resolveEnvironmentLinks(host)
        EnvironmentLinksRegistry.withLinks(links) {
            respondSummonHydrated(status) {
                val renderer = getPlatformRenderer()
                val session = sessions.get<AdminSession>()
                val chrome =
                    session?.let { page.chrome.copy(isAdminSession = true, adminUsername = it.username) } ?: page.chrome
                val provider = LocalPageChrome.provides(chrome)
                provider.current
                // Render head first with metadata, then body content
                renderer.renderHeadElements(page.head)
                page.content()
            }
        }
    }
}
