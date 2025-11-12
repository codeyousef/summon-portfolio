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
import code.yousef.summon.components.display.Text
import code.yousef.summon.components.forms.*
import code.yousef.summon.components.foundation.RawHtml
import code.yousef.summon.components.layout.Box
import code.yousef.summon.components.layout.Column
import code.yousef.summon.components.layout.Row
import code.yousef.summon.components.navigation.AnchorLink
import code.yousef.summon.components.navigation.LinkNavigationMode
import code.yousef.summon.extensions.px
import code.yousef.summon.extensions.rem
import code.yousef.summon.modifier.*
import code.yousef.summon.modifier.LayoutModifiers.gap
import code.yousef.summon.modifier.StylingModifiers.fontWeight
import code.yousef.summon.modifier.StylingModifiers.lineHeight
import code.yousef.summon.runtime.LocalPlatformRenderer
import code.yousef.summon.runtime.PlatformRenderer
import code.yousef.summon.runtime.setPlatformRenderer
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
                        AdminProjectForm(adminBasePath, null)
                        content.projects.sortedBy { it.order }.forEach { project ->
                            AdminProjectForm(adminBasePath, project)
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
                        AdminServiceForm(adminBasePath, null)
                        content.services.sortedBy { it.order }.forEach { service ->
                            AdminServiceForm(adminBasePath, service)
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
                        AdminBlogForm(adminBasePath, null)
                        content.blogPosts.sortedByDescending { it.publishedAt }.forEach { post ->
                            AdminBlogForm(adminBasePath, post)
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
                                    text = submission.name,
                                    modifier = Modifier().fontWeight(600)
                                )
                                listOfNotNull(
                                    submission.email?.let { "Email: $it" },
                                    submission.whatsapp?.let { "WhatsApp: $it" }
                                ).forEach { line ->
                                    Text(
                                        text = line,
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
                AppHeader(locale = locale)
                FormStyleSheet()
                AdminFormCss()
                activeContent()
            }
        }
    }
}

@Composable
private fun AdminCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
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

@Composable
private fun AdminProjectForm(basePath: String, project: Project?) {
    val isEditing = project != null
    val summary = if (isEditing) {
        "✏️ Edit ${project!!.summaryLabel("Project")}"
    } else {
        "➕ Create Project"
    }
    val options = ProjectCategory.entries.map { category ->
        FormSelectOption(
            value = category.name,
            label = category.name.lowercase().replaceFirstChar { it.uppercase() }
        )
    }

    AdminFormDisclosure(summary = summary, defaultOpen = !isEditing) {
        Form(
            action = adminAction(basePath, "projects/upsert"),
            hiddenFields = project?.id?.let { listOf(FormHiddenField("id", it)) } ?: emptyList()
        ) {
            FormTextField(
                name = "slug",
                label = "Slug",
                defaultValue = project?.slug.orEmpty(),
                required = true,
                helperText = "Used in URLs"
            )
            FormTextField(
                name = "layerLabel_en",
                label = "Layer Label (EN)",
                defaultValue = project?.layerLabel?.en.orEmpty(),
                required = true
            )
            FormTextField(
                name = "layerLabel_ar",
                label = "Layer Label (AR)",
                defaultValue = project?.layerLabel?.ar.orEmpty()
            )
            FormTextField(
                name = "layerName_en",
                label = "Layer Name (EN)",
                defaultValue = project?.layerName?.en.orEmpty(),
                required = true
            )
            FormTextField(
                name = "layerName_ar",
                label = "Layer Name (AR)",
                defaultValue = project?.layerName?.ar.orEmpty()
            )
            FormTextField(
                name = "title_en",
                label = "Title (EN)",
                defaultValue = project?.title?.en.orEmpty(),
                required = true
            )
            FormTextField(
                name = "title_ar",
                label = "Title (AR)",
                defaultValue = project?.title?.ar.orEmpty()
            )
            FormTextArea(
                name = "description_en",
                label = "Description (EN)",
                defaultValue = project?.description?.en.orEmpty(),
                required = true,
                minHeight = "140px"
            )
            FormTextArea(
                name = "description_ar",
                label = "Description (AR)",
                defaultValue = project?.description?.ar.orEmpty(),
                minHeight = "140px"
            )
            FormSelect(
                name = "category",
                label = "Category",
                options = options,
                selectedValue = project?.category?.name,
                fullWidth = true
            )
            FormTextField(
                name = "order",
                label = "Order",
                defaultValue = (project?.order ?: 0).toString(),
                type = FormTextFieldType.NUMBER,
                helperText = "Lower numbers appear first."
            )
            FormTextField(
                name = "technologies",
                label = "Technologies (comma separated)",
                defaultValue = project?.technologies?.joinToString(", ").orEmpty(),
                fullWidth = true
            )
            FormCheckbox(
                name = "featured",
                label = "Featured on landing page",
                checked = project?.featured == true,
                description = "Surface this project in the hero slider"
            )
            FormButton(
                text = if (isEditing) "Save Project" else "Create Project",
                tone = FormButtonTone.ACCENT,
                fullWidth = false
            )
        }
        project?.let {
            DeleteEntityForm(
                basePath = basePath,
                actionSuffix = "projects/delete",
                id = it.id,
                label = "Delete ${it.summaryLabel("Project")}"
            )
        }
    }
}

