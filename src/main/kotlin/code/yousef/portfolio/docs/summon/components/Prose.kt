package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.RichText
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.gap

@Composable
fun Prose(html: String) {
    // Style links in markdown content for better visibility
    GlobalStyle(
        """
        .prose a {
            color: ${PortfolioTheme.Colors.LINK};
            text-decoration: underline;
            text-underline-offset: 2px;
        }
        .prose a:hover {
            color: ${PortfolioTheme.Colors.LINK_HOVER};
        }
        .prose a:visited {
            color: ${PortfolioTheme.Colors.LINK};
        }
        .prose code {
            background-color: ${PortfolioTheme.Colors.BORDER};
            padding: 0.15em 0.4em;
            border-radius: 4px;
            font-size: 0.9em;
        }
        .prose pre code {
            background-color: transparent;
            padding: 0;
        }
        .prose pre {
            background-color: #0b0d12;
            padding: 1rem;
            border-radius: 8px;
            overflow-x: auto;
        }
        """
    )
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.xl)
            .gap(PortfolioTheme.Spacing.md)
            .alignItems(codes.yousef.summon.modifier.AlignItems.Stretch)
            .className("prose")
    ) {
        RichText(
            html,
            modifier = Modifier()
                .fontSize(1.rem)
                .lineHeight(1.7)
                .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                .fontFamily(PortfolioTheme.Typography.FONT_SANS)
        )
    }
}
