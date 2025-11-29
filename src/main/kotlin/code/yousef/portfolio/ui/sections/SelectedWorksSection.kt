package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.i18n.strings.ProjectsStrings
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AspectRatioBox
import code.yousef.portfolio.ui.components.GlassPill
import code.yousef.portfolio.ui.components.SplitView
import code.yousef.portfolio.ui.components.WindowFrame
import code.yousef.portfolio.ui.foundation.ContentSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.flexWrap
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight

@Composable
fun SelectedWorksSection(
    projects: List<Project>,
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    ContentSection(modifier = modifier) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.xl)
        ) {
            // Section header
            Text(
                text = ProjectsStrings.selectedWorkTitle.resolve(locale),
                modifier = Modifier()
                    .fontSize(2.5.rem)
                    .fontWeight(700)
            )

            // Project list with alternating layout
            projects.forEachIndexed { index, project ->
                ProjectItem(
                    project = project,
                    locale = locale,
                    reverseOrder = index % 2 != 0
                )
            }
        }
    }
}

@Composable
private fun ProjectItem(
    project: Project,
    locale: PortfolioLocale,
    reverseOrder: Boolean
) {
    SplitView(
        locale = locale,
        reverseOrder = reverseOrder,
        left = {
            ProjectTextContent(project = project, locale = locale)
        },
        right = {
            WindowFrame {
                AspectRatioBox(ratio = 16.0 / 9.0) {
                    if (project.imageUrl != null) {
                        Image(
                            src = project.imageUrl,
                            alt = project.title.resolve(locale),
                            modifier = Modifier()
                                .width("100%")
                                .height("100%")
                                .objectFit("cover")
                        )
                    } else {
                        GradientPlaceholder(
                            label = project.layerLabel.resolve(locale)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ProjectTextContent(
    project: Project,
    locale: PortfolioLocale
) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
            .justifyContent(JustifyContent.Center)
            .height("100%")
    ) {
        // Category pill
        GlassPill(text = project.category.label.resolve(locale))

        // Project title
        Text(
            text = project.title.resolve(locale),
            modifier = Modifier()
                .fontSize(1.75.rem)
                .fontWeight(700)
                .lineHeight(1.3)
        )

        // Project description
        Text(
            text = project.description.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .lineHeight(1.8)
        )

        // Technologies
        if (project.technologies.isNotEmpty()) {
            Row(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexWrap(FlexWrap.Wrap)
                    .gap(PortfolioTheme.Spacing.xs)
            ) {
                project.technologies.forEach { tech ->
                    GlassPill(text = tech)
                }
            }
        }
    }
}

@Composable
private fun GradientPlaceholder(label: String) {
    Box(
        modifier = Modifier()
            .width("100%")
            .height("100%")
            .background(PortfolioTheme.Gradients.HERO)
            .display(Display.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
    ) {
        Text(
            text = label,
            modifier = Modifier()
                .fontSize(1.5.rem)
                .fontWeight(600)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
    }
}

