package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.docs.DocsNavNode
import code.yousef.portfolio.docs.DocsNavTree
import code.yousef.portfolio.docs.summon.safeHref
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.foundation.RawHtml
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.vh
import codes.yousef.summon.modifier.*

@Composable
fun DocsSidebar(tree: DocsNavTree, currentPath: String, basePath: String = "") {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(codes.yousef.summon.modifier.FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.sm)
            .flex(grow = 0, shrink = 0, basis = "200px")
            .width(200.px)
            .padding(PortfolioTheme.Spacing.md)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .position(Position.Sticky)
            .top(PortfolioTheme.Spacing.lg)
            .maxHeight(80.vh)
            .overflowY(Overflow.Auto)
            // Hide on mobile, show on desktop
            .display(Display.None)
            .mediaQuery(MediaQuery.MinWidth(900)) {
                display(Display.Flex)
            }
    ) {
        tree.sections.forEach { section ->
            Column(
                modifier = Modifier()
                    .gap(PortfolioTheme.Spacing.xs)
            ) {
                // Top-level section title (e.g., "Documentation", "API Reference")
                Text(
                    text = section.title,
                    modifier = Modifier()
                        .fontWeight(600)
                        .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                        .marginBottom(PortfolioTheme.Spacing.xs)
                )
                
                // Render children (which may be direct links or folder groups)
                section.children.forEach { node ->
                    if (node.children.isEmpty()) {
                        // Direct link (no nested children)
                        val active = normalize(currentPath) == normalize(node.path)
                        DocsSidebarLink(
                            label = node.title,
                            href = basePath + node.path,
                            active = active,
                            dataLabel = node.title.lowercase()
                        )
                    } else {
                        // Folder group with children
                        DocsSidebarGroup(
                            node = node,
                            currentPath = currentPath,
                            basePath = basePath
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocsSidebarGroup(
    node: DocsNavNode,
    currentPath: String,
    basePath: String
) {
    Column(
        modifier = Modifier()
            .gap(PortfolioTheme.Spacing.xxs)
            .marginTop(PortfolioTheme.Spacing.sm)
    ) {
        // Folder heading
        Text(
            text = node.title,
            modifier = Modifier()
                .fontWeight(500)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                .fontSize(13.px)
                .textTransform(TextTransform.Uppercase)
                .letterSpacing(0.5.px)
                .marginBottom(PortfolioTheme.Spacing.xxs)
        )
        
        // Children (indented) — recurse if child has its own children
        Column(
            modifier = Modifier()
                .paddingLeft(PortfolioTheme.Spacing.sm)
                .gap(PortfolioTheme.Spacing.xxs)
        ) {
            node.children.forEach { child ->
                if (child.children.isEmpty()) {
                    val active = normalize(currentPath) == normalize(child.path)
                    DocsSidebarLink(
                        label = child.title,
                        href = basePath + child.path,
                        active = active,
                        dataLabel = child.title.lowercase()
                    )
                } else {
                    DocsSidebarGroup(
                        node = child,
                        currentPath = currentPath,
                        basePath = basePath
                    )
                }
            }
        }
    }
}

private fun normalize(path: String): String = if (path.isBlank()) "/" else path.trimEnd('/')

@Composable
private fun DocsSidebarLink(
    label: String,
    href: String,
    active: Boolean,
    dataLabel: String
) {
    val baseModifier = Modifier()
        .display(Display.Block)
        .padding(PortfolioTheme.Spacing.xs, PortfolioTheme.Spacing.sm)
        .borderRadius(PortfolioTheme.Radii.md)
        .color(
            if (active) PortfolioTheme.Colors.ACCENT_ALT else PortfolioTheme.Colors.TEXT_PRIMARY
        )
        .backgroundColor(
            if (active) PortfolioTheme.Colors.SURFACE_STRONG else "transparent"
        )
    AnchorLink(
        label = label,
        href = safeHref(href),
        modifier = baseModifier,
        navigationMode = LinkNavigationMode.Native,
        dataAttributes = mapOf("docs-nav" to dataLabel),
        target = null,
        rel = null,
        title = null,
        id = null,
        ariaLabel = null,
        ariaDescribedBy = null,
        dataHref = null
    )
}

/**
 * Mobile-friendly collapsible sidebar for documentation navigation.
 * Uses HTML details/summary for native accordion behavior without JS.
 */
@Composable
fun MobileDocsSidebar(tree: DocsNavTree, currentPath: String, basePath: String = "") {
    Column(
        modifier = Modifier()
            .width("100%")
            .marginBottom(PortfolioTheme.Spacing.md)
            // Show only on mobile, hide on desktop
            .display(Display.Block)
            .mediaQuery(MediaQuery.MinWidth(900)) {
                display(Display.None)
            }
    ) {
        // Using details/summary for native collapsible behavior
        Box(
            modifier = Modifier()
                .backgroundColor(PortfolioTheme.Colors.SURFACE)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
                .borderRadius(PortfolioTheme.Radii.lg)
                .padding(PortfolioTheme.Spacing.md)
        ) {
            RawHtml(
                html = buildMobileNavHtml(tree, currentPath, basePath)
            )
        }
    }
}

private fun buildMobileNavHtml(tree: DocsNavTree, currentPath: String, basePath: String): String {
    val sb = StringBuilder()
    sb.append("""<details class="mobile-docs-nav">""")
    sb.append("""<summary style="cursor: pointer; font-weight: 600; padding: 8px 0; display: flex; align-items: center; gap: 8px;">""")
    sb.append("""<span style="font-size: 20px;">☰</span> Navigation</summary>""")
    sb.append("""<nav style="display: flex; flex-direction: column; gap: 8px; padding-top: 12px;">""")

    tree.sections.forEach { section ->
        sb.append("""<div style="margin-bottom: 12px;">""")
        sb.append("""<div style="font-weight: 600; color: ${PortfolioTheme.Colors.TEXT_SECONDARY}; margin-bottom: 8px;">${section.title}</div>""")

        section.children.forEach { node ->
            appendMobileNavNode(sb, node, currentPath, basePath)
        }
        sb.append("""</div>""")
    }

    sb.append("""</nav></details>""")
    return sb.toString()
}

private fun appendMobileNavNode(sb: StringBuilder, node: DocsNavNode, currentPath: String, basePath: String) {
    if (node.children.isEmpty()) {
        val active = normalize(currentPath) == normalize(node.path)
        val activeStyle = if (active) "background: ${PortfolioTheme.Colors.SURFACE_STRONG}; color: ${PortfolioTheme.Colors.ACCENT_ALT};" else ""
        sb.append("""<a href="${safeHref(basePath + node.path)}" style="display: block; padding: 8px 12px; border-radius: 6px; color: ${PortfolioTheme.Colors.TEXT_PRIMARY}; text-decoration: none; $activeStyle">${node.title}</a>""")
    } else {
        sb.append("""<details style="margin-left: 0;">""")
        sb.append("""<summary style="cursor: pointer; font-weight: 500; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; color: ${PortfolioTheme.Colors.TEXT_SECONDARY}; padding: 4px 0;">${node.title}</summary>""")
        sb.append("""<div style="padding-left: 12px; display: flex; flex-direction: column; gap: 4px;">""")
        node.children.forEach { child ->
            appendMobileNavNode(sb, child, currentPath, basePath)
        }
        sb.append("""</div></details>""")
    }
}
