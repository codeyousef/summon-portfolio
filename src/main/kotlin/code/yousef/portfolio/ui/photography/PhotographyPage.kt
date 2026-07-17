package code.yousef.portfolio.ui.photography

import code.yousef.portfolio.content.model.PhotographyMediaType
import code.yousef.portfolio.content.model.PhotographyPhoto
import code.yousef.portfolio.content.model.PhotographySourceKind
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.components.AppHeader
import code.yousef.portfolio.ui.components.GlobalNavigationDestination
import code.yousef.portfolio.ui.foundation.PageScaffold
import code.yousef.portfolio.ui.sections.ContactFooterSection
import code.yousef.portfolio.ui.sections.PortfolioFooter
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Image
import codes.yousef.summon.components.display.ImageDecoding
import codes.yousef.summon.components.display.ImageSource
import codes.yousef.summon.components.display.Paragraph
import codes.yousef.summon.components.display.Picture
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.html.Iframe
import codes.yousef.summon.components.layout.Box
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.components.layout.Row
import codes.yousef.summon.components.media.Video
import codes.yousef.summon.components.media.VideoSource
import codes.yousef.summon.components.navigation.AnchorLink
import codes.yousef.summon.components.navigation.LinkNavigationMode
import codes.yousef.summon.components.styles.StyleElement
import codes.yousef.summon.components.styles.StylePseudoClass
import codes.yousef.summon.components.styles.StyleRulePriority
import codes.yousef.summon.components.styles.StyleSelector
import codes.yousef.summon.components.styles.TypedStyleSheet
import codes.yousef.summon.extensions.percent
import codes.yousef.summon.extensions.px
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun PhotographyPage(
    photos: List<PhotographyPhoto>,
    selectedFilter: String? = null,
    locale: PortfolioLocale = PortfolioLocale.EN
) {
    val media = photos.sortedWith(compareBy<PhotographyPhoto> { it.order }.thenByDescending { it.uploadedAt })
    val activeFilter = selectedFilter.normalizedMediaFilter(media)
    val filteredMedia = media.filter { it.matchesMediaFilter(activeFilter) }
    val hero = media.firstOrNull { it.featured } ?: media.firstOrNull()

    PageScaffold(locale = locale, enableAuroraEffects = false) {
        PhotographyStyles()
        AppHeader(
            locale = locale,
            activeDestination = GlobalNavigationDestination.PHOTOGRAPHY,
        )
        if (hero == null) {
            EmptyPhotographyPage()
        } else {
            PhotographyHero(hero = hero, media = media)
            MediaFilters(media = media, activeFilter = activeFilter)
            MediaGallery(media = filteredMedia)
        }

        ContactFooterSection(locale = locale, modifier = Modifier().id("contact"))
        PortfolioFooter(locale = locale)
    }
}

