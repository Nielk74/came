package com.nielk74.came.update

import com.nielk74.came.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigInteger

/** Resolves the newest installable APK across every page of the public GitHub release feed. */
class AppUpdateChecker(
    private val client: OkHttpClient = UpdateNetwork.createClient(),
    private val json: Json = UpdateNetwork.createJson(),
    private val repo: String = BuildConfig.GITHUB_REPO,
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun check(): UpdateStatus = withContext(ioDispatcher) {
        val release = try {
            fetchLatestInstallableRelease()
        } catch (error: IOException) {
            return@withContext UpdateStatus.Failed(error.message ?: "Update check failed")
        } catch (error: SerializationException) {
            return@withContext UpdateStatus.Failed(error.message ?: "Invalid update response")
        } catch (error: IllegalArgumentException) {
            return@withContext UpdateStatus.Failed(error.message ?: "Invalid update configuration")
        } ?: return@withContext UpdateStatus.Failed("No installable release found")

        if (isNewer(release.versionName, currentVersionName)) {
            UpdateStatus.Available(release)
        } else {
            UpdateStatus.UpToDate
        }
    }

    private fun fetchLatestInstallableRelease(): AppRelease? {
        var pageNumber = 1
        var latest: AppRelease? = null

        do {
            val page = fetchReleasePage(pageNumber)
            page.releases
                .mapNotNull { it.toAppRelease() }
                .forEach { candidate ->
                    val selected = latest
                    if (
                        selected == null ||
                        compareSemVer(candidate.versionName, selected.versionName) > 0
                    ) {
                        latest = candidate
                    }
                }
            pageNumber += 1
        } while (page.hasNext)

        return latest
    }

    private fun fetchReleasePage(pageNumber: Int): GithubReleasePage {
        val normalizedRepo = repo.trim().trim('/')
        require(RepoRegex.matches(normalizedRepo)) { "Invalid GitHub repository" }
        val request = Request.Builder()
            .url(
                "https://api.github.com/repos/$normalizedRepo/releases" +
                    "?per_page=$ReleasePageSize&page=$pageNumber",
            )
            .header("Accept", GithubJsonMediaType)
            .header("X-GitHub-Api-Version", GithubApiVersion)
            .header("User-Agent", userAgent())
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub returned ${response.code}")
            val body = response.body?.string()
                ?.takeIf(String::isNotBlank)
                ?: throw IOException("Empty response from GitHub")
            val releases = json.decodeFromString(
                ListSerializer(GithubReleaseDto.serializer()),
                body,
            )
            return GithubReleasePage(
                releases = releases,
                hasNext = response.header("Link").hasNextGithubPage(),
            )
        }
    }

    companion object {
        internal const val GithubApiVersion = "2022-11-28"
        internal const val GithubJsonMediaType = "application/vnd.github+json"
        private const val ReleasePageSize = 100
        private val RepoRegex = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        private val CoreIdentifierRegex = Regex("0|[1-9][0-9]*")
        private val PreReleaseIdentifierRegex = Regex("[0-9A-Za-z-]+")

        internal fun userAgent(): String = "came-android/${BuildConfig.VERSION_NAME}"

        /** GitHub pagination is authoritative: keep going only while `rel=next` is advertised. */
        internal fun String?.hasNextGithubPage(): Boolean =
            this
                ?.split(',')
                ?.any { link ->
                    link
                        .substringAfter('>', missingDelimiterValue = "")
                        .split(';')
                        .any { parameter ->
                            parameter.trim().removePrefix("rel=").trim('"')
                                .split(' ')
                                .any { relation -> relation == "next" }
                        }
                } == true

        /** Maps `v1.2.3` and the legacy `android-v1.2.3` form to `1.2.3`. */
        internal fun tagToVersion(tag: String): String =
            tag.trim().removePrefix("android-").removePrefix("v").trim()

        internal fun GithubReleaseDto.toAppRelease(): AppRelease? {
            if (draft || prerelease) return null
            val version = tagToVersion(tagName)
            if (SemanticVersion.parse(version) == null) return null

            val apk = assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    it.browserDownloadUrl.isNotBlank()
            } ?: return null
            val checksumName = "${apk.name}.sha256"
            val sha256Url = assets
                .firstOrNull {
                    it.name == checksumName && it.browserDownloadUrl.isNotBlank()
                }
                ?.browserDownloadUrl

            return AppRelease(
                versionName = version,
                tag = tagName,
                notes = body?.takeIf(String::isNotBlank)?.trim()
                    ?: name.orEmpty().trim(),
                apkUrl = apk.browserDownloadUrl,
                apkSizeBytes = apk.size,
                sha256Url = sha256Url,
                htmlUrl = htmlUrl,
            )
        }

        internal fun isNewer(candidate: String, current: String): Boolean {
            val candidateVersion = SemanticVersion.parse(candidate) ?: return false
            val currentVersion = SemanticVersion.parse(current) ?: return false
            return candidateVersion > currentVersion
        }

        internal fun compareSemVer(left: String, right: String): Int {
            val leftVersion = requireNotNull(SemanticVersion.parse(left)) {
                "Invalid semantic version: $left"
            }
            val rightVersion = requireNotNull(SemanticVersion.parse(right)) {
                "Invalid semantic version: $right"
            }
            return leftVersion.compareTo(rightVersion)
        }

        private data class SemanticVersion(
            val major: BigInteger,
            val minor: BigInteger,
            val patch: BigInteger,
            val preRelease: List<String>?,
        ) : Comparable<SemanticVersion> {
            override fun compareTo(other: SemanticVersion): Int {
                major.compareTo(other.major).takeIf { it != 0 }?.let { return it }
                minor.compareTo(other.minor).takeIf { it != 0 }?.let { return it }
                patch.compareTo(other.patch).takeIf { it != 0 }?.let { return it }

                val leftPreRelease = preRelease
                val rightPreRelease = other.preRelease
                if (leftPreRelease == null && rightPreRelease == null) return 0
                if (leftPreRelease == null) return 1
                if (rightPreRelease == null) return -1

                val sharedLength = minOf(leftPreRelease.size, rightPreRelease.size)
                for (index in 0 until sharedLength) {
                    val leftIdentifier = leftPreRelease[index]
                    val rightIdentifier = rightPreRelease[index]
                    val leftNumeric = leftIdentifier.all(Char::isDigit)
                    val rightNumeric = rightIdentifier.all(Char::isDigit)
                    val comparison = when {
                        leftNumeric && rightNumeric ->
                            leftIdentifier.toBigInteger().compareTo(rightIdentifier.toBigInteger())
                        leftNumeric -> -1
                        rightNumeric -> 1
                        else -> leftIdentifier.compareTo(rightIdentifier)
                    }
                    if (comparison != 0) return comparison
                }
                return leftPreRelease.size.compareTo(rightPreRelease.size)
            }

            companion object {
                fun parse(raw: String): SemanticVersion? {
                    val versionAndBuild = raw.trim().split('+', limit = 2)
                    val build = versionAndBuild.getOrNull(1)
                    if (
                        build != null &&
                        (build.isBlank() || build.split('.').any {
                            !PreReleaseIdentifierRegex.matches(it)
                        })
                    ) {
                        return null
                    }
                    val withoutBuild = versionAndBuild.first()
                    val coreAndPreRelease = withoutBuild.split('-', limit = 2)
                    val core = coreAndPreRelease.first().split('.')
                    if (core.size != 3 || core.any { !CoreIdentifierRegex.matches(it) }) {
                        return null
                    }

                    val preRelease = coreAndPreRelease.getOrNull(1)?.split('.')?.also { identifiers ->
                        if (
                            identifiers.isEmpty() ||
                            identifiers.any {
                                !PreReleaseIdentifierRegex.matches(it) ||
                                    (it.length > 1 && it.first() == '0' && it.all(Char::isDigit))
                            }
                        ) {
                            return null
                        }
                    }

                    return SemanticVersion(
                        major = core[0].toBigInteger(),
                        minor = core[1].toBigInteger(),
                        patch = core[2].toBigInteger(),
                        preRelease = preRelease,
                    )
                }
            }
        }
    }

    private data class GithubReleasePage(
        val releases: List<GithubReleaseDto>,
        val hasNext: Boolean,
    )
}
