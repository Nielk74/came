package com.nielk74.came.camera

import com.nielk74.came.filters.RenderStage

/** Everything a photograph passes through between the shutter and the library, in order. */
enum class CaptureStage {
    EXPOSING,
    READING,
    DEVELOPING,
    PRINTING,
    SKY,
    HALATION,
    GRAIN,
    SAVING,
    ;

    internal companion object {
        fun of(stage: RenderStage): CaptureStage = when (stage) {
            RenderStage.DEVELOP -> DEVELOPING
            RenderStage.PRINT -> PRINTING
            RenderStage.SKY -> SKY
            RenderStage.HALATION -> HALATION
            RenderStage.GRAIN -> GRAIN
        }
    }
}
