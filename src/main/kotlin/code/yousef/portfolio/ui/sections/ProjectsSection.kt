package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.ProjectCategory
import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.modifier.*

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
                .style("display", "flex")
                .style("flex-direction", "column")
                .style("gap", PortfolioTheme.Spacing.lg)
        ) {
            SectionHeading(locale = locale)
            CategoryLegend(locale = locale)
            Column(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("flex-direction", "column")
                    .style("gap", PortfolioTheme.Spacing.lg)
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
            .style("display", "flex")
            .style("flex-direction", "column")
            .style("gap", PortfolioTheme.Spacing.xs)
    ) {
        Text(
            text = ProjectsCopy.projectsEyebrow.resolve(locale),
            modifier = Modifier()
                .style("letter-spacing", "0.25em")
                .style("font-size", "0.75rem")
                .style("text-transform", "uppercase")
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        )
        Text(
            text = ProjectsCopy.projectsTitle.resolve(locale),
            modifier = Modifier()
                .style("font-size", "2.5rem")
                .style("font-weight", "700")
        )
        Text(
            text = ProjectsCopy.projectsDescription.resolve(locale),
            modifier = Modifier()
                .style("color", PortfolioTheme.Colors.TEXT_SECONDARY)
                .style("line-height", "1.8")
        )
    }
}

@Composable
private fun CategoryLegend(locale: PortfolioLocale) {
    Row(
        modifier = Modifier()
            .style("display", "flex")
            .style("flex-wrap", "wrap")
            .style("gap", PortfolioTheme.Spacing.sm)
    ) {
        ProjectCategory.values().forEach { category ->
            Text(
                text = category.label.resolve(locale),
                modifier = Modifier()
                    .style("padding", "${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.md}")
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(PortfolioTheme.Colors.BORDER)
                    .borderRadius(PortfolioTheme.Radii.pill)
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .style("font-size", "0.85rem")
            )
        }
    }
}

@Composable
private fun ProjectCard(project: Project, locale: PortfolioLocale) {
    Column(
        modifier = Modifier()
            .style("display", "flex")
            .style("flex-direction", "column")
            .style("gap", PortfolioTheme.Spacing.sm)
            .style("padding", PortfolioTheme.Spacing.lg)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .style("box-shadow", PortfolioTheme.Shadows.LOW)
    ) {
        Row(
            modifier = Modifier()
                .style("display", "flex")
                .style("justify-content", "space-between")
                .style("align-items", "center")
        ) {
            Column(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("flex-direction", "column")
            ) {
                Text(
                    text = project.layerLabel.resolve(locale),
                    modifier = Modifier()
                        .style("font-size", "0.8rem")
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .style("text-transform", "uppercase")
                )
                Text(
                    text = project.layerName.resolve(locale),
                    modifier = Modifier()
                        .style("font-size", "0.9rem")
                        .style("font-weight", "600")
                )
            }
            Text(
                text = project.category.label.resolve(locale),
                modifier = Modifier()
                    .style("font-size", "0.85rem")
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
            )
        }

        Text(
            text = project.title.resolve(locale),
            modifier = Modifier()
                .style("font-size", "2rem")
                .style("font-weight", "700")
        )
        Text(
            text = project.description.resolve(locale),
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .style("line-height", "1.7")
        )

        if (project.technologies.isNotEmpty()) {
            Row(
                modifier = Modifier()
                    .style("display", "flex")
                    .style("flex-wrap", "wrap")
                    .style("gap", PortfolioTheme.Spacing.xs)
            ) {
                project.technologies.forEach { tech ->
                    Text(
                        text = tech,
                        modifier = Modifier()
                            .style("padding", "${PortfolioTheme.Spacing.xs} ${PortfolioTheme.Spacing.sm}")
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER)
                            .borderRadius(PortfolioTheme.Radii.pill)
                            .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            .style("font-size", "0.75rem")
                    )
                }
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
