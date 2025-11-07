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
import code.yousef.summon.modifier.*
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
                    .style("font-size", "2.25rem")
                    .style("font-weight", "700")
            )
            Text(
                text = "Review projects, services, and inbound contact requests in one place.",
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .style("line-height", "1.8")
            )
        }
        Row(
            modifier = Modifier()
                .style("display", "grid")
                .style("grid-template-columns", "repeat(auto-fit, minmax(320px, 1fr))")
                .style("gap", PortfolioTheme.Spacing.lg)
        ) {
            AdminCard(
                title = "Projects (${content.projects.size})",
                description = "Sorted by layer stack"
            ) {
                content.projects.sortedBy { it.order }.take(6).forEach { project ->
                    Column(
                        modifier = Modifier()
                            .style("display", "flex")
                            .style("gap", PortfolioTheme.Spacing.xs)
                            .padding(PortfolioTheme.Spacing.sm)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER)
                            .borderRadius(PortfolioTheme.Radii.md)
                    ) {
                        Text(
                            text = project.title.resolve(locale),
                            modifier = Modifier()
                                .style("font-weight", "700")
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
                            .style("display", "flex")
                            .style("gap", PortfolioTheme.Spacing.xs)
                            .padding(PortfolioTheme.Spacing.sm)
                    ) {
                        Text(
                            text = service.title.resolve(locale),
                            modifier = Modifier()
                                .style("font-weight", "600")
                        )
                        Text(
                            text = service.description.resolve(locale),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                .style("line-height", "1.6")
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
                            .style("display", "flex")
                            .style("gap", PortfolioTheme.Spacing.xs)
                            .padding(PortfolioTheme.Spacing.sm)
                            .borderWidth(1)
                            .borderStyle(BorderStyle.Solid)
                            .borderColor(PortfolioTheme.Colors.BORDER)
                            .borderRadius(PortfolioTheme.Radii.md)
                    ) {
                        Text(
                            text = "${submission.name} · ${submission.whatsapp}",
                            modifier = Modifier().style("font-weight", "600")
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
                                .style("line-height", "1.5")
                        )
                        Text(
                            text = dateFormatter.format(submission.createdAt),
                            modifier = Modifier()
                                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                                .style("font-size", "0.8rem")
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
            .style("display", "flex")
            .style("gap", PortfolioTheme.Spacing.sm)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .style("padding", PortfolioTheme.Spacing.lg)
    ) {
        Text(
            text = title,
            modifier = Modifier()
                .style("font-size", "1.25rem")
                .style("font-weight", "600")
        )
        Text(
            text = description,
            modifier = Modifier()
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .style("font-size", "0.9rem")
        )
        Column(
            modifier = Modifier()
                .style("display", "flex")
                .style("gap", PortfolioTheme.Spacing.sm)
        ) {
            content()
        }
    }
}
