package code.yousef.portfolio.ui.sections

import code.yousef.portfolio.i18n.LocalizedText
import code.yousef.portfolio.i18n.PortfolioLocale
import code.yousef.portfolio.theme.PortfolioTheme
import code.yousef.portfolio.ui.foundation.ContentSection
import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.components.display.Text
import codes.yousef.summon.components.layout.Column
import codes.yousef.summon.extensions.rem
import codes.yousef.summon.modifier.*
import codes.yousef.summon.modifier.LayoutModifiers.flexDirection
import codes.yousef.summon.modifier.LayoutModifiers.gap
import codes.yousef.summon.modifier.StylingModifiers.fontWeight
import codes.yousef.summon.modifier.StylingModifiers.lineHeight

@Composable
fun AboutMeSection(
    locale: PortfolioLocale,
    modifier: Modifier = Modifier()
) {
    ContentSection(modifier = modifier) {
        Column(
            modifier = Modifier()
                .display(Display.Flex)
                .flexDirection(FlexDirection.Column)
                .gap(PortfolioTheme.Spacing.md)
        ) {
            Text(
                text = AboutMeCopy.title.resolve(locale),
                modifier = Modifier()
                    .fontSize(2.5.rem)
                    .fontWeight(700)
            )
            Text(
                text = AboutMeCopy.body.resolve(locale),
                modifier = Modifier()
                    .color(PortfolioTheme.Colors.TEXT_SECONDARY)
                    .lineHeight(1.8)
                    .fontSize(1.1.rem)
            )
        }
    }
}

private object AboutMeCopy {
    val title = LocalizedText(
        en = "The 'One Developer' Philosophy",
        ar = "فلسفة 'المطور الواحد'"
    )
    val body = LocalizedText(
        en = "Modern software has become overly complex. I believe in radical simplification. By using unified languages like Kotlin across the stack, I eliminate the friction between 'backend' and 'frontend' teams. One developer, one language, one codebase — from server logic to mobile UI to web applications. This approach dramatically reduces coordination overhead, eliminates translation layers, and delivers faster, more cohesive products.",
        ar = "أصبحت البرمجيات الحديثة معقدة بشكل مفرط. أؤمن بالتبسيط الجذري. باستخدام لغات موحدة مثل Kotlin عبر كل الطبقات، أزيل الاحتكاك بين فرق 'الخادم' و'الواجهة'. مطور واحد، لغة واحدة، قاعدة كود واحدة — من منطق الخادم إلى واجهة الهاتف إلى تطبيقات الويب. هذا النهج يقلل بشكل كبير من تكاليف التنسيق، ويزيل طبقات الترجمة، ويقدم منتجات أسرع وأكثر تماسكًا."
    )
}