@Composable
private fun PhotographyStyles() {
    val shell = StyleSelector.className("photography-shell")
    val hero = StyleSelector.className("photography-hero")
    val frame = StyleSelector.className("photography-frame")
    val cardFrame = StyleSelector.className("photography-card-frame")
    val filter = StyleSelector.className("photography-filter")
    val picture = StyleSelector.className("photography-picture")
    val media = StyleSelector.className("photography-media")
        .or(
            StyleSelector.className("photography-video"),
            StyleSelector.className("photography-iframe")
        )

    TypedStyleSheet {
        rule(shell, Modifier().width(100.percent).maxWidth(1240.px).margin("0 auto"))
        rule(
            hero,
            Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("minmax(0, 0.92fr) minmax(420px, 1.08fr)")
                .gap(32.px)
                .alignItems(AlignItems.FlexEnd)
                .minHeight("calc(100vh - 168px)")
        )
        rule(frame, Modifier().aspectRatio(16.0 / 10.0))
        rule(
            StyleSelector.className("photography-title"),
            Modifier().fontSize(4.2.rem),
            StyleRulePriority.Important
        )
        rule(
            StyleSelector.className("photography-empty-title"),
            Modifier().fontSize(3.2.rem),
            StyleRulePriority.Important
        )
        rule(cardFrame, Modifier().aspectRatio(4.0 / 3.0))
        rule(
            media,
            Modifier()
                .width(100.percent)
                .height(100.percent)
                .display(Display.Block)
                .objectFit(ObjectFit.Cover)
                .borderWidth(0)
        )
        rule(picture, Modifier().width(100.percent).height(100.percent).display(Display.Block))
        rule(
            picture.descendant(StyleSelector.element(StyleElement.Image)),
            Modifier()
                .width(100.percent)
                .height(100.percent)
                .display(Display.Block)
                .objectFit(ObjectFit.Cover)
                .borderWidth(0)
        )
        rule(
            filter,
            Modifier().transition(
                "background ${PortfolioTheme.Motion.DEFAULT}, border-color ${PortfolioTheme.Motion.DEFAULT}, color ${PortfolioTheme.Motion.DEFAULT}"
            )
        )
        rule(
            filter.withClass("is-active"),
            Modifier().backgroundColor("#ffffff").borderColor("#ffffff").color("#060607")
        )
        rule(
            StyleSelector.className("photography-card"),
            Modifier().transition("transform ${PortfolioTheme.Motion.DEFAULT}, border-color ${PortfolioTheme.Motion.DEFAULT}")
        )
        rule(
            StyleSelector.className("photography-card").pseudoClass(StylePseudoClass.Hover),
            Modifier()
                .transform(TransformFunction.TranslateY to "-3px")
                .borderColor("rgba(255,255,255,0.34)")
        )
        media(MediaQuery.MaxWidth(980)) {
            rule(hero, Modifier().gridTemplateColumns(gridFraction()).minHeight("auto"))
        }
        media(MediaQuery.MaxWidth(720)) {
            rule(shell, Modifier().maxWidth(100.percent))
            rule(
                StyleSelector.className("photography-title"),
                Modifier().fontSize(2.7.rem),
                StyleRulePriority.Important
            )
            rule(
                StyleSelector.className("photography-empty-title"),
                Modifier().fontSize(2.55.rem),
                StyleRulePriority.Important
            )
            rule(hero, Modifier().gap(22.px))
            rule(frame.or(cardFrame), Modifier().aspectRatio(1.0))
        }
    }
}

@Composable
private fun PhotographyHero(hero: PhotographyPhoto, media: List<PhotographyPhoto>) {
    Row(
        modifier = Modifier()
            .display(Display.Grid)
            .className("photography-shell photography-hero")
            .gap(PortfolioTheme.Spacing.xl)
            .alignItems(AlignItems.FlexStart)
            .minHeight("auto")
    ) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.lg)
                .paddingBottom(PortfolioTheme.Spacing.lg)
                .minWidth(0.px)
        ) {
            Text(
                text = "Photography & Motion",
                modifier = Modifier()
                    .fontSize(4.2.rem)
                    .lineHeight(0.98)
                    .fontWeight(800)
                    .letterSpacing(0.px)
                    .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
                    .color("#ffffff")
                    .className("photography-title")
            )
            Paragraph(
                text = "Albums, still frames, field video, and 360 work gathered into a visual record of places, light, and motion.",
                modifier = Modifier()
                    .maxWidth(680.px)
                    .fontSize(1.08.rem)
                    .lineHeight(1.75)
                    .color("#d6d1ca")
            )
            HeroMetrics(media)
        }

        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
                .minWidth(0.px)
        ) {
            MediaFrame(photo = hero, large = true)
            MediaMeta(photo = hero, prominent = true)
        }
    }
}

