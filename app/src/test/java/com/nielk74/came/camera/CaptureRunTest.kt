package com.nielk74.came.camera

import com.nielk74.came.filters.FilmCatalog
import com.nielk74.came.filters.FilmProcessor
import com.nielk74.came.filters.FilmProfile
import com.nielk74.came.filters.RenderQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRunTest {
    @Test
    fun theAnnouncedStagesAreTheStagesEveryStockReallyRuns() {
        // The viewfinder lays the run out as a trail of dots before the render starts, so a stage
        // it promises and never reaches would leave a dot dark for the whole capture.
        for (profile in FilmCatalog.profiles) {
            for (grainEnabled in listOf(true, false)) {
                assertEquals(
                    "${profile.id}, grain=$grainEnabled",
                    rendered(profile, grainEnabled),
                    CaptureRun(profile, grainEnabled).stages,
                )
            }
        }
    }

    @Test
    fun aStockWithoutHalationIsNeverPromisedIt() {
        val ektar = FilmCatalog.require("ektar100")
        assertTrue(CaptureStage.HALATION !in CaptureRun(ektar, grainEnabled = true).stages)
        assertTrue(CaptureStage.GRAIN in CaptureRun(ektar, grainEnabled = true).stages)
        assertTrue(CaptureStage.GRAIN !in CaptureRun(ektar, grainEnabled = false).stages)
    }

    @Test
    fun everyRunOpensOnTheShutterAndClosesOnTheSave() {
        for (profile in FilmCatalog.profiles) {
            val stages = CaptureRun(profile, grainEnabled = true).stages
            assertEquals(CaptureStage.EXPOSING, stages.first())
            assertEquals(CaptureStage.SAVING, stages.last())
        }
    }

    /** The stages a real capture of [profile] reports, in order. */
    private fun rendered(profile: FilmProfile, grainEnabled: Boolean): List<CaptureStage> {
        val width = 48
        val height = 36
        val pixels = IntArray(width * height) { index ->
            val tone = 40 + (index % 160)
            -0x1000000 or (tone shl 16) or (tone shl 8) or tone
        }
        val seen = mutableListOf<CaptureStage>()
        seen += CaptureStage.EXPOSING
        seen += CaptureStage.READING
        FilmProcessor.render(
            pixels, width, height, profile,
            grainEnabled = grainEnabled,
            renderSeed = 4L,
            quality = RenderQuality.CAPTURE,
            onStage = { stage -> seen += CaptureStage.of(stage) },
        )
        seen += CaptureStage.SAVING
        return seen
    }
}
