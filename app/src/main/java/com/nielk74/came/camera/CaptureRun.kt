package com.nielk74.came.camera

import com.nielk74.came.filters.FilmProfile

/**
 * The photograph currently being made.
 *
 * Both the stock and the stage list are fixed when the shutter is released, so the viewfinder
 * reports the frame that is actually in the pipeline rather than whatever the film carousel has
 * moved on to since.
 */
data class CaptureRun(val profile: FilmProfile, val grainEnabled: Boolean) {
    /**
     * The stages this capture will report, in order.
     *
     * Halation and grain are conditional in the renderer, so a stock that authors no halation, or
     * a capture made with grain switched off, must not be shown a stage it will never reach.
     * `CaptureRunTest` holds this list against the stages the renderer really emits.
     */
    val stages: List<CaptureStage> = buildList {
        add(CaptureStage.EXPOSING)
        add(CaptureStage.READING)
        add(CaptureStage.DEVELOPING)
        add(CaptureStage.PRINTING)
        add(CaptureStage.SKY)
        if (profile.halation.enabled) add(CaptureStage.HALATION)
        if (grainEnabled && profile.grain.enabled) add(CaptureStage.GRAIN)
        add(CaptureStage.SAVING)
    }
}
