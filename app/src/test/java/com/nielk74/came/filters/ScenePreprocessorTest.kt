package com.nielk74.came.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePreprocessorTest {
    @Test
    fun flatSceneExpandsUsefulTonalRange() {
        val pixels = grayscaleRamp(from = 64, to = 192, count = 256)

        ScenePreprocessor.apply(pixels, width = pixels.size, height = 1)

        assertTrue(channel(pixels.first()) < 58)
        assertTrue(channel(pixels.last()) > 198)
        assertTrue((1 until pixels.size).all { index ->
            channel(pixels[index - 1]) <= channel(pixels[index])
        })
    }

    @Test
    fun fullRangeSceneKeepsMidpointAndEndpointsStable() {
        val pixels = grayscaleRamp(from = 0, to = 255, count = 256)

        ScenePreprocessor.apply(pixels, width = pixels.size, height = 1)

        assertEquals(0, channel(pixels.first()))
        assertEquals(255, channel(pixels.last()))
        assertTrue(channel(pixels[128]) in 122..136)
    }

    @Test
    fun uniformDarkSceneIsNotMistakenForAFlatHistogram() {
        val pixels = IntArray(64) { argb(40, 40, 40) }

        ScenePreprocessor.apply(pixels, width = 8, height = 8)

        assertTrue(channel(pixels.first()) in 28..55)
        assertTrue(pixels.all { it == pixels.first() })
    }

    @Test
    fun neutralAndHueRelationshipsArePreserved() {
        val neutral = IntArray(16) { argb(128, 128, 128) }
        ScenePreprocessor.apply(neutral, width = 4, height = 4)
        neutral.forEach { color ->
            assertEquals(color ushr 16 and 0xff, color ushr 8 and 0xff)
            assertEquals(color ushr 8 and 0xff, color and 0xff)
        }

        val warm = IntArray(16) { argb(170, 120, 75) }
        ScenePreprocessor.apply(warm, width = 4, height = 4)
        warm.forEach { color ->
            assertTrue((color ushr 16 and 0xff) > (color ushr 8 and 0xff))
            assertTrue((color ushr 8 and 0xff) > (color and 0xff))
        }
    }

    @Test
    fun histogramAnalysisStaysBoundedForLargeCaptures() {
        val pixels = IntArray(1_000_000) { index ->
            val value = index and 0xff
            argb(value, value, value)
        }

        val analysis = ScenePreprocessor.analyze(pixels)

        assertTrue(analysis.histogramSamples <= 65_536)
        assertTrue(analysis.histogramSamples > 60_000)
    }

    @Test
    fun strongEdgeDoesNotCreateAVisibleLocalContrastHalo() {
        val width = 480
        val height = 480
        val pixels = IntArray(width * height) { index ->
            val value = if (index % width < width / 2) 80 else 180
            argb(value, value, value)
        }

        ScenePreprocessor.apply(pixels, width, height)

        val centerRow = height / 2 * width
        val darkReference = channel(pixels[centerRow + width / 4])
        val brightReference = channel(pixels[centerRow + width * 3 / 4])
        val darkEdge = channel(pixels[centerRow + width / 2 - 1])
        val brightEdge = channel(pixels[centerRow + width / 2])
        assertTrue(darkEdge in (darkReference - 3)..(darkReference + 3))
        assertTrue(brightEdge in (brightReference - 3)..(brightReference + 3))
    }

    @Test
    fun exposureCurveDoesNotJumpWhenBimodalPopulationCrossesHalf() {
        val count = 65_536
        val balanced = IntArray(count) { index ->
            val value = if (index < count / 2) 80 else 180
            argb(value, value, value)
        }
        val darkMajority = balanced.copyOf().also { it[count / 2] = argb(80, 80, 80) }

        val balancedAnalysis = ScenePreprocessor.analyze(balanced)
        val shiftedAnalysis = ScenePreprocessor.analyze(darkMajority)

        assertTrue(kotlin.math.abs(balancedAnalysis.gamma - shiftedAnalysis.gamma) < .001f)
        assertTrue(
            kotlin.math.abs(balancedAnalysis.sourceMidtone - shiftedAnalysis.sourceMidtone) < .001f,
        )
    }

    private fun grayscaleRamp(from: Int, to: Int, count: Int): IntArray = IntArray(count) { index ->
        val value = from + ((to - from) * index.toFloat() / (count - 1)).toInt()
        argb(value, value, value)
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        -0x1000000 or (red shl 16) or (green shl 8) or blue

    private fun channel(color: Int): Int = color and 0xff
}
