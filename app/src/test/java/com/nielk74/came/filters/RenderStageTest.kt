package com.nielk74.came.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderStageTest {
    @Test
    fun aCaptureReportsEveryStageItRunsInOrder() {
        val stages = render(FilmCatalog.require("cinestill800t"), grainEnabled = true)
        assertEquals(
            listOf(
                RenderStage.DEVELOP,
                RenderStage.SKY,
                RenderStage.PRINT,
                RenderStage.HALATION,
                RenderStage.GRAIN,
            ),
            stages,
        )
    }

    @Test
    fun stagesThatDoNoWorkAreNotAnnounced() {
        // Ektar authors no halation, so a viewer should never be told it is being applied.
        val withoutHalation = render(FilmCatalog.require("ektar100"), grainEnabled = true)
        assertTrue(RenderStage.HALATION !in withoutHalation)
        assertTrue(RenderStage.GRAIN in withoutHalation)

        val withoutGrain = render(FilmCatalog.require("ektar100"), grainEnabled = false)
        assertTrue(RenderStage.GRAIN !in withoutGrain)
    }

    @Test
    fun aPreviewSkipsTheTextureStages() {
        val stages = render(
            FilmCatalog.require("cinestill800t"),
            grainEnabled = true,
            quality = RenderQuality.PREVIEW,
        )
        assertEquals(listOf(RenderStage.DEVELOP, RenderStage.SKY, RenderStage.PRINT), stages)
    }

    @Test
    fun everyRenderStageHasACaptureStage() {
        val mapped = RenderStage.entries.map(com.nielk74.came.camera.CaptureStage::of)
        assertEquals(RenderStage.entries.size, mapped.toSet().size)
    }

    private fun render(
        profile: FilmProfile,
        grainEnabled: Boolean,
        quality: RenderQuality = RenderQuality.CAPTURE,
    ): List<RenderStage> {
        val width = 48
        val height = 36
        val pixels = IntArray(width * height) { index ->
            val tone = 40 + (index % 160)
            -0x1000000 or (tone shl 16) or (tone shl 8) or tone
        }
        val seen = mutableListOf<RenderStage>()
        FilmProcessor.render(
            pixels, width, height, profile,
            grainEnabled = grainEnabled,
            renderSeed = 4L,
            quality = quality,
            onStage = seen::add,
        )
        return seen
    }
}
