package code.yousef.portfolio.ui.admin

import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.ProjectCategory
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.summon.annotation.Composable
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.extensions.px
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
    val blogPosts: List<BlogPost>,
    val contacts: List<ContactSubmission>
)

enum class AdminSectionPage {
    PROJECTS, SERVICES, BLOG, CONTACTS;

    fun pathSegment(): String = name.lowercase()

    fun label(): String = when (this) {
        PROJECTS -> "Projects"
        SERVICES -> "Services"
        BLOG -> "Blog"
        CONTACTS -> "Contacts"
    }
}

@Composable
fun AdminDashboardPage(
    locale: PortfolioLocale,
    content: AdminDashboardContent,
    activeSection: AdminSectionPage
) {
    val adminBasePath = if (locale == PortfolioLocale.EN) "/admin" else "/${locale.code}/admin"

    val activeContent: @Composable () -> Unit = when (activeSection) {
        AdminSectionPage.PROJECTS -> {
            {
                AdminSection(id = "admin-projects") {
                    AdminCard(
                        title = "Projects (${content.projects.size})",
                        description = "Edit layer metadata, ordering, and featured state."
                    ) {
                        RawHtml(projectFormHtml(adminBasePath, null))
                        content.projects.sortedBy { it.order }.forEach { project ->
                            RawHtml(projectFormHtml(adminBasePath, project))
                        }
                    }
                }
            }
        }
        AdminSectionPage.SERVICES -> {
            {
                AdminSection(id = "admin-services") {
                    AdminCard(
                        title = "Services (${content.services.size})",
                        description = "Create or edit published service offerings."
                    ) {
                        RawHtml(serviceFormHtml(adminBasePath, null))
                        content.services.sortedBy { it.order }.forEach { service ->
                            RawHtml(serviceFormHtml(adminBasePath, service))
                        }
                    }
                }
            }
        }
        AdminSectionPage.BLOG -> {
            {
                AdminSection(id = "admin-blog") {
                    AdminCard(
                        title = "Blog Posts (${content.blogPosts.size})",
                        description = "Publish long-form thoughts and release notes."
                    ) {
                        RawHtml(blogFormHtml(adminBasePath, null))
                        content.blogPosts.sortedByDescending { it.publishedAt }.forEach { post ->
                            RawHtml(blogFormHtml(adminBasePath, post))
                        }
                    }
                }
            }
        }
        AdminSectionPage.CONTACTS -> {
            {
                AdminSection(id = "admin-contacts") {
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
    }

    PageScaffold(locale = locale) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.xl)
                .alignItems(AlignItems.Stretch)
        ) {
            AdminSidebar(locale = locale, basePath = adminBasePath, activeSection = activeSection)
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flex(grow = 1, shrink = 1, basis = "auto")
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                AppHeader(locale = locale, onRequestServices = {})
                RawHtml(adminFormStyles())
                activeContent()
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

@Composable
private fun AdminSidebar(
    locale: PortfolioLocale,
    basePath: String,
    activeSection: AdminSectionPage
) {
    val navItems = AdminSectionPage.entries
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
            .width(260.px)
            .padding(PortfolioTheme.Spacing.lg)
            .background(PortfolioTheme.Gradients.GLASS)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .style("height", "100%")
            .style("min-height", "calc(100vh - 160px)")
            .style("align-self", "stretch")
    ) {
        navItems.forEach { section ->
            val isActive = section == activeSection
            val target = "${basePath}/${section.pathSegment()}"
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(PortfolioTheme.Spacing.xs)
            ) {
                AnchorLink(
                    label = section.label(),
                    href = target,
                    modifier = Modifier()
                        .textDecoration("none")
                        .style("text-align", "center")
                        .color(
                            if (isActive) PortfolioTheme.Colors.TEXT_PRIMARY else PortfolioTheme.Colors.TEXT_SECONDARY
                        )
                        .fontWeight(600),
                    navigationMode = LinkNavigationMode.Native
                )
                Box(
                    modifier = Modifier()
                        .width(120.px)
                        .height(1.px)
                        .margin(PortfolioTheme.Spacing.xs, "auto", PortfolioTheme.Spacing.sm, "auto")
                        .backgroundColor("#ffffff22")
                ) {}
            }
        }
    }
}

@Composable
private fun AdminSection(id: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier()
            .id(id)
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
    ) {
        content()
    }
}

