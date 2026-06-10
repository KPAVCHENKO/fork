package app.fork.messenger

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.drinkless.tdlib.TdApi

/** Превращает содержимое сообщений TDLib в человекочитаемый текст и форматирует время. */
object MessageFormat {

    fun contentText(content: TdApi.MessageContent?): String = when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> withCaption("🖼 Фото", content.caption)
        is TdApi.MessageVideo -> withCaption("🎬 Видео", content.caption)
        is TdApi.MessageVoiceNote -> "🎤 Голосовое сообщение"
        is TdApi.MessageVideoNote -> "📹 Видеосообщение"
        is TdApi.MessageSticker -> "${content.sticker.emoji} Стикер"
        is TdApi.MessageAnimation -> "🎞 GIF"
        is TdApi.MessageDocument -> withCaption("📎 ${content.document.fileName}", content.caption)
        is TdApi.MessageAudio -> "🎵 Аудио"
        is TdApi.MessageCall -> "📞 Звонок"
        is TdApi.MessageContact -> "👤 Контакт"
        is TdApi.MessageLocation -> "📍 Геопозиция"
        is TdApi.MessagePoll -> "📊 ${content.poll.question.text}"
        is TdApi.MessageAnimatedEmoji -> content.emoji
        null -> ""
        else -> "Сообщение"
    }

    private fun withCaption(label: String, caption: TdApi.FormattedText?): String =
        if (caption != null && caption.text.isNotBlank()) "$label, ${caption.text}" else label

    private val weekdays = arrayOf("вс", "пн", "вт", "ср", "чт", "пт", "сб")

    /** Время для списка чатов: сегодня — 14:05, на этой неделе — «вт», иначе — 03.06.26. */
    fun listTime(unixSeconds: Int): String {
        if (unixSeconds <= 0) return ""
        val then = Calendar.getInstance().apply { time = Date(unixSeconds * 1000L) }
        val now = Calendar.getInstance()
        val daysAgo = (now.timeInMillis - then.timeInMillis) / 86_400_000L
        return when {
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) ->
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(then.time)

            daysAgo < 7 -> weekdays[then.get(Calendar.DAY_OF_WEEK) - 1]

            else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(then.time)
        }
    }

    /** Время внутри пузыря сообщения. */
    fun bubbleTime(unixSeconds: Int): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(unixSeconds * 1000L))

    /** Инициалы для аватара-заглушки: «Иван Петров» -> «ИП». */
    fun initials(title: String): String =
        title.split(' ').filter { it.isNotBlank() }.take(2)
            .map { it.first().uppercaseChar() }.joinToString("")
            .ifBlank { "?" }
}
