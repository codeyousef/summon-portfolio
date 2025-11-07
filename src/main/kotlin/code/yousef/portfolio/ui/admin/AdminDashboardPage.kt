package code.yousef.portfolio.ui.admin

import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.LayoutModifiers.gridTemplateColumns
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class AdminDashboardContent(
    val projects: List<Project>,
    val services: List<Service>,
    val contacts: List<ContactSubmission>
)

@Composable
fun AdminDashboardPage(
    locale: PortfolioLocale,
    content: AdminDashboardContent
) {
    PageScaffold(locale = locale) {
        ContentSection(surface = false) {
            Text(
                text = "Admin Dashboard",
                modifier = Modifier()
                    .fontSize(2.25.rem)
                    .fontWeight(700)
            )
            Text(
                text = "Review projects, services, and inbound contact requests in one place.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.8)
            )
        }
        Row(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(320px, 1fr))")
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            AdminCard(
                title = "Projects (${content.projects.size})",
                description = "Sorted by layer stack"
            ) {
                content.projects.sortedBy { it.order }.take(6).forEach { project ->
                    Column(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.xs)
                            .padding(PortfolioTheme.Spacing.sm)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER)
                            .borderRadius(PortfolioTheme.Radii.md)
                    ) {
                        Text(
                            text = project.title.resolve(locale),
                            modifier = Modifier().fontWeight(700)
                        )
                        Text(
                            text = "${project.layerLabel.resolve(locale)} · ${project.category.label.resolve(locale)}",
                            modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        )
                    }
                }
            }
            AdminCard(
                title = "Services (${content.services.size})",
                description = "What users can book"
            ) {
                content.services.sortedBy { it.order }.forEach { service ->
                    Column(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.xs)
                            .padding(PortfolioTheme.Spacing.sm)
                    ) {
                        Text(
                            text = service.title.resolve(locale),
                            modifier = Modifier()
                                .fontWeight(600)
                        )
                        Text(
                            text = service.description.resolve(locale),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                .lineHeight(1.6)
                        )
                    }
                }
            }
            AdminCard(
                title = "Contacts (${content.contacts.size})",
                description = "Most recent submissions"
            ) {
                val dateFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
                content.contacts.sortedByDescending { it.createdAt }.take(8).forEach { submission ->
                    Column(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .gap(PortfolioTheme.Spacing.xs)
                            .padding(PortfolioTheme.Spacing.sm)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER)
                            .borderRadius(PortfolioTheme.Radii.md)
                    ) {
                        Text(
                            text = "${submission.name} · ${submission.whatsapp}",
                            modifier = Modifier().fontWeight(600)
                        )
                        submission.email?.let {
                            Text(
                                text = it,
                                modifier = Modifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)
                            )
                        }
                        Text(
                            text = submission.requirements,
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                .lineHeight(1.5)
                        )
                        Text(
                            text = dateFormatter.format(submission.createdAt),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                .fontSize(0.8.rem)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCard(
    title: String,
    description: String,
    content: () -> Unit
) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .gap(PortfolioTheme.Spacing.xs)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.lg)
    ) {
        Text(
            text = title,
            modifier = Modifier()
                .fontSize(1.25.rem)
                .fontWeight(600)
        )
        Text(
            text = description,
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontSize(0.9.rem)
        )
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.sm)
        ) {
            content()
        }
    }
}