@Composable
private fun HeroMetrics(media: List<PhotographyPhoto>) {
    val categories = media.mapNotNull { it.category.displayCategory() }.distinct().size
    val albums = media.mapNotNull { it.albumTitle?.trim()?.takeIf(String::isNotBlank) }.distinct().size
    val videos = media.count { it.mediaType == PhotographyMediaType.VIDEO || it.mediaType == PhotographyMediaType.VIDEO_360 }
    Row(
        modifier = Modifier()
            .display(Display.Flex)
            .gap(PortfolioTheme.Spacing.md)
            .flexWrap(FlexWrap.Wrap)
    ) {
        MetricPill(media.size.toString(), "Pieces")
        MetricPill(categories.toString(), "Categories")
        MetricPill(albums.toString(), "Albums")
        MetricPill(videos.toString(), "Videos")
    }
}

@Composable
private fun MetricPill(value: String, label: String) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(2.px)
            .minWidth(116.px)
            .padding(12.px, 14.px)
            .border("1px", "solid", "rgba(255,255,255,0.16)")
            .borderRadius(8.px)
            .backgroundColor("rgba(255,255,255,0.05)")
    ) {
        Text(text = value, modifier = Modifier().fontSize(1.35.rem).fontWeight(800).color("#ffffff"))
        Text(text = label, modifier = Modifier().fontSize(0.78.rem).textTransform(TextTransform.Uppercase).color("#b7b0a6").letterSpacing(0.px))
    }
}

@Composable
private fun MediaFilters(media: List<PhotographyPhoto>, activeFilter: String) {
    val categories = media.mapNotNull { it.category.displayCategory() }.distinct()
    Column(
        modifier = Modifier()
            .className("photography-shell")
            .id("media-gallery")
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.sm)
            .paddingTop(PortfolioTheme.Spacing.lg)
            .paddingBottom(PortfolioTheme.Spacing.md)
    ) {
        Row(
            modifier = Modifier()
                .display(Display.Flex)
                .gap(PortfolioTheme.Spacing.sm)
                .flexWrap(FlexWrap.Wrap)
        ) {
            FilterChip("All", "all", activeFilter)
            FilterChip("Photos", "type:PHOTO", activeFilter)
            FilterChip("Video", "type:VIDEO", activeFilter)
            FilterChip("360", "type:VIDEO_360", activeFilter)
            categories.forEach { category ->
                FilterChip(category, "category:${category.slug()}", activeFilter)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, filter: String, activeFilter: String) {
    val active = filter == activeFilter
    val href = "/photography?filter=${filter.urlQueryComponent()}#media-gallery"
    AnchorLink(
        label = label,
        href = href,
        modifier = Modifier()
            .display(Display.InlineFlex)
            .alignItems(AlignItems.Center)
            .height(34.px)
            .padding(0.px, 14.px)
            .border("1px", "solid", "rgba(255,255,255,0.18)")
            .borderRadius(999.px)
            .backgroundColor(if (active) "#ffffff" else "rgba(255,255,255,0.05)")
            .color(if (active) "#060607" else "#f3eee7")
            .fontSize(0.82.rem)
            .fontWeight(700)
            .textDecoration(TextDecoration.None)
            .className("photography-filter${if (active) " is-active" else ""}"),
        dataAttributes = mapOf("media-filter" to filter),
        navigationMode = LinkNavigationMode.Native
    )
}

@Composable
private fun MediaGallery(media: List<PhotographyPhoto>) {
    Column(
        modifier = Modifier()
            .className("photography-shell")
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.xxl)
            .paddingTop(PortfolioTheme.Spacing.lg)
    ) {
        if (media.isEmpty()) {
            Paragraph(
                text = "No media matches this filter.",
                modifier = Modifier().color("#c5bdb2").fontSize(1.05.rem)
            )
        } else {
            media.groupBy { it.category.normalizedCategory() }.forEach { (category, categoryItems) ->
                CategorySection(category = category, items = categoryItems)
            }
        }
    }
}

@Composable
private fun CategorySection(category: String, items: List<PhotographyPhoto>) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.xl)
            .id("category-${category.slug()}")
            .dataAttribute("media-section", category.slug())
    ) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.xs)
        ) {
            if (!category.isUncategorized()) {
                Text(
                    text = category,
                    modifier = Modifier()
                        .fontSize(2.15.rem)
                        .fontWeight(800)
                        .lineHeight(1.1)
                        .letterSpacing(0.px)
                        .color("#ffffff")
                )
                Text(
                    text = "${items.size} ${if (items.size == 1) "piece" else "pieces"}",
                    modifier = Modifier()
                        .fontSize(0.86.rem)
                        .textTransform(TextTransform.Uppercase)
                        .color("#9f978c")
                        .letterSpacing(0.px)
                )
            }
        }

        items.groupBy { it.albumTitle?.trim()?.takeIf(String::isNotBlank) ?: "Singles" }.forEach { (album, albumItems) ->
            AlbumSection(album = album, items = albumItems)
        }
    }
}

