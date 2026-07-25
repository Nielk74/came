package com.nielk74.came.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.nielk74.came.filters.FilmProcessor
import com.nielk74.came.filters.FilmProfile
import com.nielk74.came.filters.RenderQuality
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Owns the capture path from CameraX to a fully rendered JPEG in MediaStore.
 *
 * CameraX first writes its JPEG to private cache so an incomplete or failed render can never
 * appear in the user's library. The selected film profile is applied at capture quality and the
 * finished bitmap is then published atomically to Pictures/camé.
 */
class CameraCaptureStore(private val context: Context) {
    /**
     * [onStage] is called as each stage begins, from whichever thread is doing the work, so it
     * must be safe to invoke off the main thread. Rendering a full-resolution frame is not quick
     * and is not meant to be; reporting progress is how that time is accounted for.
     */
    suspend fun capture(
        imageCapture: ImageCapture,
        profile: FilmProfile,
        grainEnabled: Boolean,
        onStage: (CaptureStage) -> Unit = {},
    ): Uri {
        val sourceFile = createSourceFile()
        try {
            onStage(CaptureStage.EXPOSING)
            takePicture(imageCapture, sourceFile)

            val rendered = withContext(Dispatchers.Default) {
                onStage(CaptureStage.READING)
                val source = decodeUpright(sourceFile)
                try {
                    FilmProcessor.apply(
                        source = source,
                        profile = profile,
                        grainEnabled = grainEnabled,
                        renderSeed = sourceFile.lastModified(),
                        quality = RenderQuality.CAPTURE,
                        onStage = { stage -> onStage(CaptureStage.of(stage)) },
                    ).also { output ->
                        if (output !== source) source.recycle()
                    }
                } catch (error: Throwable) {
                    source.recycle()
                    throw error
                }
            }

            return try {
                onStage(CaptureStage.SAVING)
                publish(rendered)
            } finally {
                rendered.recycle()
            }
        } finally {
            sourceFile.delete()
        }
    }

    private fun createSourceFile(): File {
        val directory = File(context.cacheDir, "camera-captures")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the private capture directory")
        }
        return File.createTempFile("came-source-", ".jpg", directory)
    }

    private suspend fun takePicture(
        imageCapture: ImageCapture,
        outputFile: File,
    ) = suspendCancellableCoroutine { continuation ->
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            },
        )
    }

    private fun decodeUpright(sourceFile: File): Bitmap {
        val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath)
            ?: throw IOException("CameraX produced an unreadable JPEG")
        val exif = ExifInterface(sourceFile)
        val rotation = exif.rotationDegrees
        val flipped = exif.isFlipped
        if (rotation == 0 && !flipped) return decoded

        val transform = Matrix().apply {
            if (flipped) postScale(-1f, 1f)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            transform,
            true,
        ).also { upright ->
            if (upright !== decoded) decoded.recycle()
        }
    }

    private suspend fun publish(bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val timestamp = System.currentTimeMillis()
        val name = "CAME_${FILE_TIMESTAMP.format(LocalDateTime.now())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "$PICTURES_DIRECTORY/$ALBUM_NAME")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val album = File(
                    Environment.getExternalStoragePublicDirectory(PICTURES_DIRECTORY),
                    ALBUM_NAME,
                )
                if (!album.exists() && !album.mkdirs()) {
                    throw IOException("Could not create Pictures/$ALBUM_NAME")
                }
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(album, name).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore refused the new photo")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw IOException("The rendered photo could not be encoded")
                }
            } ?: throw IOException("MediaStore could not open the new photo")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private companion object {
        const val ALBUM_NAME = "camé"
        const val JPEG_QUALITY = 96
        val PICTURES_DIRECTORY: String = Environment.DIRECTORY_PICTURES
        val FILE_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
    }
}
