package code.yousef.portfolio.ui.scratchpad

import code.yousef.portfolio.i18n.PortfolioLocale
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

/**
 * The Scratchpad - a brutalist infinite canvas chaos zone.
 * Contains:
 * - Project graveyard (dead projects)
 * - Hot takes scattered as sticky notes
 * - A "don't click" button that spawns physics balls
 * - Terminal overlay for commands
 */
@Composable
fun ScratchpadPage(
    locale: PortfolioLocale = PortfolioLocale.EN
) {
    ScratchpadScaffold {
        // Return link
        Box(
            modifier = Modifier()
                .position(Position.Fixed)
                .top(16.px)
                .left(16.px)
                .zIndex(9600)
        ) {
            AnchorLink(
                label = "[ EXIT ]",
                href = "/experiments",
                modifier = Modifier()
                    .color(ScratchpadTheme.TEXT_PRIMARY)
                    .fontSize(0.9.rem)
                    .textDecoration(TextDecoration.None)
                    .padding(8.px, 12.px)
                    .backgroundColor(ScratchpadTheme.BG_SURFACE)
                    .borderWidth(1)
                    .borderStyle(BorderStyle.Solid)
                    .borderColor(ScratchpadTheme.TEXT_MUTED)
                    .hover(
                        Modifier()
                            .backgroundColor(ScratchpadTheme.TEXT_PRIMARY)
                            .color(ScratchpadTheme.BG_PRIMARY)
                    ),
                navigationMode = LinkNavigationMode.Native
            )
        }

        // Title
        Box(
            modifier = Modifier()
                .position(Position.Fixed)
                .top(16.px)
                .left(0.px)
                .right(0.px)
                .display(Display.Flex)
                .justifyContent(JustifyContent.Center)
                .zIndex(9599)
                .pointerEvents(PointerEvents.None)
        ) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .alignItems(AlignItems.Center)
                    .gap(4.px)
            ) {
                Text(
                    text = "THE SCRATCHPAD",
                    modifier = Modifier()
                        .fontSize(1.5.rem)
                        .fontWeight(700)
                        .color(ScratchpadTheme.TEXT_PRIMARY)
                        .letterSpacing(0.3.rem)
                )
                Text(
                    text = "// where ideas come to die",
                    modifier = Modifier()
                        .fontSize(0.8.rem)
                        .color(ScratchpadTheme.TEXT_MUTED)
                        .fontStyle(FontStyle.Italic)
                )
            }
        }

        // Instructions
        Box(
            modifier = Modifier()
                .position(Position.Fixed)
                .top(16.px)
                .right(16.px)
                .zIndex(9598)
                .backgroundColor(ScratchpadTheme.BG_SURFACE)
                .padding(8.px, 12.px)
                .borderWidth(1)
                .borderStyle(BorderStyle.Solid)
                .borderColor(ScratchpadTheme.BORDER)
        ) {
            Text(
                text = "drag to pan / scroll to zoom",
                modifier = Modifier()
                    .fontSize(0.75.rem)
                    .color(ScratchpadTheme.TEXT_MUTED)
            )
        }

        // Infinite canvas with all the content
        InfiniteCanvas {
            // Hot takes as sticky notes
            StickyNote(
                noteId = "hot-take-1",
                title = "HOT TAKE #1",
                content = "Microservices are just distributed monoliths with extra network latency.",
                x = 4400,
                y = 4850,
                rotation = -3f,
                color = StickyNoteColor.YELLOW
            )

            StickyNote(
                noteId = "hot-take-2",
                title = "HOT TAKE #2",
                content = "The best code is the code you don't write.",
                x = 4350,
                y = 5200,
                rotation = 2f,
                color = StickyNoteColor.PINK
            )

            StickyNote(
                noteId = "hot-take-3",
                title = "HOT TAKE #3",
                content = "GraphQL is just REST with extra steps and a type system you'll eventually ignore anyway.",
                x = 5650,
                y = 4800,
                rotation = -1f,
                color = StickyNoteColor.CYAN
            )

            StickyNote(
                noteId = "thought-1",
                title = "THOUGHT",
                content = "Every time I write 'TODO: fix later', I'm lying to future me.",
                x = 5600,
                y = 5150,
                rotation = 4f,
                color = StickyNoteColor.GREEN
            )

            StickyNote(
                noteId = "note-to-self-1",
                title = "NOTE TO SELF",
                content = "Stop starting new side projects. Finish the ones you have.\n\n(Posted 47 side projects ago)",
                x = 4450,
                y = 5500,
                rotation = -2f,
                color = StickyNoteColor.ORANGE,
                maxWidth = 220
            )

            StickyNote(
                noteId = "unpopular-opinion-1",
                title = "UNPOPULAR OPINION",
                content = "CSS is actually fine. You just need to understand it.",
                x = 5700,
                y = 5400,
                rotation = 1f,
                color = StickyNoteColor.PURPLE
            )

            StickyNote(
                noteId = "code-wisdom-1",
                content = "if (works) {\n  don't.touch();\n}",
                x = 5000,
                y = 5650,
                rotation = 0f,
                color = StickyNoteColor.GREEN,
                maxWidth = 160
            )

            // The forbidden button
            DontClickButton(
                x = 5100,
                y = 5300
            )
        }

        // Terminal at the bottom
        TerminalOverlay()
    }
}