private fun adminFormStyles(): String = """
    <style>
      .admin-form {
        margin-top: 12px;
        border: 1px solid rgba(255,255,255,0.08);
        border-radius: 14px;
        padding: 12px 14px;
        background: rgba(255,255,255,0.02);
      }
      .admin-form summary {
        cursor: pointer;
        font-weight: 600;
        letter-spacing: 0.02em;
      }
      .admin-form form {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-top: 12px;
      }
      .admin-form label {
        display: flex;
        flex-direction: column;
        gap: 4px;
        font-size: 0.85rem;
      }
      .admin-form input[type="text"],
      .admin-form input[type="number"],
      .admin-form input[type="date"],
      .admin-form select,
      .admin-form textarea {
        padding: 8px 10px;
        border-radius: 8px;
        border: 1px solid rgba(255,255,255,0.1);
        background: rgba(9,9,12,0.4);
        color: #f3f3ff;
        font-family: inherit;
      }
      .admin-form textarea {
        min-height: 80px;
      }
      .admin-form button {
        margin-top: 6px;
        border: none;
        border-radius: 8px;
        padding: 10px 12px;
        background: linear-gradient(120deg,#b01235,#ff3b6a);
        color: white;
        font-weight: 600;
        cursor: pointer;
      }
      .admin-form .danger {
        background: transparent;
        color: #ff8080;
        border: 1px solid rgba(255,128,128,0.4);
      }
    </style>
""".trimIndent()

private fun projectFormHtml(basePath: String, project: Project?): String {
    val action = adminAction(basePath, "projects/upsert")
    val deleteAction = adminAction(basePath, "projects/delete")
    val isEditing = project != null
    val summary = if (isEditing) {
        "✏️ Edit ${project!!.title.en.htmlEscape()}"
    } else {
        "➕ Create Project"
    }
    val featuredChecked = if (project?.featured == true) "checked" else ""
    val technologies = project?.technologies?.joinToString(", ")?.htmlEscape().orEmpty()
    val categoryOptions = ProjectCategory.entries.joinToString("") { category ->
        val selected = if (category == project?.category) "selected" else ""
        """<option value="${category.name}" $selected>${category.name.lowercase().replaceFirstChar { it.uppercase() }}</option>"""
    }
    val deleteHtml = if (isEditing) {
        deleteFormHtml(deleteAction, project!!.id, "Delete ${project.title.en.htmlEscape()}")
    } else ""
    val openAttr = if (isEditing) "" else "open"
    return """
        <details class="admin-form" $openAttr>
          <summary>$summary</summary>
          <form method="post" action="$action">
            <input type="hidden" name="id" value="${project?.id.htmlEscape()}">
            <label>Slug
              <input type="text" name="slug" value="${project?.slug.htmlEscape()}" required>
            </label>
            <label>Layer Label (EN)
              <input type="text" name="layerLabel_en" value="${project?.layerLabel?.en.htmlEscape()}" required>
            </label>
            <label>Layer Label (AR)
              <input type="text" name="layerLabel_ar" value="${project?.layerLabel?.ar.htmlEscape()}">
            </label>
            <label>Layer Name (EN)
              <input type="text" name="layerName_en" value="${project?.layerName?.en.htmlEscape()}" required>
            </label>
            <label>Layer Name (AR)
              <input type="text" name="layerName_ar" value="${project?.layerName?.ar.htmlEscape()}">
            </label>
            <label>Title (EN)
              <input type="text" name="title_en" value="${project?.title?.en.htmlEscape()}" required>
            </label>
            <label>Title (AR)
              <input type="text" name="title_ar" value="${project?.title?.ar.htmlEscape()}">
            </label>
            <label>Description (EN)
              <textarea name="description_en" required>${project?.description?.en.htmlEscape()}</textarea>
            </label>
            <label>Description (AR)
              <textarea name="description_ar">${project?.description?.ar.htmlEscape()}</textarea>
            </label>
            <label>Category
              <select name="category">
                $categoryOptions
              </select>
            </label>
            <label>Order
              <input type="number" name="order" value="${project?.order ?: 0}">
            </label>
            <label>Technologies (comma separated)
              <input type="text" name="technologies" value="$technologies">
            </label>
            <label>
              <span style="display:flex;align-items:center;gap:8px">
                <input type="checkbox" name="featured" $featuredChecked>
                <span>Featured on landing page</span>
              </span>
            </label>
            <button type="submit">${if (isEditing) "Save Project" else "Create Project"}</button>
          </form>
          $deleteHtml
        </details>
    """.trimIndent()
}

