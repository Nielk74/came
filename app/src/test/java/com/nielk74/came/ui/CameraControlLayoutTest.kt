package com.nielk74.came.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraControlLayoutTest {
    @Test
    fun cameraCarouselLeavesASquareSlotForPackagingArt() {
        val availableHeight =
            FILM_CAROUSEL_CARD_HEIGHT - FILM_CAROUSEL_VERTICAL_PADDING * 2

        assertEquals(FILM_CAROUSEL_THUMBNAIL_SIZE, availableHeight)
    }

    @Test
    fun exposureControlOccupiesAFullSlotAboveSettings() {
        val expectedHeight = CAMERA_CONTROL_SIZE * 2 + CAMERA_CONTROL_GAP

        assertEquals(expectedHeight, CAMERA_RIGHT_RAIL_HEIGHT)
    }
}
