package code.yousef.portfolio.ui.photography

import code.yousef.portfolio.content.model.PhotographyPhoto
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*

@Composable
fun PhotographyPage(
    photos: List<PhotographyPhoto>
) {
    Column(
        modifier = Modifier()
            .minHeight("100vh")
            .backgroundColor("#070707")
            .color("#f5f1ea")
            .fontFamily("ui-serif, Georgia, serif")
            .padding(28.px)
            .gap(40.px)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween)
                .gap(20.px)
                .width(100.percent)
        ) {
            Column(
                modifier = Modifier()
                    .display(Display.Flex)
                    .flexDirection(FlexDirection.Column)
                    .gap(8.px)
                    .minWidth(0.px)
            ) {
                Text(
                    text = "Photography",
                    modifier = Modifier()
                        .fontSize("clamp(2.5rem, 9vw, 7.5rem)")
                        .lineHeight(0.9)
                        .fontWeight(400)
                        .letterSpacing(0.px)
                )
                Paragraph(
                    text = "Selected frames, quiet light, and places worth remembering.",
                    modifier = Modifier()
                        .maxWidth(720.px)
                        .fontSize(1.05.rem)
                        .lineHeight(1.6)
                        .color("#b8b0a3")
                )
            }
            AnchorLink(
                label = "Home",
                href = "/",
                modifier = Modifier()
                    .display(Display.InlineFlex)
                    .alignItems(AlignItems.Center)
                    .justifyContent(JustifyContent.Center)
                    .height(44.px)
                    .padding(0.px, 18.px)
                    .border("1px", "solid", "#39342c")
                    .borderRadius(4.px)
                    .color("#f5f1ea")
                    .textDecoration(TextDecoration.None)
                    .whiteSpace(WhiteSpace.NoWrap),
                navigationMode = LinkNavigationMode.Native
            )
        }

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier()
                    .border("1px", "solid", "#28231d")
                    .padding(28.px)
                    .maxWidth(620.px)
            ) {
                Text(
                    text = "No published photos yet.",
                    modifier = Modifier().color("#b8b0a3").fontSize(1.rem)
                )
            }
        } else {
            Row(
                modifier = Modifier()
                    .display(Display.Grid)
                    .gridTemplateColumns("repeat(auto-fit, minmax(260px, 1fr))")
                    .gap(18.px)
                    .width(100.percent)
            ) {
                photos.forEach { photo ->
                    PhotoCard(photo)
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(photo: PhotographyPhoto) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(12.px)
            .minWidth(0.px)
    ) {
        Image(
            src = "/uploads/photography/${photo.id}",
            alt = photo.altText,
            modifier = Modifier()
                .width(100.percent)
                .height(360.px)
                .objectFit(ObjectFit.Cover)
                .backgroundColor("#12100d")
                .attribute("loading", "lazy")
                .attribute("decoding", "async")
        )
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(4.px)
        ) {
            Text(
                text = photo.title,
                modifier = Modifier()
                    .fontSize(1.1.rem)
                    .fontWeight(600)
                    .lineHeight(1.25)
            )
            if (!photo.caption.isNullOrBlank()) {
                Paragraph(
                    text = photo.caption,
                    modifier = Modifier()
                        .fontSize(0.92.rem)
                        .lineHeight(1.5)
                        .color("#a29a8f")
                )
            }
            photo.takenAt?.let {
                Text(
                    text = it.year.toString(),
                    modifier = Modifier()
                        .fontSize(0.78.rem)
                        .textTransform(TextTransform.Uppercase)
                        .color("#7c746a")
                )
            }
        }
    }
}