@Composable
private fun AlbumSection(album: String, items: List<PhotographyPhoto>) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
    ) {
        if (album != "Singles") {
            Text(
                text = album,
                modifier = Modifier()
                    .fontSize(1.05.rem)
                    .fontWeight(800)
                    .textTransform(TextTransform.Uppercase)
                    .color("#d9d2c8")
                    .letterSpacing(0.px)
            )
        }
        Row(
            modifier = Modifier()
                .display(Display.Grid)
                .gridTemplateColumns("repeat(auto-fit, minmax(260px, 1fr))")
                .gap(PortfolioTheme.Spacing.lg)
        ) {
            items.forEach { photo -> MediaCard(photo) }
        }
    }
}

@Composable
private fun MediaCard(photo: PhotographyPhoto) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
            .minWidth(0.px)
            .padding(10.px)
            .border("1px", "solid", "rgba(255,255,255,0.13)")
            .borderRadius(8.px)
            .backgroundColor("rgba(255,255,255,0.045)")
            .className("photography-card")
            .dataAttribute("media-item", photo.id)
            .dataAttribute("media-type", photo.mediaType.name)
            .dataAttribute("media-category", photo.category.normalizedCategory().slug())
    ) {
        MediaFrame(photo = photo, large = false)
        MediaMeta(photo = photo, prominent = false)
    }
}

@Composable
private fun MediaFrame(photo: PhotographyPhoto, large: Boolean) {
    Box(
        modifier = Modifier()
            .position(Position.Relative)
            .overflow(Overflow.Hidden)
            .borderRadius(8.px)
            .backgroundColor("#111111")
            .className(if (large) "photography-frame" else "photography-card-frame")
    ) {
        when {
            photo.mediaType == PhotographyMediaType.PHOTO -> PhotoImage(photo)
            photo.sourceKind == PhotographySourceKind.UPLOAD -> UploadedVideo(photo)
            else -> ExternalVideo(photo)
        }
        BadgeRow(photo)
    }
}

@Composable
private fun PhotoImage(photo: PhotographyPhoto) {
    when (photo.sourceKind) {
        PhotographySourceKind.UPLOAD -> Picture(
            sources = listOf(
                ImageSource(
                    srcset = photo.primaryImageSource(),
                    type = photo.contentType.takeIf { it.startsWith("image/") }
                )
            ),
            fallbackSrc = mediaSource(photo),
            alt = photo.altText,
            decoding = ImageDecoding.ASYNC,
            modifier = Modifier().className("photography-picture")
        )
        PhotographySourceKind.EXTERNAL -> Image(
            src = photo.primaryImageSource(),
            alt = photo.altText,
            decoding = ImageDecoding.ASYNC,
            modifier = Modifier().className("photography-media")
        )
    }
}

@Composable
private fun UploadedVideo(photo: PhotographyPhoto) {
    Video(
        sources = listOf(
            VideoSource(
                src = mediaSource(photo),
                type = photo.contentType
            )
        ),
        poster = photo.thumbnailUrl?.takeIf { it.isNotBlank() },
        controls = true,
        playsInline = true,
        preload = "metadata",
        ariaLabel = photo.altText,
        modifier = Modifier().className("photography-video")
    )
}