@Composable
private fun AdminServiceForm(basePath: String, service: Service?) {
    val isEditing = service != null
    val summary = if (isEditing) {
        "✏️ Edit ${service!!.summaryLabel("Service")}"
    } else {
        "➕ Create Service"
    }

    AdminFormDisclosure(summary = summary, defaultOpen = !isEditing) {
        Form(
            action = adminAction(basePath, "services/upsert"),
            hiddenFields = service?.id?.let { listOf(FormHiddenField("id", it)) } ?: emptyList()
        ) {
            FormTextField(
                name = "title_en",
                label = "Title (EN)",
                defaultValue = service?.title?.en.orEmpty(),
                required = true
            )
            FormTextField(
                name = "title_ar",
                label = "Title (AR)",
                defaultValue = service?.title?.ar.orEmpty()
            )
            FormTextArea(
                name = "description_en",
                label = "Description (EN)",
                defaultValue = service?.description?.en.orEmpty(),
                required = true,
                minHeight = "140px"
            )
            FormTextArea(
                name = "description_ar",
                label = "Description (AR)",
                defaultValue = service?.description?.ar.orEmpty(),
                minHeight = "140px"
            )
            FormTextField(
                name = "order",
                label = "Order",
                defaultValue = (service?.order ?: 0).toString(),
                type = FormTextFieldType.NUMBER
            )
            FormCheckbox(
                name = "featured",
                label = "Featured service",
                checked = service?.featured == true
            )
            FormButton(
                text = if (isEditing) "Save Service" else "Create Service",
                tone = FormButtonTone.ACCENT
            )
        }
        service?.let {
            DeleteEntityForm(
                basePath = basePath,
                actionSuffix = "services/delete",
                id = it.id,
                label = "Delete ${it.summaryLabel("Service")}"
            )
        }
    }
}

@Composable
private fun AdminBlogForm(basePath: String, post: BlogPost?) {
    val isEditing = post != null
    val summary = if (isEditing) {
        "✏️ Edit ${post!!.summaryLabel("Post")}"
    } else {
        "➕ Create Blog Post"
    }

    AdminFormDisclosure(summary = summary, defaultOpen = !isEditing) {
        Form(
            action = adminAction(basePath, "blog/upsert"),
            hiddenFields = post?.id?.let { listOf(FormHiddenField("id", it)) } ?: emptyList()
        ) {
            FormTextField(
                name = "slug",
                label = "Slug",
                defaultValue = post?.slug.orEmpty(),
                required = true,
                helperText = "Used in /blog/{slug}"
            )
            FormTextField(
                name = "title_en",
                label = "Title (EN)",
                defaultValue = post?.title?.en.orEmpty(),
                required = true
            )
            FormTextField(
                name = "title_ar",
                label = "Title (AR)",
                defaultValue = post?.title?.ar.orEmpty()
            )
            FormTextArea(
                name = "excerpt_en",
                label = "Excerpt (EN)",
                defaultValue = post?.excerpt?.en.orEmpty(),
                required = true,
                minHeight = "120px"
            )
            FormTextArea(
                name = "excerpt_ar",
                label = "Excerpt (AR)",
                defaultValue = post?.excerpt?.ar.orEmpty(),
                minHeight = "120px"
            )
            MarkdownEditorField(
                name = "content_en",
                label = "Content (EN)",
                defaultValue = post?.content?.en.orEmpty(),
                required = true
            )
            MarkdownEditorField(
                name = "content_ar",
                label = "Content (AR)",
                defaultValue = post?.content?.ar.orEmpty(),
                showPreview = false
            )
            FormTextField(
                name = "published_at",
                label = "Published Date",
                defaultValue = post?.publishedAt?.toString().orEmpty(),
                required = true,
                type = FormTextFieldType.DATE
            )
            FormTextField(
                name = "author",
                label = "Author",
                defaultValue = post?.author.orEmpty(),
                required = true
            )
            FormTextField(
                name = "tags",
                label = "Tags (comma separated)",
                defaultValue = post?.tags?.joinToString(", ").orEmpty(),
                fullWidth = true
            )
            FormCheckbox(
                name = "featured",
                label = "Featured post",
                checked = post?.featured == true
            )
            FormButton(
                text = if (isEditing) "Save Post" else "Publish Post",
                tone = FormButtonTone.ACCENT
            )
        }
        post?.let {
            DeleteEntityForm(
                basePath = basePath,
                actionSuffix = "blog/delete",
                id = it.id,
                label = "Delete ${it.summaryLabel("Post")}"
            )
        }
    }
}

