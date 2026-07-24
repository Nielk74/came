package com.nielk74.came.gallery

import org.junit.Assert.assertEquals
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
}
