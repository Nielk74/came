package com.nielk74.came.update

import com.nielk74.came.update.AppUpdateChecker.Companion.hasNextGithubPage
import com.nielk74.came.update.AppUpdateChecker.Companion.isNewer
import com.nielk74.came.update.AppUpdateChecker.Companion.tagToVersion
import com.nielk74.came.update.AppUpdateChecker.Companion.toAppRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun `release tags are normalized`() {
        assertEquals("1.2.3", tagToVersion("v1.2.3"))
        assertEquals("1.2.3", tagToVersion("1.2.3"))
        assertEquals("1.2.3", tagToVersion("android-v1.2.3"))
    }

    @Test
    fun `semantic versions follow numeric and prerelease precedence`() {
        assertTrue(isNewer("0.10.0", "0.9.9"))
        assertTrue(isNewer("1.0.0", "1.0.0-rc.10"))
        assertTrue(isNewer("1.0.0-rc.10", "1.0.0-rc.2"))
        assertTrue(isNewer("1.0.0-beta", "1.0.0-12"))
        assertFalse(isNewer("1.0.0-rc.1", "1.0.0"))
        assertFalse(isNewer("not-a-version", "1.0.0"))
        assertFalse(isNewer("1.0.1+invalid+build", "1.0.0"))
    }

    @Test
    fun `release resolves apk and only its exact checksum asset`() {
        val dto = installableRelease(
            version = "1.2.3",
            assets = listOf(
                GithubAssetDto(
                    "app-release.apk.SHA256",
                    82,
                    "https://example.com/wrong-case.sha256",
                ),
                GithubAssetDto(
                    "another.apk.sha256",
                    82,
                    "https://example.com/another.apk.sha256",
                ),
                GithubAssetDto(
                    "app-release.apk",
                    12_345,
                    "https://example.com/app-release.apk",
                ),
                GithubAssetDto(
                    "app-release.apk.sha256",
                    82,
                    "https://example.com/app-release.apk.sha256",
                ),
            ),
        )

        val release = dto.toAppRelease()

        checkNotNull(release)
        assertEquals("1.2.3", release.versionName)
        assertEquals(12_345, release.apkSizeBytes)
        assertEquals("https://example.com/app-release.apk.sha256", release.sha256Url)
    }

    @Test
    fun `draft prerelease invalid semver and apk-less releases are ignored`() {
        val release = installableRelease("1.2.3")

        assertNull(release.copy(draft = true).toAppRelease())
        assertNull(release.copy(prerelease = true).toAppRelease())
        assertNull(release.copy(tagName = "release-next").toAppRelease())
        assertNull(release.copy(assets = emptyList()).toAppRelease())
    }

    @Test
    fun `link parser recognizes next among multiple relations`() {
        assertTrue(
            "<https://api.github.com/page=2>; rel=\"next last\"".hasNextGithubPage(),
        )
        assertFalse("<https://api.github.com/page=1>; rel=\"prev\"".hasNextGithubPage())
        assertFalse(null.hasNextGithubPage())
    }

    @Test
    fun `checker scans all advertised pages and selects highest installable semver`() = runBlocking {
        val requestedPages = mutableListOf<Int>()
        val client = githubClient(
            pages = mapOf(
                1 to
                    """
                    [
                      {"tag_name":"v9.0.0","assets":[]},
                      {
                        "tag_name":"v1.9.0",
                        "assets":[{
                          "name":"came.apk",
                          "size":100,
                          "browser_download_url":"https://example.com/1.9.0.apk"
                        }]
                      }
                    ]
                    """.trimIndent(),
                2 to
                    """
                    [{
                      "tag_name":"v1.10.0",
                      "assets":[{
                        "name":"came.apk",
                        "size":200,
                        "browser_download_url":"https://example.com/1.10.0.apk"
                      }]
                    }]
                    """.trimIndent(),
            ),
            requestedPages = requestedPages,
        )

        val status = checker(client).check()

        assertTrue(status is UpdateStatus.Available)
        assertEquals("1.10.0", (status as UpdateStatus.Available).release.versionName)
        assertEquals(listOf(1, 2), requestedPages)
    }

    @Test
    fun `checker sends public GitHub API headers`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals(AppUpdateChecker.GithubJsonMediaType, request.header("Accept"))
                assertEquals(AppUpdateChecker.GithubApiVersion, request.header("X-GitHub-Api-Version"))
                assertTrue(request.header("User-Agent")!!.startsWith("came-android/"))
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        assertTrue(checker(client).check() is UpdateStatus.Failed)
    }

    private fun checker(client: OkHttpClient): AppUpdateChecker = AppUpdateChecker(
        client = client,
        json = Json { ignoreUnknownKeys = true },
        repo = "Nielk74/came",
        currentVersionName = "1.0.0",
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun githubClient(
        pages: Map<Int, String>,
        requestedPages: MutableList<Int>,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/repos/Nielk74/came/releases", request.url.encodedPath)
            assertEquals("100", request.url.queryParameter("per_page"))
            val pageNumber = checkNotNull(request.url.queryParameter("page")?.toInt())
            requestedPages += pageNumber
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    checkNotNull(pages[pageNumber])
                        .toResponseBody("application/json".toMediaType()),
                )
            if (pages.containsKey(pageNumber + 1)) {
                response.header(
                    "Link",
                    "<https://api.github.com/repos/Nielk74/came/releases" +
                        "?per_page=100&page=${pageNumber + 1}>; rel=\"next\"",
                )
            }
            response.build()
        }
        .build()

    private fun installableRelease(
        version: String,
        assets: List<GithubAssetDto> = listOf(
            GithubAssetDto("came.apk", 123, "https://example.com/came.apk"),
        ),
    ) = GithubReleaseDto(
        tagName = "v$version",
        name = "came $version",
        body = "Release notes",
        htmlUrl = "https://github.com/Nielk74/came/releases/tag/v$version",
        assets = assets,
    )
}
