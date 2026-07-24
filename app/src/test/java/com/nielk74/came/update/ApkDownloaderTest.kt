package com.nielk74.came.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkDownloaderTest {
    @Test
    fun `checksum parser accepts sha256sum output`() {
        val hash = "a".repeat(64)

        assertEquals(hash, ApkDownloader.parseExpectedSha256("$hash  came.apk\n"))
        assertEquals(hash, ApkDownloader.parseExpectedSha256("$hash\tcame.apk\n"))
    }

    @Test
    fun `checksum parser rejects missing or malformed hashes`() {
        assertNull(ApkDownloader.parseExpectedSha256(""))
        assertNull(ApkDownloader.parseExpectedSha256("abc  came.apk"))
        assertNull(ApkDownloader.parseExpectedSha256("g".repeat(64)))
    }

    @Test
    fun `sha256 hashes the complete file`() {
        val file = File.createTempFile("came-checksum", ".bin")
        try {
            file.writeText("came")
            assertEquals(
                "76806e2c372db5358a574ba14d2264b6238c24ce6ad5d364c4c12cb1b87dde98",
                ApkDownloader.sha256Of(file),
            )
            assertTrue(
                ApkDownloader.matchesSha256(
                    file,
                    "76806E2C372DB5358A574BA14D2264B6238C24CE6AD5D364C4C12CB1B87DDE98",
                ),
            )
            assertFalse(ApkDownloader.matchesSha256(file, "0".repeat(64)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `asset requests include GitHub media headers`() {
        val request = ApkDownloader.githubAssetRequest("https://github.com/example/came.apk")

        assertEquals("application/octet-stream", request.header("Accept"))
        assertEquals(AppUpdateChecker.GithubApiVersion, request.header("X-GitHub-Api-Version"))
        assertTrue(request.header("User-Agent")!!.startsWith("came-android/"))
    }
}
