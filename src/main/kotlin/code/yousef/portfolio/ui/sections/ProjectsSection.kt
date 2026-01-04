package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.ProjectCategory
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.ssr.summonMarketingUrl
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.portfolio.ui.resolveSummonInline
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.RichText
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

@Composable
fun ProjectsSection(
    projects: List<Project>,
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    val sortedProjects = projects.sortedBy { it.order }

    ContentSection(modifier = modifier, surface = false) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            SectionHeading(locale = locale)
            CategoryLegend(locale = locale)
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                sortedProjects.forEach { project ->
                    ProjectCard(project = project, locale = locale)
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(locale: PortfolioLocale) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.xs)
    ) {
        Text(
            text = ProjectsCopy.projectsEyebrow.resolve(locale),
            modifier = Modifier()
                .letterSpacing(0.25.rem)
                .textTransform(TextTransform.Uppercase)
                .fontSize(0.75.rem)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        Text(
            text = ProjectsCopy.projectsTitle.resolve(locale),
            modifier = Modifier()
                .fontSize(2.5.rem)
                .fontWeight(700)
        )
        Text(
            text = ProjectsCopy.projectsDescription.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .lineHeight(1.8)
        )
    }
}

@Composable
private fun CategoryLegend(locale: PortfolioLocale) {
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .flexWrap(FlexWrap.Wrap)
            .gap(PortfolioTheme.Spacing.sm)
    ) {
        ProjectCategory.values().forEach { category ->
            Text(
                text = category.label.resolve(locale),
                modifier = Modifier()
                    .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.md)
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .fontSize(0.85.rem)
            )
        }
    }
}

private const val SUMMON_PROJECT_SLUG = "summon-framework"

@Composable
private fun ProjectCard(project: Project, locale: PortfolioLocale) {
    val projectTitle = project.title.resolve(locale)
    val description = project.description.resolveSummonInline(locale)
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.sm)
            .padding(PortfolioTheme.Spacing.lg)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .boxShadow(PortfolioTheme.Shadows.LOW)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .justifyContent(JustifyContent.SpaceBetween)
                .alignItems(AlignItems.Center)
        ) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
            ) {
                Text(
                    text = project.layerLabel.resolve(locale),
                    modifier = Modifier()
                        .fontSize(0.8.rem)
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .textTransform(TextTransform.Uppercase)
                )
                Text(
                    text = project.layerName.resolve(locale),
                    modifier = Modifier()
                        .fontSize(0.9.rem)
                        .fontWeight(600)
                )
            }
            Text(
                text = project.category.label.resolve(locale),
                modifier = Modifier()
                    .fontSize(0.85.rem)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }

        if (project.slug == SUMMON_PROJECT_SLUG) {
            AnchorLink(
                label = projectTitle,
                href = summonMarketingUrl(),
                modifier = Modifier()
                    .fontSize(2.rem)
                    .fontWeight(700)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .textDecoration(TextDecoration.None),
                navigationMode = LinkNavigationMode.Native,
                dataAttributes = mapOf("project-link" to "summon"),
                target = null,
                rel = null,
                title = null,
                id = null,
                ariaLabel = null,
                ariaDescribedBy = null,
                dataHref = null
            )
        } else {
            Text(
                text = projectTitle,
                modifier = Modifier()
                    .fontSize(2.rem)
                    .fontWeight(700)
            )
        }
        if (description.hasSummonLink) {
            RichText(
                "<p>${description.html}</p>",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.7)
            )
        } else {
            Text(
                text = description.value,
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.7)
            )
        }

        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .flexWrap(FlexWrap.Wrap)
                .alignItems(AlignItems.Center)
                .gap(PortfolioTheme.Spacing.sm)
        ) {
            if (project.technologies.isNotEmpty()) {
                project.technologies.forEach { tech ->
                    Text(
                        text = tech,
                        modifier = Modifier()
                            .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER)
                            .borderRadius(PortfolioTheme.Radii.pill)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .fontSize(0.75.rem)
                    )
                }
            }
            if (project.githubUrl != null) {
                AnchorLink(
                    label = "GitHub",
                    href = project.githubUrl,
                    modifier = Modifier()
                        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
                        .backgroundColor(PortfolioTheme.Colors.ACCENT)
                        .color("#ffffff")
                        .borderRadius(PortfolioTheme.Radii.pill)
                        .fontSize(0.75.rem)
                        .fontWeight(600)
                        .textDecoration(TextDecoration.None),
                    navigationMode = LinkNavigationMode.Native,
                    target = "_blank",
                    rel = "noopener noreferrer",
                    title = null,
                    id = null,
                    ariaLabel = "View ${project.title.en} on GitHub",
                    ariaDescribedBy = null,
                    dataHref = null,
                    dataAttributes = emptyMap()
                )
            }
        }
    }
}

private object ProjectsCopy {
    val projectsEyebrow = LocalizedText("Layered Journey", "رحلة متعددة الطبقات")
    val projectsTitle = LocalizedText("Language → Framework → Experience", "لغة → إطار → تجربة")
    val projectsDescription = LocalizedText(
        en = "Each layer builds on the previous one. Together they form a cohesive pipeline from low-level systems to expressive, client-facing experiences.",
        ar = "كل طبقة تبني على السابقة، لتشكّل معًا سلسلة مترابطة من الأنظمة منخفضة المستوى إلى التجارب المتفاعلة."
    )
}