@Composable
private fun DeleteEntityForm(
    basePath: String,
    actionSuffix: String,
    id: String,
    label: String
) {
    Form(
        action = adminAction(basePath, actionSuffix),
        hiddenFields = listOf(FormHiddenField("id", id))
    ) {
        FormButton(
            text = label,
            tone = FormButtonTone.DANGER
        )
    }
}

@Composable
private fun AdminFormDisclosure(
    summary: String,
    defaultOpen: Boolean,
    content: @Composable () -> Unit
) {
    val openAttr = if (defaultOpen) "open" else ""
    val innerHtml = renderFragmentHtml(content)
    RawHtml(
        """
        <details class="summon-admin-form" $openAttr>
          <summary>${summary.htmlEscape()}</summary>
          <div class="summon-admin-form-body">
            $innerHtml
          </div>
        </details>
        """.trimIndent(),
        sanitize = false
    )
}

@Composable
private fun AdminFormCss() {
    RawHtml(
        """
        <style>
  details.summon-admin-form {
    margin-top: 12px;
    border: 1px solid rgba(255,255,255,0.08);
    border-radius: 24px;
    background: rgba(12,14,20,0.65);
    backdrop-filter: blur(24px);
    overflow: hidden;
    box-shadow: 0 24px 70px rgba(0,0,0,0.45);
  }
  details.summon-admin-form summary {
    cursor: pointer;
    font-weight: 600;
    letter-spacing: 0.02em;
    padding: 18px 28px;
    list-style: none;
    outline: none;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  details.summon-admin-form summary::-webkit-details-marker {
    display: none;
  }
  details.summon-admin-form summary::after {
    content: "▾";
    font-size: 0.95rem;
    transition: transform 160ms ease;
    color: rgba(255,255,255,0.65);
  }
  details.summon-admin-form[open] summary::after {
    transform: rotate(180deg);
  }
  details.summon-admin-form[open] summary {
    border-bottom: 1px solid rgba(255,255,255,0.08);
  }
  .summon-admin-form-body {
    padding: 32px;
    display: flex;
    flex-direction: column;
    gap: 32px;
    background: linear-gradient(135deg, rgba(19,22,33,0.9), rgba(9,10,14,0.92));
    border-top: 1px solid rgba(255,255,255,0.05);
  }
        </style>
        """.trimIndent()
    )
}

private fun renderFragmentHtml(content: @Composable () -> Unit): String {
    val previousRenderer = runCatching { LocalPlatformRenderer.current }.getOrNull()
    val renderer = PlatformRenderer()
    val document = try {
        renderer.renderComposableRoot(content)
    } finally {
        previousRenderer?.let { setPlatformRenderer(it) }
    }
    val bodyStart = document.indexOf("<body")
    if (bodyStart == -1) return document
    val bodyOpenEnd = document.indexOf('>', bodyStart)
    if (bodyOpenEnd == -1) return document.substring(bodyStart)
    val bodyCloseStart = document.lastIndexOf("</body>")
    if (bodyCloseStart == -1 || bodyCloseStart <= bodyOpenEnd) {
        return document.substring(bodyOpenEnd + 1)
    }
    return document.substring(bodyOpenEnd + 1, bodyCloseStart)
}

private fun Project.summaryLabel(fallback: String): String =
    title.en.orFallback(slug).orFallback(fallback)

private fun Service.summaryLabel(fallback: String): String =
    title.en.orFallback(id).orFallback(fallback)

private fun BlogPost.summaryLabel(fallback: String): String =
    title.en.orFallback(slug).orFallback(fallback)

private fun String?.orFallback(fallback: String): String =
    if (this.isNullOrBlank()) fallback else this

private fun adminAction(basePath: String, suffix: String): String =
    "$basePath/$suffix"

private fun String?.htmlEscape(): String =
    this?.replace("&", "&amp;")
        ?.replace("<", "&lt;")
        ?.replace(">", "&gt;")
        ?.replace("\"", "&quot;")
        ?.replace("'", "&#39;")
        ?: ""
