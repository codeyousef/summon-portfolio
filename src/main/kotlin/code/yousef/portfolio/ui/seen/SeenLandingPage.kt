package code.yousef.portfolio.ui.seen

import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.LandingBranding
import code.yousef.portfolio.ui.components.LandingNavbar
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.foundation.SectionWrap
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.ButtonLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

private const val SEEN_ACCENT = "#58a6ff"
private const val SEEN_GITHUB_URL = "https://github.com/YousefCodeworx/seen"

@Composable
fun SeenLandingPage(playgroundUrl: String) {
    PageScaffold(locale = PortfolioLocale.EN) {
        LandingNavbar(
            branding = LandingBranding.seen(playgroundUrl, SEEN_GITHUB_URL)
        )
        SeenHero(playgroundUrl)
        SeenFeatureGrid()
        SeenCodeExample()
        SeenCtaFooter(playgroundUrl)
        PortfolioFooter(locale = PortfolioLocale.EN)
    }
}

@Composable
private fun SeenHero(playgroundUrl: String) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            Text(
                text = "Seen",
                modifier = Modifier()
                    .fontSize(4.rem)
                    .fontWeight(900)
                    .letterSpacing("-0.02em")
                    .color(SEEN_ACCENT)
                    .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
            )
            Paragraph(
                text = "A multi-language systems programming language with a self-hosted compiler and LLVM backend. Write code using keywords in your native language.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontSize(1.3.rem)
                    .lineHeight(1.7)
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.sm)
                    .flexWrap(FlexWrap.Wrap)
            ) {
                ButtonLink(
                    label = "Try the Playground",
                    href = playgroundUrl,
                    modifier = Modifier()
                        .backgroundColor(SEEN_ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("seen-cta" to "playground"),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
                ButtonLink(
                    label = "View on GitHub",
                    href = SEEN_GITHUB_URL,
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .backgroundColor("transparent")
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .padding(PortfolioTheme.Spacing.sm, PortfolioTheme.Spacing.lg)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("seen-cta" to "github"),
                    target = "_blank",
                    rel = "noopener noreferrer",
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
            }
        }
    }
}

private data class Feature(val title: String, val description: String)

private val seenFeatures = listOf(
    Feature(
        title = "Multi-Language Keywords",
        description = "Write code using keywords in English, Arabic, or other languages. Seen's lexer supports swappable keyword tables for native-language programming."
    ),
    Feature(
        title = "Self-Hosted Compiler",
        description = "The Seen compiler is written in Seen itself. A fully bootstrapped, self-hosting compiler that compiles its own source code."
    ),
    Feature(
        title = "LLVM Backend",
        description = "Generates optimized native code via LLVM IR. Get production-grade performance with automatic optimizations from the LLVM toolchain."
    ),
    Feature(
        title = "Interactive Playground",
        description = "Try Seen directly in your browser with a full-featured code editor, example programs, and instant compilation feedback."
    )
)

@Composable
private fun SeenFeatureGrid() {
    SectionWrap {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.md)
                .flexWrap(FlexWrap.Wrap)
        ) {
            seenFeatures.forEach { feature ->
                Column(
                    modifier = Modifier()
                        .flex(grow = 1, shrink = 1, basis = "280px")
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.lg)
                        .padding(PortfolioTheme.Spacing.lg)
                        .gap(PortfolioTheme.Spacing.sm)
                ) {
                    Text(
                        text = feature.title,
                        modifier = Modifier()
                            .fontWeight(700)
                    )
                    Paragraph(
                        text = feature.description,
                        modifier = Modifier()
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    )
                }
            }
        }
    }
}

@Composable
private fun SeenCodeExample() {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            Text(
                text = "Familiar syntax, powerful features",
                modifier = Modifier()
                    .fontSize(1.5.rem)
                    .fontWeight(700)
            )
            Column(
                modifier = Modifier()
                    .backgroundColor("#0d1117")
                    .borderRadius(PortfolioTheme.Radii.lg)
                    .padding(PortfolioTheme.Spacing.lg)
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .overflow(Overflow.Auto)
            ) {
                Text(
                    text = """class Animal {
    var name: string
    var sound: string

    init(name: string, sound: string) {
        self.name = name
        self.sound = sound
    }

    func speak() {
        print(self.name + " says " + self.sound)
    }
}

var cat = Animal("Cat", "Meow")
cat.speak()  // Cat says Meow""",
                    modifier = Modifier()
                        .fontFamily("'JetBrains Mono', 'Fira Code', monospace")
                        .fontSize(0.85.rem)
                        .lineHeight(1.6)
                        .color("#e6edf3")
                        .whiteSpace(WhiteSpace.Pre)
                )
            }
            Paragraph(
                text = "Seen supports classes, inheritance, closures, and more—all compiled to native code through LLVM. The self-hosted compiler ensures the language eats its own dog food.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }
    }
}

@Composable
private fun SeenCtaFooter(playgroundUrl: String) {
    SectionWrap {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.lg)
                .padding(PortfolioTheme.Spacing.xxl, 0.px)
        ) {
            Text(
                text = "Ready to try Seen?",
                modifier = Modifier()
                    .fontSize(1.75.rem)
                    .fontWeight(700)
                    .textAlign(TextAlign.Center)
            )
            Paragraph(
                text = "Jump into the interactive playground and start writing Seen code in your browser.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .textAlign(TextAlign.Center)
                    .maxWidth(600.px)
            )
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .gap(PortfolioTheme.Spacing.md)
                    .flexWrap(FlexWrap.Wrap)
                    .justifyContent(JustifyContent.Center)
            ) {
                ButtonLink(
                    label = "Open Playground",
                    href = playgroundUrl,
                    modifier = Modifier()
                        .backgroundColor(SEEN_ACCENT)
                        .color("#ffffff")
                        .padding(PortfolioTheme.Spacing.md, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("seen-cta" to "footer-playground"),
                    target = null,
                    rel = null,
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
                ButtonLink(
                    label = "View on GitHub",
                    href = SEEN_GITHUB_URL,
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .backgroundColor("transparent")
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                        .padding(PortfolioTheme.Spacing.md, PortfolioTheme.Spacing.xl)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .fontWeight(700)
                        .whiteSpace(WhiteSpace.NoWrap),
                    navigationMode = LinkNavigationMode.Native,
                    dataAttributes = mapOf("seen-cta" to "github"),
                    target = "_blank",
                    rel = "noopener noreferrer",
                    title = null,
                    id = null,
                    ariaLabel = null,
                    ariaDescribedBy = null,
                    dataHref = null
                )
            }
        }
    }
}
