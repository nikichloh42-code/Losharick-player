package com.example.musicplayer

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.io.File
import java.io.FileOutputStream

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val added: Long,
    val track: Int
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    /** Название с учётом правок пользователя */
    val name: String get() = Store.overrides[id]?.title?.takeIf { it.isNotBlank() } ?: title

    /** Исполнитель с учётом правок пользователя */
    val who: String get() = Store.overrides[id]?.artist?.takeIf { it.isNotBlank() } ?: artist
}

/** Переводы: tr("English", "Español", "Русский") */
fun tr(en: String, es: String, ru: String): String = when (Store.lang) {
    "es" -> es
    "ru" -> ru
    else -> en
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun fmtHours(ms: Long): String {
    val m = ms / 60000
    val h = m / 60
    val mm = m % 60
    return if (h > 0) tr("$h h $mm min", "$h h $mm min", "$h ч $mm мин")
    else tr("$mm min", "$mm min", "$mm мин")
}

object Library {
    fun scan(ctx: Context): List<Song> {
        val out = ArrayList<Song>()
        
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM
        )
            ${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION}
        try {
            ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val artist = c.getString(2)
                    out.add(
                        Song(
                            id = c.getLong(0),
                            title = c.getString(1) ?: "Unknown",
                            artist = if (artist == null || artist == "<unknown>") "Unknown" else artist,
                            album = c.getString(3) ?: "Unknown",
                            albumId = c.getLong(4),
                            duration = c.getLong(5),
                            added = c.getLong(6),
                            track = c.getInt(7) % 1000
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}

fun decodeSampled(bytes: ByteArray, max: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var s = 1
    while (o.outWidth / (s * 2) >= max && o.outHeight / (s * 2) >= max) s *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
}

/** Копирует выбранную картинку в память приложения (уменьшая её) */
fun importImage(ctx: Context, src: Uri, dest: File, max: Int): Boolean {
    return try {
        val bytes = ctx.contentResolver.openInputStream(src)?.use { it.readBytes() } ?: return false
        val bmp = decodeSampled(bytes, max) ?: return false
        dest.parentFile?.mkdirs()
        FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    } catch (e: Exception) {
        false
    }
}

object Covers {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(ctx: Context, s: Song, px: Int, coverPath: String?): Bitmap? {
        val key = "${s.id}:$px:$coverPath"
        cache.get(key)?.let { return it }
        var b: Bitmap? = null
        if (coverPath != null) {
            b = try {
                val f = File(coverPath)
                if (f.exists()) decodeSampled(f.readBytes(), px) else null
            } catch (_: Exception) {
                null
            }
        }
        if (b == null) {
            b = if (px <= 512) {
                thumb(ctx, s, px) ?: embedded(ctx, s, px)
            } else {
                embedded(ctx, s, px) ?: thumb(ctx, s, px)
            }
        }
        if (b != null) cache.put(key, b)
        return b
    }

    private fun embedded(ctx: Context, s: Song, px: Int): Bitmap? {
        return try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, s.uri)
                r.embeddedPicture?.let { decodeSampled(it, px) }
            } finally {
                r.release()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun thumb(ctx: Context, s: Song, px: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                ctx.contentResolver.loadThumbnail(s.uri, Size(px, px), null)
            } else {
                val u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.albumId)
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) {
            null
        }
    }
}

object Wall {
    private var rev = -1
    private var bmp: Bitmap? = null

    fun get(ctx: Context, r: Int): Bitmap? {
        if (r == rev) return bmp
        val f = File(ctx.filesDir, "wallpaper.jpg")
        bmp = try {
            if (f.exists()) decodeSampled(f.readBytes(), 1600) else null
        } catch (_: Exception) {
            null
        }
        rev = r
        return bmp
    }
}
