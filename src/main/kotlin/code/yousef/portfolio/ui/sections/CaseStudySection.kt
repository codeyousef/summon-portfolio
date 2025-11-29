package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.CaseStudyStrings
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.CodeBlock
import code.yousef.portfolio.ui.components.SplitView
import code.yousef.portfolio.ui.components.WindowFrame
import code.yousef.portfolio.ui.foundation.ContentSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight

@Composable
fun CaseStudySection(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    ContentSection(modifier = modifier) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            // Section header
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.sm)
            ) {
                Text(
                    text = CaseStudyStrings.title.resolve(locale),
                    modifier = Modifier()
                        .fontSize(2.5.rem)
                        .fontWeight(700)
                )
                Text(
                    text = CaseStudyStrings.body.resolve(locale),
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .lineHeight(1.8)
                )
            }

            // Split view: Code left, Preview right
            SplitView(
                locale = locale,
                left = {
                    WindowFrame(title = "Card.kt") {
                        CodeBlock(
                            lines = listOf(
                                "@Composable",
                                "fun Card(title: String) {",
                                "    Box(modifier = Modifier()",
                                "        .backgroundColor(\"#1a1a2e\")",
                                "        .borderRadius(16.px)",
                                "        .padding(24.px)",
                                "    ) {",
                                "        Text(title)",
                                "    }",
                                "}"
                            ),
                            showCopyButton = false
                        )
                    }
                },
                right = {
                    WindowFrame(title = "Preview") {
                        Box(
                            modifier = Modifier()
                                .display(Display.Flex)
                                .alignItems(AlignItems.Center)
                                .justifyContent(JustifyContent.Center)
                                .padding(PortfolioTheme.Spacing.xl)
                                .background(PortfolioTheme.Colors.BACKGROUND)
                                .minHeight("200px")
                        ) {
                            // Rendered Card preview
                            Box(
                                modifier = Modifier()
                                    .backgroundColor("#1a1a2e")
                                    .borderRadius(16.px)
                                    .padding(24.px)
                            ) {
                                Text(
                                    text = CaseStudyStrings.cardTitle.resolve(locale),
                                    modifier = Modifier()
                                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                                        .fontWeight(600)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

