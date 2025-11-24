package code.yousef.portfolio.ui.components

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.layout.ResponsiveLayout
import codes.yousef.summon.components.layout.ScreenSize
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.positionInset
import codes.yousef.summon.modifier.LayoutModifiers.top

@Composable
fun AppHeader(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier(),
    forceNativeLinks: Boolean = false,
    nativeBaseUrl: String? = null,
    docsBaseUrl: String? = null,
    forcePortfolioAnchors: Boolean = false
) {
    val paddingStart = if (locale.direction.equals("rtl", ignoreCase = true)) {
        "calc(${PortfolioTheme.Spacing.xl} + ${PortfolioTheme.Spacing.md})"
    } else {
        PortfolioTheme.Spacing.xl
    }
    val paddingEnd = if (locale.direction.equals("rtl", ignoreCase = true)) {
        PortfolioTheme.Spacing.xl
    } else {
        "calc(${PortfolioTheme.Spacing.xl} + ${PortfolioTheme.Spacing.md})"
    }
    val containerPaddingStart = "calc(${PortfolioTheme.Spacing.md} + $paddingStart)"
    val containerPaddingEnd = "calc(${PortfolioTheme.Spacing.md} + $paddingEnd)"

    val containerModifier = modifier
        .width(100.percent)
        .backgroundColor(PortfolioTheme.Colors.SURFACE)
        .padding(PortfolioTheme.Spacing.md)
        .position(Position.Fixed)
        .top(0.px)
        .positionInset(left = "0", right = "0")
        .zIndex(50)
        .let { base ->
            if (locale.direction.equals("rtl", ignoreCase = true)) {
                base
                    .paddingRight(containerPaddingStart)
                    .paddingLeft(containerPaddingEnd)
            } else {
                base
                    .paddingLeft(containerPaddingStart)
                    .paddingRight(containerPaddingEnd)
            }
        }

    val desktopContent = @Composable {
        DesktopHeader(
            locale = locale,
            modifier = Modifier().width(100.percent),
            forceNativeLinks = forceNativeLinks,
            nativeBaseUrl = nativeBaseUrl,
            docsBaseUrl = docsBaseUrl,
            forcePortfolioAnchors = forcePortfolioAnchors
        )
    }

    val mobileContent = @Composable {
        MobileHeader(
            locale = locale,
            modifier = Modifier().width(100.percent),
            forceNativeLinks = forceNativeLinks,
            nativeBaseUrl = nativeBaseUrl,
            docsBaseUrl = docsBaseUrl,
            forcePortfolioAnchors = forcePortfolioAnchors
        )
    }

    ResponsiveLayout(
        content = mapOf(
            ScreenSize.SMALL to mobileContent,
            ScreenSize.MEDIUM to mobileContent,
            ScreenSize.LARGE to desktopContent,
            ScreenSize.XLARGE to desktopContent
        ),
        defaultContent = desktopContent,
        modifier = containerModifier,
        serverSideScreenSize = ScreenSize.LARGE
    )
}
