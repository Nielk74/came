package com.nielk74.came.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryFormattingTest {
    @Test
    fun bytesAreReadableAtGalleryScale() {
        assertEquals("900 B", formatBytes(900))
        assertEquals("1.5 KB", formatBytes(1_536))
        assertEquals("2.0 MB", formatBytes(2L * 1024L * 1024L))
    }
}
