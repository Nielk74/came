package com.nielk74.came.gallery

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhotoItem(
    val uri: Uri,
    val name: String,
    val dateTakenMillis: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

/** MediaStore access scoped to photographs created in Pictures/camé. */
class PhotoRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val bitmapCache = object : LruCache<String, Bitmap>(CACHE_KILOBYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    suspend fun loadPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
        )
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%/$ALBUM_NAME/%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Images.Media.DATA} LIKE ?" to
                arrayOf("%/${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME/%")
        }

        buildList {
            resolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (cursor.moveToNext()) {
                    add(
                        PhotoItem(
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn)),
                            name = cursor.getString(nameColumn).orEmpty(),
                            dateTakenMillis = resolvedDateMillis(
                                dateTakenMillis = cursor.getLong(dateColumn),
                                dateAddedSeconds = cursor.getLong(dateAddedColumn),
                            ),
                            width = cursor.getInt(widthColumn),
                            height = cursor.getInt(heightColumn),
                            sizeBytes = cursor.getLong(sizeColumn),
                        ),
                    )
                }
            }
        }
    }

    suspend fun loadBitmap(uri: Uri, maxDimension: Int): Bitmap? = withContext(Dispatchers.IO) {
        require(maxDimension > 0)
        val key = "$uri@$maxDimension"
        bitmapCache.get(key)?.let { return@withContext it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return@withContext null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null
        bitmapCache.put(key, decoded)
        decoded
    }

    suspend fun delete(photo: PhotoItem): Boolean = withContext(Dispatchers.IO) {
        val deleted = resolver.delete(photo.uri, null, null) > 0
        if (deleted) {
            val prefix = photo.uri.toString()
            bitmapCache.snapshot().keys.filter { it.startsWith(prefix) }.forEach(bitmapCache::remove)
        }
        deleted
    }

    suspend fun requireReadable(photo: PhotoItem) = withContext(Dispatchers.IO) {
        resolver.openFileDescriptor(photo.uri, "r")?.close()
            ?: throw IOException("The photograph is no longer available")
    }

    private companion object {
        const val ALBUM_NAME = "camé"
        const val CACHE_KILOBYTES = 48 * 1024
    }
}

internal fun resolvedDateMillis(dateTakenMillis: Long, dateAddedSeconds: Long): Long =
    dateTakenMillis.takeIf { it > 0L } ?: dateAddedSeconds.coerceAtLeast(0L) * 1_000L

internal fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    require(width > 0 && height > 0 && maxDimension > 0)
    var sample = 1
    while (width / (sample * 2) >= maxDimension || height / (sample * 2) >= maxDimension) {
        sample *= 2
    }
    return sample
}
