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
import io.ktor.server.sessions.*

@Composable
private fun FullPage(page: SummonPage) {
    // ✅ Only render content - skip head elements to avoid separate callback context
    page.content()
    
    // TODO: Head elements need to be rendered elsewhere or integrated into page.content()
}

suspend fun ApplicationCall.respondSummonPage(
    page: SummonPage,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    SummonRenderLock.withLock {
        val host = request.host()
        val links = resolveEnvironmentLinks(host)
        EnvironmentLinksRegistry.withLinks(links) {
            respondSummonHydrated(status) {
                val session = sessions.get<AdminSession>()
                val chrome =
                    session?.let { page.chrome.copy(isAdminSession = true, adminUsername = it.username) } ?: page.chrome
                val provider = LocalPageChrome.provides(chrome)
                
                // ✅ Use provider and render everything in single composable
                provider.current
                FullPage(page)
            }
        }
    }
}
