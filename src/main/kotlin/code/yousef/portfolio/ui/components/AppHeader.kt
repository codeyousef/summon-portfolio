package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.modifier.Modifier

/**
 * Compatibility entry point for portfolio pages.
 *
 * All public surfaces now render the same semantic site navigation and resolve
 * owned-site destinations centrally from EnvironmentLinks.
 */
@Composable
fun AppHeader(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    activeDestination: GlobalNavigationDestination? = null,
    context: PageNavigationContext? = null,
    compact: Boolean = false,
    showLocale: Boolean = true,
) {
    SiteNavigation(
        locale = locale,
        activeDestination = activeDestination,
        context = context,
        modifier = modifier,
        compact = compact,
        showLocale = showLocale,
    )
}
