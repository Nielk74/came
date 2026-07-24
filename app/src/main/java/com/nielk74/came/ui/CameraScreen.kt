package com.nielk74.came.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nielk74.came.camera.CameraSession
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Minimal full-bleed camera surface. No chrome is shown until the first vertical swipe. */
@Composable
fun CameraScreen(
    cameraSession: CameraSession,
    selectedFilterName: String,
    previewColorMatrix: FloatArray,
    previewTintTop: Long,
    previewTintBottom: Long,
    countdownSeconds: Int?,
    isCapturing: Boolean,
    captureFeedbackKey: Int,
    statusMessage: String?,
    onFilterStep: (Int) -> Unit,
    onCapture: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var controlsEpoch by remember { mutableIntStateOf(0) }
    var flashVisible by remember { mutableStateOf(false) }
    val dragThreshold = with(LocalDensity.current) { 54.dp.toPx() }

    LaunchedEffect(controlsEpoch) {
        if (controlsEpoch == 0) return@LaunchedEffect
        delay(CONTROLS_VISIBLE_MILLIS)
        controlsVisible = false
    }
    LaunchedEffect(captureFeedbackKey) {
        if (captureFeedbackKey == 0) return@LaunchedEffect
        flashVisible = true
        delay(CAPTURE_FLASH_MILLIS)
        flashVisible = false
    }

    val rootGestures = Modifier
        .pointerInput(onCapture, onFilterStep, dragThreshold) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var verticalDistance = 0f
                var horizontalDistance = 0f
                var draggedVertically = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.positionChange()
                    verticalDistance += delta.y
                    horizontalDistance += delta.x
                    if (
                        abs(verticalDistance) > viewConfiguration.touchSlop &&
                        abs(verticalDistance) > abs(horizontalDistance)
                    ) {
                        draggedVertically = true
                        change.consume()
                    }
                    if (!change.pressed) break
                }

                if (draggedVertically && abs(verticalDistance) >= dragThreshold) {
                    // Moving the finger upward advances through the film roll.
                    onFilterStep(if (verticalDistance < 0f) 1 else -1)
                    controlsVisible = true
                    controlsEpoch++
                } else if (
                    abs(verticalDistance) < viewConfiguration.touchSlop &&
                    abs(horizontalDistance) < viewConfiguration.touchSlop
                ) {
                    onCapture()
                }
            }
        }
        .semantics(mergeDescendants = false) {
            contentDescription = "Camera viewfinder"
            role = Role.Button
            onClick(label = "Take photo") {
                onCapture()
                true
            }
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(rootGestures),
    ) {
        FilteredPreview(
            cameraSession = cameraSession,
            colorMatrix = previewColorMatrix,
            modifier = Modifier.fillMaxSize(),
        )
        FilmPreviewTint(
            top = previewTintTop,
            bottom = previewTintBottom,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 22.dp, vertical = 30.dp),
            enter = fadeIn(tween(220)) + slideInVertically(tween(320)) { it / 2 },
            exit = fadeOut(tween(260)) + slideOutVertically(tween(260)) { it / 3 },
        ) {
            FilterControls(
                filterName = selectedFilterName,
                visible = controlsVisible,
                onOpenSettings = onOpenSettings,
            )
        }

        if (countdownSeconds != null) {
            Countdown(number = countdownSeconds, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = isCapturing && countdownSeconds == null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.46f),
                shape = CircleShape,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        }

        AnimatedVisibility(
            visible = statusMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 28.dp, vertical = 34.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(100),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = statusMessage.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }

        if (flashVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.8f)),
            )
        }
    }
}

@Composable
private fun FilteredPreview(
    cameraSession: CameraSession,
    colorMatrix: FloatArray,
    modifier: Modifier = Modifier,
) {
    // Keep CameraX's compatibility path uninterrupted while profile changes update the lightweight
    // tint above it. The complete matrix, tone response, halation, and grain run on the saved frame.
    require(colorMatrix.size == COLOR_MATRIX_SIZE)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(cameraSession) {
        onDispose(cameraSession::unbind)
    }
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                post { cameraSession.bind(lifecycleOwner, this) }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun FilmPreviewTint(top: Long, bottom: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(top).copy(alpha = 0.045f),
                    Color.Transparent,
                    Color(bottom).copy(alpha = 0.035f),
                ),
            ),
        ),
    )
}

@Composable
private fun FilterControls(
    filterName: String,
    visible: Boolean,
    onOpenSettings: () -> Unit,
) {
    val gearRotation = remember { Animatable(82f) }
    LaunchedEffect(visible) {
        gearRotation.animateTo(
            targetValue = if (visible) 0f else 72f,
            animationSpec = tween(420),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.62f),
            shape = RoundedCornerShape(100),
        ) {
            Text(
                text = filterName,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
        Surface(
            color = Color.Black.copy(alpha = 0.62f),
            contentColor = Color.White,
            shape = CircleShape,
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(46.dp)
                    .semantics { role = Role.Button },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Open camera settings",
                    modifier = Modifier
                        .size(21.dp)
                        .rotate(gearRotation.value),
                )
            }
        }
    }
}

@Composable
private fun Countdown(number: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Assertive
            contentDescription = "Photo in $number"
        },
        color = Color.Black.copy(alpha = 0.42f),
        shape = CircleShape,
    ) {
        Text(
            text = number.toString(),
            color = Color.White,
            fontSize = 64.sp,
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 16.dp),
        )
    }
}

@Composable
fun CameraPermissionScreen(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111111)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (permanentlyDenied) {
                    "Camera access is off. Enable it in Android settings to use camé."
                } else {
                    "camé needs the camera to frame and take photographs."
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = onRequestPermission,
                color = Color.White,
                contentColor = Color.Black,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = if (permanentlyDenied) "Open app settings" else "Allow camera",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp),
                )
            }
        }
    }
}

private const val COLOR_MATRIX_SIZE = 20
private const val CONTROLS_VISIBLE_MILLIS = 2_600L
private const val CAPTURE_FLASH_MILLIS = 95L
