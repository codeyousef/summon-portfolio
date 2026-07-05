package code.yousef.portfolio.ui.admin

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.Link
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

private data class AdminLinkItem(
    val title: String,
    val description: String,
    val href: String,
    val primary: Boolean = false
)

private val adminLinks = listOf(
    AdminLinkItem(
        title = "Photography",
        description = "Upload photos and manage the public photography page.",
        href = "/admin/photography",
        primary = true
    ),
    AdminLinkItem("Projects", "Edit portfolio project entries.", "/admin/projects"),
    AdminLinkItem("Services", "Edit service cards.", "/admin/services"),
    AdminLinkItem("Blog posts", "Edit long-form posts.", "/admin/blog_posts"),
    AdminLinkItem("Testimonials", "Edit testimonials.", "/admin/testimonials"),
    AdminLinkItem("Contact submissions", "Review incoming contact messages.", "/admin/contact_submissions")
)

@Composable
fun AdminHomePage(
    username: String
) {
    Column(
        modifier = Modifier()
            .minHeight("100vh")
            .backgroundColor("#0d1117")
            .color("#e6edf3")
            .fontFamily("ui-sans-serif, system-ui, sans-serif")
            .padding(28.px)
            .gap(28.px)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .gap(16.px)
                .width(100.percent)
                .flexWrap(FlexWrap.Wrap)
        ) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(6.px)
            ) {
                Text(text = "Admin", modifier = Modifier().fontSize(2.rem).fontWeight(800))
                Text(
                    text = "Signed in as $username",
                    modifier = Modifier().color("#8b949e").fontSize(0.95.rem)
                )
            }
            Row(modifier = Modifier().display(Display.Flex).gap(10.px).flexWrap(FlexWrap.Wrap)) {
                AdminPillLink("View site", "/")
                AdminPillLink("Photography page", "/photography")
            }
        }

        Row(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(240px, 1fr))")
                .gap(16.px)
                .width(100.percent)
        ) {
            adminLinks.forEach { item ->
                AdminCardLink(item)
            }
        }
    }
}

@Composable
private fun AdminCardLink(item: AdminLinkItem) {
    Link(
        href = item.href,
        modifier = Modifier()
            .display(Display.Block)
            .padding(18.px)
            .border("1px", "solid", if (item.primary) "#2ea043" else "#30363d")
            .borderRadius(6.px)
            .backgroundColor(if (item.primary) "#10281a" else "#161b22")
            .color("#e6edf3")
            .textDecoration(TextDecoration.None)
            .minHeight(138.px),
        target = null
    ) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(10.px)
        ) {
            Text(
                text = item.title,
                modifier = Modifier()
                    .fontSize(1.15.rem)
                    .fontWeight(800)
                    .color(if (item.primary) "#d4f8df" else "#e6edf3")
            )
            Text(
                text = item.description,
                modifier = Modifier()
                    .fontSize(0.92.rem)
                    .lineHeight(1.5)
                    .color("#8b949e")
            )
        }
    }
}

@Composable
private fun AdminPillLink(label: String, href: String) {
    AnchorLink(
        label = label,
        href = href,
        modifier = Modifier()
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .height(38.px)
            .padding(0.px, 14.px)
            .border("1px", "solid", "#30363d")
            .borderRadius(6.px)
            .color("#e6edf3")
            .textDecoration(TextDecoration.None),
        navigationMode = LinkNavigationMode.Native
    )
}
