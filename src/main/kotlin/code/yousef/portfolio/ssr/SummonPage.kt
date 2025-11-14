package code.yousef.portfolio.ssr

import code.yousef.portfolio.ui.foundation.PageChrome
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.seo.HeadScope

typealias ComposableContent = @Composable () -> Unit

data class SummonPage(
    val head: (HeadScope) -> Unit = {},
    val content: ComposableContent,
    val chrome: PageChrome = PageChrome()
)