@Composable
private fun ExternalVideo(photo: PhotographyPhoto) {
    val embedUrl = photo.externalUrl?.let { externalEmbedUrl(it) }
    when {
        embedUrl != null -> Iframe(
            src = embedUrl,
            title = photo.title,
            loading = "lazy",
            allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share",
            modifier = Modifier()
                .className("photography-iframe")
                .attribute("allowfullscreen", "")
        )
        !photo.thumbnailUrl.isNullOrBlank() -> ExternalVideoFallback(photo, showImage = true)
        else -> ExternalVideoFallback(photo, showImage = false)
    }
}

@Composable
private fun ExternalVideoFallback(photo: PhotographyPhoto, showImage: Boolean) {
    if (showImage) {
        Image(
            src = photo.thumbnailUrl.orEmpty(),
            alt = photo.altText,
            modifier = Modifier()
                .width(100.percent)
                .height(100.percent)
                .objectFit(ObjectFit.Cover)
                .className("photography-media")
        )
    } else {
        Box(
            modifier = Modifier()
                .width(100.percent)
                .height(100.percent)
                .display(Display.Flex)
                .alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.Center)
                .backgroundColor("#141414")
        ) {
            Text(text = photo.mediaType.label(), modifier = Modifier().color("#cfc7bb").fontWeight(800))
        }
    }
    photo.externalUrl?.let { url ->
        AnchorLink(
            label = "Open media",
            href = url,
            modifier = Modifier()
                .position(Position.Absolute)
                .right(12.px)
                .bottom(12.px)
                .padding(8.px, 12.px)
                .borderRadius(999.px)
                .backgroundColor("rgba(0,0,0,0.72)")
                .color("#ffffff")
                .fontSize(0.78.rem)
                .fontWeight(800)
                .textDecoration(TextDecoration.None),
            target = "_blank",
            rel = "noopener",
            navigationMode = LinkNavigationMode.Native
        )
    }
}

@Composable
private fun BadgeRow(photo: PhotographyPhoto) {
    Row(
        modifier = Modifier()
            .position(Position.Absolute)
            .left(12.px)
            .top(12.px)
            .display(Display.Flex)
            .gap(6.px)
            .flexWrap(FlexWrap.Wrap)
    ) {
        MediaBadge(photo.mediaType.label())
        if (photo.featured) MediaBadge("Featured")
    }
}

@Composable
private fun MediaBadge(label: String) {
    Box(
        modifier = Modifier()
            .backgroundColor("rgba(0,0,0,0.72)")
            .color("#ffffff")
            .padding(5.px, 8.px)
            .borderRadius(999.px)
            .fontSize(0.72.rem)
            .fontWeight(800)
            .textTransform(TextTransform.Uppercase)
            .letterSpacing(0.px)
    ) {
        Text(text = label)
    }
}

@Composable
private fun MediaMeta(photo: PhotographyPhoto, prominent: Boolean) {
    Column(
        modifier = Modifier()
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.xs)
            .minWidth(0.px)
    ) {
        Text(
            text = photo.title,
            modifier = Modifier()
                .fontSize(if (prominent) 1.45.rem else 1.05.rem)
                .fontWeight(800)
                .lineHeight(1.24)
                .color("#ffffff")
        )
        if (!photo.caption.isNullOrBlank()) {
            Paragraph(
                text = photo.caption,
                modifier = Modifier()
                    .fontSize(if (prominent) 1.rem else 0.9.rem)
                    .lineHeight(1.55)
                    .color("#c5bdb2")
            )
        }
        Text(
            text = listOfNotNull(photo.takenAt?.year?.toString(), photo.category.displayCategory(), photo.albumTitle).joinToString(" / "),
            modifier = Modifier()
                .fontSize(0.78.rem)
                .fontWeight(800)
                .textTransform(TextTransform.Uppercase)
                .color("#91887d")
                .letterSpacing(0.px)
        )
    }
}

