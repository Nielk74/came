package com.nielk74.came.update

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.nielk74.came.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Downloads, optionally checksum-verifies, and hands a release APK to Android's installer. */
class ApkDownloader(
    context: Context,
    private val client: OkHttpClient = UpdateNetwork.createClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val context = context.applicationContext
    private val authority = "${BuildConfig.APPLICATION_ID}.files"

    fun downloadAndInstall(release: AppRelease): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))
        val target = apkFile(release.versionName)
        target.delete()

        try {
            download(release, target) { fraction ->
                emit(DownloadState.Downloading(fraction))
            }

            emit(DownloadState.Verifying)
            release.sha256Url?.let { checksumUrl ->
                val expected = fetchExpectedSha256(checksumUrl)
                if (!matchesSha256(target, expected)) {
                    target.delete()
                    emit(DownloadState.Failed("Checksum mismatch - download rejected"))
                    return@flow
                }
            }

            installApk(target)
            emit(DownloadState.ReadyToInstall)
        } catch (error: IOException) {
            target.delete()
            emit(DownloadState.Failed(error.message ?: "Download failed"))
        } catch (error: ActivityNotFoundException) {
            target.delete()
            emit(DownloadState.Failed("Android's package installer is unavailable"))
        } catch (error: SecurityException) {
            target.delete()
            emit(DownloadState.Failed(error.message ?: "Installation permission was denied"))
        }
    }.flowOn(ioDispatcher)

    private suspend fun download(
        release: AppRelease,
        target: File,
        onProgress: suspend (Float) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        val request = githubAssetRequest(release.apkUrl)

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
            val body = response.body ?: throw IOException("Download returned an empty response")
            val totalBytes = release.apkSizeBytes.takeIf { it > 0 } ?: body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BufferSizeBytes)
                    var downloadedBytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        if (totalBytes > 0) {
                            onProgress(
                                (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            }
            onProgress(1f)
        }
    }

    private fun fetchExpectedSha256(url: String): String {
        return client.newCall(githubAssetRequest(url)).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Checksum download failed (${response.code})")
            }
            val body = response.body ?: throw IOException("Checksum response was empty")
            parseExpectedSha256(body.string())
                ?: throw IOException("Published checksum is invalid")
        }
    }

    private fun installApk(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(context, authority, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ApkMimeType)
            clipData = ClipData.newRawUri("came update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun apkFile(versionName: String): File {
        val directory = File(context.cacheDir, "updates")
        val safeVersion = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(directory, "came-$safeVersion.apk")
    }

    internal companion object {
        const val ApkMimeType = "application/vnd.android.package-archive"
        const val BufferSizeBytes = 64 * 1024
        private val Sha256Regex = Regex("[A-Fa-f0-9]{64}")

        internal fun parseExpectedSha256(body: String): String? = body
            .lineSequence()
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
            ?.split(Regex("\\s+"), limit = 2)
            ?.firstOrNull()
            ?.takeIf(Sha256Regex::matches)

        internal fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BufferSizeBytes)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        internal fun matchesSha256(file: File, expected: String): Boolean =
            sha256Of(file).equals(expected, ignoreCase = true)

        internal fun githubAssetRequest(url: String): Request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("X-GitHub-Api-Version", AppUpdateChecker.GithubApiVersion)
            .header("User-Agent", AppUpdateChecker.userAgent())
            .build()
    }
}
