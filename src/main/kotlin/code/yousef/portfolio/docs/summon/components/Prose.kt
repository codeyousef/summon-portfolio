package code.yousef.portfolio.docs.summon.components

import code.yousef.portfolio.docs.MarkdownDirective
import code.yousef.portfolio.docs.MarkdownDocument
import code.yousef.portfolio.docs.plainText
import code.yousef.portfolio.docs.resolveMarkdownImage
import code.yousef.portfolio.docs.resolveMarkdownLink
import code.yousef.portfolio.theme.PortfolioTheme
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image as SummonImage
import codes.yousef.summon.components.display.Text as SummonText
import codes.yousef.summon.components.html.A
import codes.yousef.summon.components.html.Blockquote as BlockquoteElement
import codes.yousef.summon.components.html.Br
import codes.yousef.summon.components.html.Code as CodeElement
import codes.yousef.summon.components.html.Details
import codes.yousef.summon.components.html.Em
import codes.yousef.summon.components.html.H1
import codes.yousef.summon.components.html.H2
import codes.yousef.summon.components.html.H3
import codes.yousef.summon.components.html.H4
import codes.yousef.summon.components.html.H5
import codes.yousef.summon.components.html.H6
import codes.yousef.summon.components.html.Li
import codes.yousef.summon.components.html.Ol
import codes.yousef.summon.components.html.P
import codes.yousef.summon.components.html.Pre
import codes.yousef.summon.components.html.Strong
import codes.yousef.summon.components.html.Summary
import codes.yousef.summon.components.html.Table
import codes.yousef.summon.components.html.Tbody
import codes.yousef.summon.components.html.Td
import codes.yousef.summon.components.html.Th
import codes.yousef.summon.components.html.Thead
import codes.yousef.summon.components.html.Tr
import codes.yousef.summon.components.html.Ul
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code as MarkdownCode
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image as MarkdownImage
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.ThematicBreak

@Composable
fun Prose(document: MarkdownDocument, modifier: Modifier = Modifier()) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .backgroundColor(PortfolioTheme.Colors.SURFACE)
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor(PortfolioTheme.Colors.BORDER)
            .borderRadius(PortfolioTheme.Radii.lg)
            .padding(PortfolioTheme.Spacing.xl)
            .gap(PortfolioTheme.Spacing.md)
            .alignItems(AlignItems.Stretch)
            .className("prose")
            .maxWidth(100.percent)
            .overflowX(Overflow.Hidden)
            .fontSize(1.rem)
            .lineHeight(1.7)
            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
            .fontFamily(PortfolioTheme.Typography.FONT_SANS)
            .mediaQuery(MediaQuery.MaxWidth(768)) {
                padding(PortfolioTheme.Spacing.md)
            }
            .then(modifier)
    ) {
        renderBlockSequence(document.root.childNodes(), document)
    }
}

@Composable
private fun renderBlockSequence(nodes: List<Node>, document: MarkdownDocument) {
    var index = 0
    while (index < nodes.size) {
        val node = nodes[index]
        when (val directive = document.directiveFor(node)) {
            is MarkdownDirective.DetailsStart -> {
                val closingIndex = findDetailsEnd(nodes, index + 1, document)
                val contentEnd = closingIndex ?: nodes.size
                Details(
                    modifier = Modifier()
                        .borderWidth(1)
                        .borderStyle(BorderStyle.Solid)
                        .borderColor(PortfolioTheme.Colors.BORDER)
                        .borderRadius(PortfolioTheme.Radii.md)
                        .backgroundColor(PortfolioTheme.Colors.SURFACE_STRONG)
                        .padding(PortfolioTheme.Spacing.md)
                ) {
                    Summary(
                        modifier = Modifier()
                            .cursor(Cursor.Pointer)
                            .fontWeight(700)
                            .color(PortfolioTheme.Colors.TEXT_PRIMARY)
                    ) {
                        SummonText(text = directive.summary)
                    }
                    Column(
                        modifier = Modifier()
                            .display(Display.Flex)
                            .flexDirection(FlexDirection.Column)
                            .gap(PortfolioTheme.Spacing.md)
                            .paddingTop(PortfolioTheme.Spacing.md)
                    ) {
                        renderBlockSequence(nodes.subList(index + 1, contentEnd), document)
                    }
                }
                index = if (closingIndex == null) nodes.size else closingIndex + 1
            }

            is MarkdownDirective.DetailsEnd -> index += 1
            is MarkdownDirective.AiRepl -> {
                AiReplDirective(directive.code)
                index += 1
            }

            null -> {
                val next = nodes.getOrNull(index + 1)
                if (
                    document.pairParagraphsWithCode &&
                    node is Paragraph &&
                    next != null &&
                    next.isCodeBlock() &&
                    document.directiveFor(next) == null
                ) {
                    Row(
                        modifier = Modifier()
                            .display(Display.Grid)
                            .gridTemplateColumns(
                                gridMinMax(gridTrack("0"), gridFraction()),
                                gridMinMax(gridTrack("0"), gridFraction()),
                            )
                            .gap(PortfolioTheme.Spacing.lg)
                            .alignItems(AlignItems.FlexStart)
                            .mediaQuery(MediaQuery.MaxWidth(900)) {
                                gridTemplateColumns(gridMinMax(gridTrack("0"), gridFraction()))
                            }
                    ) {
                        Box(modifier = Modifier().minWidth(0.px)) {
                            renderBlock(node, document)
                        }
                        Box(modifier = Modifier().minWidth(0.px)) {
                            renderBlock(next, document)
                        }
                    }
                    index += 2
                } else {
                    renderBlock(node, document)
                    index += 1
                }
            }
        }
    }
}

