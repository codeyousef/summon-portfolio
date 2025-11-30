package code.yousef.portfolio.ui.admin

import code.yousef.portfolio.contact.ContactSubmission
import code.yousef.portfolio.content.model.BlogPost
import code.yousef.portfolio.content.model.Project
import code.yousef.portfolio.content.model.ProjectCategory
import code.yousef.portfolio.content.model.Service
import code.yousef.portfolio.content.model.Testimonial
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.foundation.PageScaffold
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.forms.*
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.components.styles.GlobalStyle
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight
import codes.yousef.summon.runtime.LocalPlatformRenderer
import codes.yousef.summon.runtime.PlatformRenderer
import codes.yousef.summon.runtime.setPlatformRenderer
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class AdminDashboardContent(
    val projects: List<Project>,
    val services: List<Service>,
    val blogPosts: List<BlogPost>,
    val testimonials: List<Testimonial>,
    val contacts: List<ContactSubmission>
)

enum class AdminSectionPage {
    PROJECTS, SERVICES, BLOG, TESTIMONIALS, CONTACTS;

    fun pathSegment(): String = name.lowercase()

    fun label(): String = when (this) {
        PROJECTS -> "Projects"
        SERVICES -> "Services"
        BLOG -> "Blog"
        TESTIMONIALS -> "Testimonials"
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

        AdminSectionPage.TESTIMONIALS -> {
            {
                AdminSection(id = "admin-testimonials") {
                    AdminCard(
                        title = "Testimonials (${content.testimonials.size})",
                        description = "Manage client testimonials displayed on the homepage."
                    ) {
                        AdminTestimonialForm(adminBasePath, null)
                        content.testimonials.sortedBy { it.order }.forEach { testimonial ->
                            AdminTestimonialForm(adminBasePath, testimonial)
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
                                    text = submission.contact,
                                    modifier = Modifier().fontWeight(600)
                                )
                                Text(
                                    text = submission.message,
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
        // Global styles for admin form labels and inputs
        GlobalStyle(
            """
            label {
                color: ${PortfolioTheme.Colors.TEXT_PRIMARY} !important;
            }
            input, textarea, select {
                padding: ${PortfolioTheme.Spacing.md} ${PortfolioTheme.Spacing.lg} !important;
                padding-right: 80px !important;
                color: #000000 !important;
            }
            form {
                padding-right: 64px !important;
            }
            """
        )
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.xl)
                .alignItems(AlignItems.Stretch)
                .margin(PortfolioTheme.Spacing.xxl, "0", "0", "0")
        ) {
            AdminSidebar(locale = locale, basePath = adminBasePath, activeSection = activeSection)
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flex(grow = 1, shrink = 1, basis = "auto")
                    .gap(PortfolioTheme.Spacing.lg)
            ) {
                AppHeader(locale = locale)
                // Form styles handled via modifiers
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
            .height(100.percent)
            .minHeight("calc(100vh - 160px)")
            .alignSelf(AlignSelf.Stretch)
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
                        .textAlign(TextAlign.Center)
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

    AdminFormDisclosure(summary = summary, defaultOpen = !isEditing, id = "project-${project?.id ?: "new"}") {
        Form(
            action = adminAction(basePath, "projects/upsert"),
            hiddenFields = project?.id?.let { listOf(FormHiddenField("id", it)) } ?: emptyList()
        ) {
            FormTextField(
                name = "slug",
                label = "Slug",
                defaultValue = project?.slug.orEmpty(),
                required = true
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
                required = true
            )
            FormTextArea(
                name = "description_ar",
                label = "Description (AR)",
                defaultValue = project?.description?.ar.orEmpty()
            )
            FormSelect(
                name = "category",
                label = "Category",
                options = options,
                selectedValue = project?.category?.name
            )
            FormTextField(
                name = "order",
                label = "Order",
                defaultValue = (project?.order ?: 0).toString()
            )
            FormTextField(
                name = "technologies",
                label = "Technologies (comma separated)",
                defaultValue = project?.technologies?.joinToString(", ").orEmpty()
            )
            FormCheckbox(
                name = "featured",
                label = "Featured on landing page",
                checked = project?.featured == true,
                description = "Surface this project in the hero slider"
            )
            FormButton(
                text = if (isEditing) "Save Project" else "Create Project"
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

    AdminFormDisclosure(summary = summary, defaultOpen = !isEditing, id = "service-${service?.id ?: "new"}") {
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
                required = true
            )
            FormTextArea(
                name = "description_ar",
                label = "Description (AR)",
                defaultValue = service?.description?.ar.orEmpty()
            )
            FormTextField(
                name = "order",
                label = "Order",
                defaultValue = (service?.order ?: 0).toString()
            )
            FormCheckbox(
                name = "featured",
                label = "Featured service",
                checked = service?.featured == true
            )
            FormButton(
                text = if (isEditing) "Save Service" else "Create Service"
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

    AdminFormDisclosure(summary = summary, defaultOpen = !isEditing, id = "blog-${post?.id ?: "new"}") {
        Form(
            action = adminAction(basePath, "blog/upsert"),
            hiddenFields = post?.id?.let { listOf(FormHiddenField("id", it)) } ?: emptyList()
        ) {
            FormTextField(
                name = "slug",
                label = "Slug",
                defaultValue = post?.slug.orEmpty(),
                required = true
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
                required = true
            )
            FormTextArea(
                name = "excerpt_ar",
                label = "Excerpt (AR)",
                defaultValue = post?.excerpt?.ar.orEmpty()
            )
            FormTextArea(
                name = "content_en",
                label = "Content (EN)",
                defaultValue = post?.content?.en.orEmpty(),
                required = true
            )
            FormTextArea(
                name = "content_ar",
                label = "Content (AR)",
                defaultValue = post?.content?.ar.orEmpty(),
                required = false
            )
            FormTextField(
                name = "published_at",
                label = "Published Date",
                defaultValue = post?.publishedAt?.toString().orEmpty(),
                required = true
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
                defaultValue = post?.tags?.joinToString(", ").orEmpty()
            )
            FormCheckbox(
                name = "featured",
                label = "Featured post",
                checked = post?.featured == true
            )
            FormButton(
                text = if (isEditing) "Save Post" else "Publish Post"
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
private fun AdminTestimonialForm(basePath: String, testimonial: Testimonial?) {
    val isEditing = testimonial != null
    val summary = if (isEditing) {
        "✏️ Edit ${testimonial!!.summaryLabel("Testimonial")}"
    } else {
        "➕ Create Testimonial"
    }

    AdminFormDisclosure(summary = summary, defaultOpen = !isEditing, id = "testimonial-${testimonial?.id ?: "new"}") {
        Form(
            action = adminAction(basePath, "testimonials/upsert"),
            hiddenFields = testimonial?.id?.let { listOf(FormHiddenField("id", it)) } ?: emptyList()
        ) {
            FormTextArea(
                name = "quote_en",
                label = "Quote (EN)",
                defaultValue = testimonial?.quote?.en.orEmpty(),
                required = true
            )
            FormTextArea(
                name = "quote_ar",
                label = "Quote (AR)",
                defaultValue = testimonial?.quote?.ar.orEmpty()
            )
            FormTextField(
                name = "author",
                label = "Author Name",
                defaultValue = testimonial?.author.orEmpty(),
                required = true
            )
            FormTextField(
                name = "role_en",
                label = "Role (EN)",
                defaultValue = testimonial?.role?.en.orEmpty(),
                required = true
            )
            FormTextField(
                name = "role_ar",
                label = "Role (AR)",
                defaultValue = testimonial?.role?.ar.orEmpty()
            )
            FormTextField(
                name = "company_en",
                label = "Company (EN)",
                defaultValue = testimonial?.company?.en.orEmpty(),
                required = true
            )
            FormTextField(
                name = "company_ar",
                label = "Company (AR)",
                defaultValue = testimonial?.company?.ar.orEmpty()
            )
            FormTextField(
                name = "order",
                label = "Order",
                defaultValue = (testimonial?.order ?: 0).toString()
            )
            FormCheckbox(
                name = "featured",
                label = "Featured testimonial",
                checked = testimonial?.featured == true
            )
            FormButton(
                text = if (isEditing) "Save Testimonial" else "Create Testimonial"
            )
        }
        testimonial?.let {
            DeleteEntityForm(
                basePath = basePath,
                actionSuffix = "testimonials/delete",
                id = it.id,
                label = "Delete ${it.summaryLabel("Testimonial")}"
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
            text = label
        )
    }
}

@Composable
private fun AdminFormDisclosure(
    summary: String,
    defaultOpen: Boolean,
    id: String,
    content: @Composable () -> Unit
) {
    val contentId = "disclosure-content-$id"

    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
            .background(PortfolioTheme.Gradients.GLASS)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .padding(PortfolioTheme.Spacing.md)
                .borderBottomWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
        ) {
            Text(
                text = summary,
                modifier = Modifier()
                    .fontWeight(600)
                    .letterSpacing("0.02em")
            )
            // Use Box with data-action for immediate toggle without hydration
            Box(
                modifier = Modifier()
                    .cursor(Cursor.Pointer)
                    .padding(PortfolioTheme.Spacing.sm)
                    .fontSize(1.5.rem)
                    .fontWeight(600)
                    .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    .dataAttribute("action", """{"type":"toggle","targetId":"$contentId"}""")
            ) {
                Text(if (defaultOpen) "−" else "+")
            }
        }
        // Content with ID for toggle targeting
        Column(
            modifier = Modifier()
                .id(contentId)
                .display(if (defaultOpen) Display.Flex else Display.None)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
                .padding(PortfolioTheme.Spacing.lg)
                .paddingRight("80px")
        ) { content() }
    }
}

@Composable
private fun AdminFormCss() { /* removed */
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

private fun Testimonial.summaryLabel(fallback: String): String =
    author.orFallback(id).orFallback(fallback)

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
