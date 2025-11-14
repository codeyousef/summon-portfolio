package code.yousef.portfolio.ui.foundation

import codes.yousef.summon.runtime.CompositionLocal

data class PageChrome(
    val isAdminSession: Boolean = false,
    val adminUsername: String? = null
)

val LocalPageChrome = CompositionLocal.compositionLocalOf(PageChrome())