@Composable
private fun renderBlock(node: Node, document: MarkdownDocument) {
    when (node) {
        is Document -> renderBlockSequence(node.childNodes(), document)
        is Paragraph -> P(modifier = paragraphModifier()) {
            renderInlineChildren(node, document)
        }

        is Heading -> renderHeading(node, document)
        is BlockQuote -> BlockquoteElement(
            modifier = Modifier()
                .margin(0.px)
                .paddingLeft(PortfolioTheme.Spacing.lg)
                .borderWidth(0)
                .borderLeftWidth(4)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.ACCENT_ALT)
                .color(PortfolioTheme.Colors.TEXT_SECONDARY)
        ) {
            renderBlockSequence(node.childNodes(), document)
        }

        is BulletList -> Ul(modifier = listModifier()) {
            renderListItems(node, document, node.isTight)
        }

        is OrderedList -> Ol(start = node.startNumber, modifier = listModifier()) {
            renderListItems(node, document, node.isTight)
        }

        is ListItem -> renderListItem(node, document, tight = false)
        is FencedCodeBlock -> renderCodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> renderCodeBlock(node.literal, null)
        is ThematicBreak -> Box(
            modifier = Modifier()
                .role("separator")
                .height(0.px)
                .width(100.percent)
                .borderWidth(0)
                .borderTopWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
        ) {}

        is TableBlock -> renderTable(node, document)
        is HtmlBlock -> renderEscapedLegacyHtml(node.literal)
        is LinkReferenceDefinition -> Unit
        else -> {
            if (!node.isFrontMatterNode()) {
                val children = node.childNodes()
                if (children.isNotEmpty()) renderBlockSequence(children, document)
            }
        }
    }
}

@Composable
private fun renderHeading(heading: Heading, document: MarkdownDocument) {
    var modifier = headingModifier(heading.level)
    document.headingIds[heading]?.let { modifier = modifier.id(it) }
    when (heading.level) {
        1 -> H1(modifier = modifier) { renderInlineChildren(heading, document) }
        2 -> H2(modifier = modifier) { renderInlineChildren(heading, document) }
        3 -> H3(modifier = modifier) { renderInlineChildren(heading, document) }
        4 -> H4(modifier = modifier) { renderInlineChildren(heading, document) }
        5 -> H5(modifier = modifier) { renderInlineChildren(heading, document) }
        else -> H6(modifier = modifier) { renderInlineChildren(heading, document) }
    }
}

@Composable
private fun renderListItems(list: Node, document: MarkdownDocument, tight: Boolean) {
    list.childNodes().filterIsInstance<ListItem>().forEach { item ->
        renderListItem(item, document, tight)
    }
}

@Composable
private fun renderListItem(item: ListItem, document: MarkdownDocument, tight: Boolean) {
    Li(modifier = Modifier().padding(2.px, 0.px)) {
        item.childNodes().forEach { child ->
            if (tight && child is Paragraph) {
                renderInlineChildren(child, document)
            } else {
                renderBlock(child, document)
            }
        }
    }
}

