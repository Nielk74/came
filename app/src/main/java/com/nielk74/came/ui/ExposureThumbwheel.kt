package com.nielk74.came.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nielk74.came.camera.ExposureValue
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * A compact five-detent exposure wheel intended to appear transiently over the viewfinder.
 *
 * A swipe rolls through EV -2, -1, 0, +1, and +2; every settled detent produces one gentle haptic.
 * Neighbouring marks remain visible, so the control reads as a physical wheel rather than a menu.
 * Tapping any visible mark moves it under the red index line.
 */
@Composable
fun ExposureThumbwheel(
    value: ExposureValue,
    onValueChange: (ExposureValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val values = ExposureValue.entries
    val selectedIndex = values.indexOf(value).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { values.size }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentCallback by rememberUpdatedState(onValueChange)

    LaunchedEffect(selectedIndex) {
        if (pagerState.settledPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val selected = values.getOrNull(page) ?: return@collect
                if (selected == currentValue) return@collect
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentCallback(selected)
            }
    }

    Surface(
        color = CamePalette.Overlay.copy(alpha = .94f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .18f)),
        modifier = modifier
            .widthIn(min = 260.dp, max = 340.dp)
            .graphicsLayer { alpha = if (enabled) 1f else .52f }
            .clearAndSetSemantics {
                contentDescription = "Exposure compensation thumbwheel"
                stateDescription = value.accessibilityLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.ev.toFloat(),
                    range = -2f..2f,
                    steps = 3,
                )
                if (enabled) {
                    setProgress { requested ->
                        val snapped = ExposureValue.closestTo(requested)
                        if (snapped != value) onValueChange(snapped)
                        true
                    }
                } else {
                    disabled()
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "EXPOSURE  EV ${value.label}",
                color = Color.White.copy(alpha = .88f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
            ) {
                val pageWidth = 54.dp
                val sidePadding = ((maxWidth - pageWidth) / 2).coerceAtLeast(0.dp)
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(pageWidth),
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    beyondViewportPageCount = 2,
                    userScrollEnabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val exposure = values[page]
                    val offset = (
                        (pagerState.currentPage - page) +
                            pagerState.currentPageOffsetFraction
                        ).absoluteValue.coerceIn(0f, 1f)
                    val selected = offset < .5f
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .graphicsLayer {
                                alpha = 1f - offset * .54f
                                scaleX = 1f - offset * .12f
                                scaleY = 1f - offset * .12f
                            }
                            .clickable(enabled = enabled) {
                                scope.launch { pagerState.animateScrollToPage(page) }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .padding(top = 7.dp)
                                .width(if (selected) 2.dp else 1.dp)
                                .height(if (selected) 20.dp else 12.dp)
                                .background(
                                    if (selected) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = .64f)
                                    },
                                ),
                        )
                        Text(
                            text = exposure.label,
                            color = Color.White,
                            fontSize = if (selected) 16.sp else 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .height(6.dp)
                        .background(CamePalette.Accent),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(46.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(CamePalette.Overlay, Color.Transparent),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(46.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, CamePalette.Overlay),
                            ),
                        ),
                )
            }
        }
    }
}
