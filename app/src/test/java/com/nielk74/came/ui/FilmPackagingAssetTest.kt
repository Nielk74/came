package com.nielk74.came.ui

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class FilmPackagingAssetTest {
    @Test
    fun portra400UsesTheApprovedFullBleedCrop() {
        val bytes = File(
            "src/main/res/drawable-nodpi/film_packaging_portra400.jpg",
        ).readBytes()
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

        assertEquals(
            "2758db504bb7ece20d4e332bf4cfc3fbfbb4840c4c5e6373be3fddd7d72b902d",
            sha256,
        )
    }
}