private fun serviceFormHtml(basePath: String, service: Service?): String {
    val action = adminAction(basePath, "services/upsert")
    val deleteAction = adminAction(basePath, "services/delete")
    val isEditing = service != null
    val summary = if (isEditing) {
        "✏️ Edit ${service!!.title.en.htmlEscape()}"
    } else {
        "➕ Create Service"
    }
    val featuredChecked = if (service?.featured == true) "checked" else ""
    val deleteHtml = if (isEditing) {
        deleteFormHtml(deleteAction, service!!.id, "Delete ${service.title.en.htmlEscape()}")
    } else ""
    val openAttr = if (isEditing) "" else "open"
    return """
        <details class="admin-form" $openAttr>
          <summary>$summary</summary>
          <form method="post" action="$action">
            <input type="hidden" name="id" value="${service?.id.htmlEscape()}">
            <label>Title (EN)
              <input type="text" name="title_en" value="${service?.title?.en.htmlEscape()}" required>
            </label>
            <label>Title (AR)
              <input type="text" name="title_ar" value="${service?.title?.ar.htmlEscape()}">
            </label>
            <label>Description (EN)
              <textarea name="description_en" required>${service?.description?.en.htmlEscape()}</textarea>
            </label>
            <label>Description (AR)
              <textarea name="description_ar">${service?.description?.ar.htmlEscape()}</textarea>
            </label>
            <label>Order
              <input type="number" name="order" value="${service?.order ?: 0}">
            </label>
            <label>
              <span style="display:flex;align-items:center;gap:8px"><input type="checkbox" name="featured" $featuredChecked><span>Featured service</span></span>
            </label>
            <button type="submit">${if (isEditing) "Save Service" else "Create Service"}</button>
          </form>
          $deleteHtml
        </details>
    """.trimIndent()
}

private fun blogFormHtml(basePath: String, post: BlogPost?): String {
    val action = adminAction(basePath, "blog/upsert")
    val deleteAction = adminAction(basePath, "blog/delete")
    val isEditing = post != null
    val summary = if (isEditing) {
        "✏️ Edit ${post!!.title.en.htmlEscape()}"
    } else {
        "➕ Create Blog Post"
    }
    val featuredChecked = if (post?.featured == true) "checked" else ""
    val deleteHtml = if (isEditing) {
        deleteFormHtml(deleteAction, post!!.id, "Delete ${post.title.en.htmlEscape()}")
    } else ""
    val openAttr = if (isEditing) "" else "open"
    return """
        <details class="admin-form" $openAttr>
          <summary>$summary</summary>
          <form method="post" action="$action">
            <input type="hidden" name="id" value="${post?.id.htmlEscape()}">
            <label>Slug
              <input type="text" name="slug" value="${post?.slug.htmlEscape()}" required>
            </label>
            <label>Title (EN)
              <input type="text" name="title_en" value="${post?.title?.en.htmlEscape()}" required>
            </label>
            <label>Title (AR)
              <input type="text" name="title_ar" value="${post?.title?.ar.htmlEscape()}">
            </label>
            <label>Excerpt (EN)
              <textarea name="excerpt_en" required>${post?.excerpt?.en.htmlEscape()}</textarea>
            </label>
            <label>Excerpt (AR)
              <textarea name="excerpt_ar">${post?.excerpt?.ar.htmlEscape()}</textarea>
            </label>
            <label>Content (EN)
              <textarea name="content_en" required>${post?.content?.en.htmlEscape()}</textarea>
            </label>
            <label>Content (AR)
              <textarea name="content_ar">${post?.content?.ar.htmlEscape()}</textarea>
            </label>
            <label>Published Date
              <input type="date" name="published_at" value="${post?.publishedAt?.toString().htmlEscape()}" required>
            </label>
            <label>Author
              <input type="text" name="author" value="${post?.author.htmlEscape()}" required>
            </label>
            <label>Tags (comma separated)
              <input type="text" name="tags" value="${post?.tags?.joinToString(", ")?.htmlEscape().orEmpty()}">
            </label>
            <label>
              <span style="display:flex;align-items:center;gap:8px"><input type="checkbox" name="featured" $featuredChecked><span>Featured post</span></span>
            </label>
            <button type="submit">${if (isEditing) "Save Post" else "Publish Post"}</button>
          </form>
          $deleteHtml
        </details>
    """.trimIndent()
}

private fun deleteFormHtml(action: String, id: String, label: String): String =
    """
        <form method="post" action="$action">
          <input type="hidden" name="id" value="${id.htmlEscape()}">
          <button type="submit" class="danger">$label</button>
        </form>
    """.trimIndent()

private fun adminAction(basePath: String, suffix: String): String =
    "$basePath/$suffix"

private fun String?.htmlEscape(): String =
    this?.replace("&", "&amp;")
        ?.replace("<", "&lt;")
        ?.replace(">", "&gt;")
        ?.replace("\"", "&quot;")
        ?.replace("'", "&#39;")
        ?: ""
