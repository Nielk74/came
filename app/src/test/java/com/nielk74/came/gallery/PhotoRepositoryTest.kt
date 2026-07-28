package com.nielk74.came.gallery

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoRepositoryTest {
    @Test
    fun dateAddedRepairsMissingCameraTimestamp() {
        assertEquals(1_725_000_000_000L, resolvedDateMillis(0L, 1_725_000_000L))
        assertEquals(1_726_000_000_123L, resolvedDateMillis(1_726_000_000_123L, 1L))
    }

    @Test
    fun bitmapSamplingUsesPowerOfTwoBounds() {
        assertEquals(1, calculateInSampleSize(1_392, 1_856, 2_560))
        assertEquals(2, calculateInSampleSize(4_000, 3_000, 1_200))
        assertEquals(4, calculateInSampleSize(8_000, 6_000, 1_200))
    }

    @Test
    fun deletedMediaDuringThumbnailRefreshBecomesAnEmptyResult() {
        assertNull(
            missingMediaAsNull<String> {
                throw FileNotFoundException("Photo was deleted after the refresh started")
            },
        )
        assertNull(
            missingMediaAsNull<String> {
                throw SecurityException("Photo permission was revoked")
            },
        )
    }

    @Test
    fun unrelatedThumbnailFailuresStillPropagate() {
        assertThrows(IllegalStateException::class.java) {
            missingMediaAsNull<String> { error("Decoder invariant failed") }
        }
    }
}
