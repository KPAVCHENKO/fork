package app.fork.messenger

import androidx.compose.foundation.text.appendInlineContent
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

/** Prefix for inline custom-emoji placeholders in the inlineContent map. */
const val CUSTOM_EMOJI_PREFIX = "ce_"

/** Custom/premium emoji ids referenced by this text (for building inlineContent). */
fun TdApi.FormattedText.customEmojiIds(): List<Long> =
    entities.orEmpty().mapNotNull { (it.type as? TdApi.TextEntityTypeCustomEmoji)?.customEmojiId }

/**
 * Превращает FormattedText TDLib в AnnotatedString: жирный/курсив/подчёркнутый/
 * зачёркнутый/моноширинный/цитаты, кликабельные ссылки, спойлеры и ВСТРОЕННЫЕ
 * кастом/премиум-эмодзи (заменяются inline-плейсхолдерами "ce_<id>").
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
    val length = text.length
    // Custom-emoji ranges replace their text with one inline placeholder each.
    val custom = entities.orEmpty().mapNotNull { e ->
        val t = e.type as? TdApi.TextEntityTypeCustomEmoji ?: return@mapNotNull null
        val s = e.offset.coerceIn(0, length)
        val en = (e.offset + e.length).coerceIn(s, length)
        if (s < en) Triple(s, en, t.customEmojiId) else null
    }.sortedBy { it.first }

    var pos = 0
    for ((s, en, id) in custom) {
        if (s > pos) append(text.substring(pos, s))
        appendInlineContent("$CUSTOM_EMOJI_PREFIX$id", text.substring(s, en))
        pos = en
    }
    if (pos < length) append(text.substring(pos))

    // Maps an original text offset to the built-string offset (inline placeholders
    // collapse each custom range to a single char).
    fun mapOff(orig: Int): Int {
        var shift = 0
        for ((s, en, _) in custom) {
            when {
                en <= orig -> shift += (en - s) - 1
                s < orig -> return s - shift
            }
        }
        return orig - shift
    }

    entities?.forEach { entity ->
        if (entity.type is TdApi.TextEntityTypeCustomEmoji) return@forEach
        val start = mapOff(entity.offset.coerceIn(0, length))
        val end = mapOff((entity.offset + entity.length).coerceIn(0, length))
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

            is TdApi.TextEntityTypeUrl -> {
                val os = entity.offset.coerceIn(0, length)
                val oe = (entity.offset + entity.length).coerceIn(os, length)
                addLink(
                    LinkAnnotation.Url(
                        text.substring(os, oe),
                        TextLinkStyles(
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        ),
                    ),
                    start, end,
                )
            }

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