@Composable
private fun renderCodeBlock(literal: String, info: String?) {
    val language = info
        ?.trim()
        ?.substringBefore(' ')
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
    Pre(
        modifier = Modifier()
            .margin(0.px)
            .backgroundColor("#0b0d12")
            .padding(1.rem)
            .borderRadius(PortfolioTheme.Radii.md)
            .overflowX(Overflow.Auto)
            .maxWidth(100.percent)
            .whiteSpace(WhiteSpace.Pre)
    ) {
        CodeElement(
            modifier = Modifier()
                .backgroundColor("transparent")
                .padding(0.px)
                .fontFamily(PortfolioTheme.Typography.FONT_MONO)
                .fontSize(0.9.rem)
                .let { base ->
                    if (language == null) base else base.className("language-$language")
                }
        ) {
            SummonText(text = literal)
        }
    }
}

@Composable
private fun renderTable(table: TableBlock, document: MarkdownDocument) {
    Box(modifier = Modifier().maxWidth(100.percent).overflowX(Overflow.Auto)) {
        Table(
            modifier = Modifier()
                .width(100.percent)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(PortfolioTheme.Colors.BORDER)
        ) {
            table.childNodes().forEach { section ->
                when (section) {
                    is TableHead -> Thead { renderTableRows(section, document) }
                    is TableBody -> Tbody { renderTableRows(section, document) }
                    is TableRow -> renderTableRow(section, document)
                }
            }
        }
    }
}

@Composable
private fun renderTableRows(section: Node, document: MarkdownDocument) {
    section.childNodes().filterIsInstance<TableRow>().forEach { row ->
        renderTableRow(row, document)
    }
}

@Composable
private fun renderTableRow(row: TableRow, document: MarkdownDocument) {
    Tr {
        row.childNodes().filterIsInstance<TableCell>().forEach { cell ->
            val modifier = tableCellModifier(cell.alignment, cell.isHeader)
            if (cell.isHeader) {
                Th(scope = "col", modifier = modifier) {
                    renderInlineChildren(cell, document)
                }
            } else {
                Td(modifier = modifier) {
                    renderInlineChildren(cell, document)
                }
            }
        }
    }
}

@Composable
private fun renderInlineChildren(parent: Node, document: MarkdownDocument) {
    var child = parent.firstChild
    while (child != null) {
        renderInline(child, document)
        child = child.next
    }
}

@Composable
private fun renderInline(node: Node, document: MarkdownDocument) {
    when (node) {
        is MarkdownText -> SummonText(text = node.literal)
        is SoftLineBreak -> SummonText(text = " ")
        is HardLineBreak -> Br()
        is MarkdownCode -> CodeElement(
            modifier = Modifier()
                .backgroundColor(PortfolioTheme.Colors.BORDER)
                .padding("0.15em", "0.4em")
                .borderRadius(4.px)
                .fontFamily(PortfolioTheme.Typography.FONT_MONO)
                .fontSize("0.9em")
        ) {
            SummonText(text = node.literal)
        }

        is Emphasis -> Em { renderInlineChildren(node, document) }
        is StrongEmphasis -> Strong { renderInlineChildren(node, document) }
        is Link -> renderLink(node, document)
        is MarkdownImage -> renderImage(node, document)
        is HtmlInline -> {
            if (node.literal.trim().matches(HTML_LINE_BREAK)) {
                Br()
            } else {
                SummonText(text = node.literal)
            }
        }

        else -> renderInlineChildren(node, document)
    }
}

@Composable
private fun renderLink(link: Link, document: MarkdownDocument) {
    val resolved = document.resolveMarkdownLink(link.destination)
    if (resolved == null) {
        renderInlineChildren(link, document)
        return
    }

    var modifier = Modifier()
        .color(PortfolioTheme.Colors.LINK)
        .textDecoration(TextDecoration.Underline)
        .hover(Modifier().color(PortfolioTheme.Colors.LINK_HOVER))
        .visited(Modifier().color(PortfolioTheme.Colors.LINK))
    if (resolved.isDocLink) modifier = modifier.dataAttribute("doc-link", "true")
    link.title?.takeIf { it.isNotBlank() }?.let { modifier = modifier.attribute("title", it) }
    A(
        href = resolved.href,
        target = resolved.target,
        rel = resolved.rel,
        modifier = modifier
    ) {
        renderInlineChildren(link, document)
    }
}

@Composable
private fun renderImage(image: MarkdownImage, document: MarkdownDocument) {
    val source = document.resolveMarkdownImage(image.destination)
    val alt = image.plainText()
    if (source == null) {
        if (alt.isNotBlank()) SummonText(text = alt)
        return
    }
    var modifier = Modifier()
        .maxWidth(100.percent)
        .height("auto")
    image.title?.takeIf { it.isNotBlank() }?.let { modifier = modifier.attribute("title", it) }
    SummonImage(src = source, alt = alt, modifier = modifier)
}

