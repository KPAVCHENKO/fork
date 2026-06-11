package app.fork.messenger

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.drinkless.tdlib.TdApi

/**
 * Превращает FormattedText TDLib в AnnotatedString: жирный/курсив/подчёркнутый/
 * зачёркнутый/моноширинный/цитаты, кликабельные ссылки и спойлеры
 * (скрыты заливкой, тап раскрывает все спойлеры сообщения).
 *
 * Смещения entities TDLib — в UTF-16, как и индексы Kotlin-строк.
 */
fun TdApi.FormattedText.toAnnotated(
    linkColor: Color,
    codeBackground: Color,
    spoilerHidden: Color,
    spoilersRevealed: Boolean,
    onRevealSpoilers: () -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val length = text.length
    entities?.forEach { entity ->
        val start = entity.offset.coerceIn(0, length)
        val end = (entity.offset + entity.length).coerceIn(start, length)
        if (start >= end) return@forEach
        when (val type = entity.type) {
            is TdApi.TextEntityTypeBold ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)

            is TdApi.TextEntityTypeItalic ->
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)

            is TdApi.TextEntityTypeUnderline ->
                addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)

            is TdApi.TextEntityTypeStrikethrough ->
                addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)

            is TdApi.TextEntityTypeCode, is TdApi.TextEntityTypePre, is TdApi.TextEntityTypePreCode ->
                addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                    start, end,
                )

            is TdApi.TextEntityTypeBlockQuote, is TdApi.TextEntityTypeExpandableBlockQuote ->
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)

            is TdApi.TextEntityTypeTextUrl -> addLink(
                LinkAnnotation.Url(
                    type.url,
                    TextLinkStyles(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    ),
                ),
                start, end,
            )

            is TdApi.TextEntityTypeUrl -> addLink(
                LinkAnnotation.Url(
                    text.substring(start, end),
                    TextLinkStyles(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    ),
                ),
                start, end,
            )

            is TdApi.TextEntityTypeMention, is TdApi.TextEntityTypeMentionName,
            is TdApi.TextEntityTypeHashtag, is TdApi.TextEntityTypeCashtag,
            is TdApi.TextEntityTypeBotCommand,
            ->
                addStyle(SpanStyle(color = linkColor), start, end)

            is TdApi.TextEntityTypeSpoiler -> {
                if (spoilersRevealed) {
                    addStyle(SpanStyle(background = spoilerHidden.copy(alpha = 0.18f)), start, end)
                } else {
                    // Текст прячется под заливкой; тап по спойлеру раскрывает.
                    addStyle(
                        SpanStyle(color = Color.Transparent, background = spoilerHidden),
                        start, end,
                    )
                    addLink(
                        LinkAnnotation.Clickable("spoiler", TextLinkStyles()) { onRevealSpoilers() },
                        start, end,
                    )
                }
            }

            else -> Unit
        }
    }
}
