package code.yousef.portfolio.ui.admin

import code.yousef.portfolio.docs.MarkdownDocument
import code.yousef.portfolio.docs.summon.components.Prose
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.Form
import codes.yousef.summon.components.forms.FormButton
import codes.yousef.summon.components.forms.FormButtonVariant
import codes.yousef.summon.components.forms.FormMethod
import codes.yousef.summon.components.forms.FormTextArea
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

@Composable
fun MarkdownPreviewPage(
    source: String,
    document: MarkdownDocument?,
    errorMessage: String? = null,
) {
    PageScaffold(locale = PortfolioLocale.EN, enableAuroraEffects = false) {
        AppHeader(locale = PortfolioLocale.EN, compact = true, showLocale = false)
        Column(
            modifier = Modifier()
                .width(100.percent)
                .maxWidth(1180.px)
                .margin("0 auto")
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.xl)
        ) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.sm)
            ) {
                Text(
                    text = "Markdown Preview",
                    modifier = Modifier()
                        .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
                        .fontSize(2.7.rem)
                        .fontWeight(800)
                        .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                )
                Paragraph(
                    text = "Preview blog content through the production CommonMark and typed Summon renderer.",
                    modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY).lineHeight(1.6)
                )
                AnchorLink(
                    label = "Back to admin",
                    href = "/admin",
                    modifier = Modifier()
                        .color(PortfolioTheme.Colors.ACCENT_ALT)
                        .textDecoration(TextDecoration.None)
                        .fontWeight(700),
                    navigationMode = LinkNavigationMode.Native
                )
            }

            Form(
                action = "/admin/markdown-preview",
                method = FormMethod.Post,
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.md)
            ) {
                FormTextArea(
                    name = "markdown",
                    label = "Markdown",
                    defaultValue = source,
                    rows = 18,
                    maxLength = MARKDOWN_PREVIEW_MAX_CHARS,
                    description = "Submit to render a safe server-side preview; nothing is saved.",
                    validationMessage = errorMessage,
                    fieldModifier = Modifier()
                        .fontFamily(PortfolioTheme.Typography.FONT_MONO)
                        .lineHeight(1.55)
                        .minHeight(360.px)
                )
                FormButton(
                    text = "Render preview",
                    variant = FormButtonVariant.Primary,
                    fullWidth = false,
                    modifier = Modifier().alignSelf(AlignSelf.FlexStart)
                )
            }

            if (document != null) {
                Column(
                    modifier = Modifier()
                        .display(Display.Flex)
                        .flexDirection(FlexDirection.Column)
                        .gap(PortfolioTheme.Spacing.md)
                        .id("preview")
                ) {
                    Text(
                        text = "Rendered output",
                        modifier = Modifier().fontSize(1.35.rem).fontWeight(800)
                    )
                    Prose(document = document)
                }
            }
        }
    }
}

const val MARKDOWN_PREVIEW_MAX_CHARS: Int = 50_000
