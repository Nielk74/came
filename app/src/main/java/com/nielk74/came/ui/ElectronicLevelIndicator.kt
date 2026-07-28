package com.nielk74.came.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nielk74.came.level.ElectronicLevelState
import kotlin.math.abs
import kotlin.math.roundToInt

private val LevelGreen = Color(0xFF62E88A)

/**
 * Compact viewfinder horizon. It draws nothing unless both the user setting and a live sensor
 * reading are present.
 */
@Composable
fun ElectronicLevelIndicator(
    state: ElectronicLevelState,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
) {
    if (!enabled || !state.isActive) return

    val lineColor by animateColorAsState(
        targetValue = if (state.isLevel) LevelGreen else Color.White,
        animationSpec = tween(durationMillis = 120),
        label = "electronic level color",
    )
    val direction = when {
        state.isLevel -> "level"
        state.rollDegrees < 0f -> "${abs(state.rollDegrees).roundToInt()} degrees left"
        else -> "${state.rollDegrees.roundToInt()} degrees right"
    }
    val visibleRoll = state.rollDegrees.coerceIn(-MAX_VISIBLE_ROLL_DEGREES, MAX_VISIBLE_ROLL_DEGREES)

    Canvas(
        modifier = modifier
            .size(width = 96.dp, height = 28.dp)
            .semantics {
                contentDescription = "Electronic level, $direction"
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val fixedGap = 33.dp.toPx()
        val fixedLength = 10.dp.toPx()
        val shadowWidth = 4.dp.toPx()
        val lineWidth = 2.dp.toPx()

        drawLine(
            color = Color.Black.copy(alpha = 0.58f),
            start = Offset(center.x - fixedGap - fixedLength, center.y),
            end = Offset(center.x - fixedGap, center.y),
            strokeWidth = shadowWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.58f),
            start = Offset(center.x + fixedGap, center.y),
            end = Offset(center.x + fixedGap + fixedLength, center.y),
            strokeWidth = shadowWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.72f),
            start = Offset(center.x - fixedGap - fixedLength, center.y),
            end = Offset(center.x - fixedGap, center.y),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.72f),
            start = Offset(center.x + fixedGap, center.y),
            end = Offset(center.x + fixedGap + fixedLength, center.y),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )

        rotate(degrees = -visibleRoll, pivot = center) {
            val liveHalfWidth = 25.dp.toPx()
            drawLine(
                color = Color.Black.copy(alpha = 0.72f),
                start = Offset(center.x - liveHalfWidth, center.y),
                end = Offset(center.x + liveHalfWidth, center.y),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(center.x - liveHalfWidth, center.y),
                end = Offset(center.x + liveHalfWidth, center.y),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val MAX_VISIBLE_ROLL_DEGREES = 35f
