package app.fork.messenger

import android.content.ContentUris
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.MediaStore

/** Недавние фото/видео из галереи устройства — для ленты в панели вложений (как в TG). */
fun queryRecentMedia(context: Context, limit: Int = 40): List<Uri> {
    val uris = ArrayList<Uri>()
    runCatching {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sort = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (c.moveToNext() && uris.size < limit) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                uris.add(ContentUris.withAppendedId(base, id))
            }
        }
    }
    return uris
}

/** Копирует выбранный документ в кэш с сохранением имени — для отправки файлом. */
fun copyDocument(context: Context, uri: Uri): java.io.File? = runCatching {
    var name = "file"
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { name = it } }
    val out = java.io.File(context.cacheDir, "doc_${System.currentTimeMillis()}_$name")
    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { input.copyTo(it) }
    } ?: return null
    out
}.getOrNull()

/** Последняя известная геопозиция (без активного запроса GPS) — для кнопки «Геопозиция». */
fun lastKnownLocation(context: Context): Pair<Double, Double>? = runCatching {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    var best: Location? = null
    for (p in lm.getProviders(true)) {
        @Suppress("MissingPermission")
        val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
        if (best == null || l.accuracy < best!!.accuracy) best = l
    }
    best?.let { it.latitude to it.longitude }
}.getOrNull()