@Composable
private fun EmptyPhotographyPage() {
    Column(
        modifier = Modifier()
            .className("photography-shell")
            .display(Display.Flex)
            .flexDirection(FlexDirection.Column)
            .gap(PortfolioTheme.Spacing.md)
            .minHeight("52vh")
            .justifyContent(JustifyContent.Center)
    ) {
        Text(
            text = "Photography & Motion",
            modifier = Modifier()
                .fontSize(3.2.rem)
                .fontWeight(800)
                .fontFamily(PortfolioTheme.Typography.FONT_SERIF)
                .color("#ffffff")
                .className("photography-empty-title")
        )
        Paragraph(
            text = "No published media yet.",
            modifier = Modifier().color("#c5bdb2").fontSize(1.05.rem)
        )
    }
}

private fun mediaSource(photo: PhotographyPhoto): String =
    when (photo.sourceKind) {
        PhotographySourceKind.UPLOAD -> "/uploads/photography/${photo.uploadAssetRef().urlPathSegment()}"
        PhotographySourceKind.EXTERNAL -> photo.externalUrl.orEmpty()
    }

private fun PhotographyPhoto.primaryImageSource(): String =
    when (sourceKind) {
        PhotographySourceKind.UPLOAD -> "/uploads/photography/${id.urlPathSegment()}"
        PhotographySourceKind.EXTERNAL -> externalUrl.orEmpty()
    }

private fun PhotographyPhoto.uploadAssetRef(): String =
    storageKey.replace('\\', '/').substringAfterLast('/').takeIf { it.isNotBlank() } ?: id

private fun String.urlPathSegment(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")

private fun String.urlQueryComponent(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")

private fun externalEmbedUrl(url: String): String? =
    runCatching {
        val uri = URI(url)
        val host = uri.host?.removePrefix("www.")?.lowercase() ?: return@runCatching null
        when {
            host == "youtu.be" -> uri.path.trim('/').takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/embed/$it" }
            host.endsWith("youtube.com") -> queryParam(uri.rawQuery, "v")?.let { "https://www.youtube.com/embed/$it" }
            host.endsWith("vimeo.com") -> uri.path.trim('/').takeIf { it.all(Char::isDigit) }?.let { "https://player.vimeo.com/video/$it" }
            else -> null
        }
    }.getOrNull()

private fun queryParam(query: String?, name: String): String? =
    query?.split('&')
        ?.mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        ?.firstOrNull { it.first == name }
        ?.second
        ?.takeIf { it.isNotBlank() }

private fun String.normalizedCategory(): String = trim().takeIf { it.isNotBlank() } ?: "Uncategorized"

private fun String.displayCategory(): String? = normalizedCategory().takeUnless { it.isUncategorized() }

private fun String.isUncategorized(): Boolean = normalizedCategory().equals("Uncategorized", ignoreCase = true)

private fun String.slug(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .takeIf { it.isNotBlank() }
        ?: "uncategorized"

private fun PhotographyMediaType.label(): String =
    when (this) {
        PhotographyMediaType.PHOTO -> "Photo"
        PhotographyMediaType.VIDEO -> "Video"
        PhotographyMediaType.VIDEO_360 -> "360 Video"
    }

private fun String?.normalizedMediaFilter(media: List<PhotographyPhoto>): String {
    val candidate = this?.trim().orEmpty().ifBlank { "all" }
    if (candidate == "all") return candidate
    val validTypes = PhotographyMediaType.entries.map { "type:${it.name}" }.toSet()
    val validCategories = media.map { "category:${it.category.normalizedCategory().slug()}" }.toSet()
    return candidate.takeIf { it in validTypes || it in validCategories } ?: "all"
}

private fun PhotographyPhoto.matchesMediaFilter(filter: String): Boolean = when {
    filter == "all" -> true
    filter.startsWith("type:") -> mediaType.name == filter.removePrefix("type:")
    filter.startsWith("category:") -> category.normalizedCategory().slug() == filter.removePrefix("category:")
    else -> true
}