@Composable
private fun AiReplDirective(code: String) {
    Column(
        modifier = Modifier()
            .className("ai-repl")
            .dataAttribute("code", code)
            .role("region")
            .ariaAttribute("label", "Python code example")
            .borderWidth(1)
            .borderStyle(BorderStyle.Solid)
            .borderColor("#3a3a4a")
            .borderRadius(PortfolioTheme.Radii.md)
            .overflow(Overflow.Hidden)
            .backgroundColor("#1e1e2e")
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .padding(6.px, 12.px)
                .backgroundColor("#2a2a3a")
        ) {
            SummonText(
                text = "Python example",
                modifier = Modifier()
                    .fontFamily(PortfolioTheme.Typography.FONT_MONO)
                    .fontSize(0.8.rem)
                    .color("#a6adc8")
            )
        }
        Pre(
            modifier = Modifier()
                .margin(0.px)
                .padding(PortfolioTheme.Spacing.md)
                .overflowX(Overflow.Auto)
                .backgroundColor("#1e1e2e")
        ) {
            CodeElement(modifier = Modifier().fontFamily(PortfolioTheme.Typography.FONT_MONO)) {
                SummonText(text = code)
            }
        }
    }
}

@Composable
private fun renderEscapedLegacyHtml(literal: String) {
    val value = literal.trim()
    if (value.isNotBlank()) {
        P(modifier = paragraphModifier().color(PortfolioTheme.Colors.TEXT_SECONDARY)) {
            SummonText(text = value)
        }
    }
}

private fun MarkdownDocument.directiveFor(node: Node): MarkdownDirective? {
    if (node !is Paragraph || node.firstChild !== node.lastChild) return null
    val marker = (node.firstChild as? MarkdownText)?.literal ?: return null
    return directives[marker]
}

private fun findDetailsEnd(
    nodes: List<Node>,
    startIndex: Int,
    document: MarkdownDocument
): Int? {
    var depth = 1
    for (index in startIndex until nodes.size) {
        when (document.directiveFor(nodes[index])) {
            is MarkdownDirective.DetailsStart -> depth += 1
            is MarkdownDirective.DetailsEnd -> {
                depth -= 1
                if (depth == 0) return index
            }
            else -> Unit
        }
    }
    return null
}

private fun Node.childNodes(): List<Node> = buildList {
    var child = firstChild
    while (child != null) {
        add(child)
        child = child.next
    }
}

private fun Node.isCodeBlock(): Boolean = this is FencedCodeBlock || this is IndentedCodeBlock

private fun Node.isFrontMatterNode(): Boolean =
    javaClass.name.startsWith("org.commonmark.ext.front.matter.")

private fun paragraphModifier(): Modifier = Modifier()
    .margin(0.px)
    .maxWidth(100.percent)
    .lineHeight(1.7)

private fun headingModifier(level: Int): Modifier = Modifier()
    .margin(0.px)
    .fontSize(
        when (level) {
            1 -> 2.2.rem
            2 -> 1.75.rem
            3 -> 1.4.rem
            4 -> 1.2.rem
            5 -> 1.05.rem
            else -> 1.rem
        }
    )
    .fontWeight(if (level <= 3) 750 else 700)
    .lineHeight(1.25)
    .color(PortfolioTheme.Colors.TEXT_PRIMARY)

private fun listModifier(): Modifier = Modifier()
    .margin(0.px)
    .paddingLeft(PortfolioTheme.Spacing.xl)
    .lineHeight(1.7)

private fun tableCellModifier(alignment: TableCell.Alignment?, header: Boolean): Modifier {
    val textAlign = when (alignment) {
        TableCell.Alignment.CENTER -> TextAlign.Center
        TableCell.Alignment.RIGHT -> TextAlign.Right
        else -> TextAlign.Left
    }
    return Modifier()
        .padding(10.px, 12.px)
        .borderWidth(1)
        .borderStyle(BorderStyle.Solid)
        .borderColor(PortfolioTheme.Colors.BORDER)
        .textAlign(textAlign)
        .backgroundColor(if (header) PortfolioTheme.Colors.SURFACE_STRONG else "transparent")
        .fontWeight(if (header) 700 else 400)
}

private val HTML_LINE_BREAK = Regex("""(?i)^<br\s*/?>$""")
