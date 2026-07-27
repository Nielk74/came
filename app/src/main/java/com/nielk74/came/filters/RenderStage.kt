package com.nielk74.came.filters

/**
 * The passes a capture goes through, in the order [FilmProcessor] runs them.
 *
 * Rendering a full-resolution frame through the whole pipeline takes real time, and the honest
 * answer to that is to say what is happening rather than to cut the work short. [FilmProcessor]
 * reports each stage as it begins so a caller can show its progress.
 */
enum class RenderStage {
    /** Scene development: levels, exposure, and local tonal separation. */
    DEVELOP,

    /** The stock's own response: negative and print curves, cross-talk, split tone, foliage. */
    PRINT,

    /** Sky brightness and colour, worked on the print so the stock cannot take it back out. */
    SKY,

    /** Red-orange scatter around bright edges. */
    HALATION,

    /** Film-plane crystal structure. */
    GRAIN,
}
