package com.nielk74.came.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nielk74.came.camera.CameraLens
import com.nielk74.came.camera.CameraSession
import com.nielk74.came.camera.CompositionZoom
import com.nielk74.came.camera.CaptureRun
import com.nielk74.came.camera.CaptureStage
import com.nielk74.came.filters.FilmProfile
import com.nielk74.came.level.rememberElectronicLevelState
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** A camera-first surface with explicit shutter, focus, film carousel, library, and settings. */
// statusBarsIgnoringVisibility is the only way to reserve a strip camé deliberately keeps empty of
// system bars; it is still marked experimental.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraScreen(
    cameraSession: CameraSession,
    profiles: List<FilmProfile>,
    selectedProfileId: String,
    compositionZoom: CompositionZoom,
    electronicLevelEnabled: Boolean,
    timerSeconds: Int,
    countdownSeconds: Int?,
    isCapturing: Boolean,
    captureStage: CaptureStage?,
    captureRun: CaptureRun?,
    captureFeedbackKey: Int,
    statusMessage: String?,
    latestThumbnail: ImageBitmap?,
    onCompositionZoomChanged: (CompositionZoom) -> Unit,
    onFilterSelected: (String) -> Unit,
    onCapture: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()
    val haptics = LocalHapticFeedback.current
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusEpoch by remember { mutableIntStateOf(0) }
    var focusVisible by remember { mutableStateOf(false) }
    var focusSuccessful by remember { mutableStateOf<Boolean?>(null) }
    var captureShadeVisible by remember { mutableStateOf(false) }
    var previewStreaming by remember { mutableStateOf(false) }
    var lensStatusMessage by remember { mutableStateOf<String?>(null) }
    var exposureWheelVisible by remember { mutableStateOf(false) }
    var exposureWheelEpoch by remember { mutableIntStateOf(0) }
    var viewfinderCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val availableLenses by cameraSession.availableLenses.collectAsStateWithLifecycle()
    val selectedLens by cameraSession.selectedLens.collectAsStateWithLifecycle()
    val isLensSwitching by cameraSession.isLensSwitching.collectAsStateWithLifecycle()
    val exposureState by cameraSession.exposureControlState.collectAsStateWithLifecycle()
    val levelState by rememberElectronicLevelState(enabled = electronicLevelEnabled)
    val currentZoom by rememberUpdatedState(compositionZoom)
    val currentOnZoomChanged by rememberUpdatedState(onCompositionZoomChanged)
    val currentIsCapturing by rememberUpdatedState(isCapturing)
    val zoomGestureState = rememberTransformableState { zoomChange, _, _ ->
        if (!currentIsCapturing) {
            val nextZoom = currentZoom.scaledBy(zoomChange)
            if (nextZoom != currentZoom) currentOnZoomChanged(nextZoom)
        }
    }

    LaunchedEffect(focusEpoch) {
        if (focusEpoch == 0) return@LaunchedEffect
        focusVisible = true
        delay(FOCUS_VISIBLE_MILLIS)
        focusVisible = false
    }
    LaunchedEffect(captureFeedbackKey) {
        if (captureFeedbackKey == 0) return@LaunchedEffect
        captureShadeVisible = true
        delay(CAPTURE_FEEDBACK_MILLIS)
        captureShadeVisible = false
    }
    LaunchedEffect(lensStatusMessage) {
        if (lensStatusMessage == null) return@LaunchedEffect
        delay(LENS_STATUS_VISIBLE_MILLIS)
        lensStatusMessage = null
    }
    LaunchedEffect(exposureWheelEpoch) {
        if (exposureWheelEpoch == 0) return@LaunchedEffect
        delay(EXPOSURE_WHEEL_VISIBLE_MILLIS)
        exposureWheelVisible = false
    }
    val displayedStatus = statusMessage ?: lensStatusMessage

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        FilteredPreview(
            cameraSession = cameraSession,
            colorMatrix = selectedProfile.previewColorMatrix,
            onFocus = { point ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                focusPoint = viewfinderCoordinates?.localToRoot(point) ?: point
                focusSuccessful = null
                focusEpoch++
                cameraSession.focusAt(point.x, point.y) { successful ->
                    focusSuccessful = successful
                }
            },
            onStreamState = { previewStreaming = it },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(VIEWFINDER_ASPECT_RATIO)
                .onGloballyPositioned { viewfinderCoordinates = it }
                .graphicsLayer {
                    scaleX = compositionZoom.factor
                    scaleY = compositionZoom.factor
                }
                .transformable(zoomGestureState),
        )

        AnimatedVisibility(
            visible = !previewStreaming,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "STARTING CAMERA",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                // camé hides the system bars, but they come back transiently on a swipe and the
                // frame may carry a cutout, so the badges keep clear of that strip either way.
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ViewfinderPill {
                Icon(
                    Icons.Rounded.FlashOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "FLASH OFF",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
            }
            AnimatedVisibility(
                visible = timerSeconds > 0,
                enter = fadeIn() + scaleIn(initialScale = .9f),
                exit = fadeOut() + scaleOut(targetScale = .9f),
            ) {
                ViewfinderPill {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "TIMER ${timerSeconds}S",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = exposureState.isSupported,
            enter = fadeIn() + scaleIn(initialScale = .9f),
            exit = fadeOut() + scaleOut(targetScale = .9f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            ViewfinderPill(
                modifier = Modifier
                    .clickable(enabled = !isCapturing) {
                        exposureWheelVisible = true
                        exposureWheelEpoch++
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription =
                            "Exposure ${exposureState.selectedValue.accessibilityLabel}. Open thumbwheel."
                    },
            ) {
                Text(
                    text = "EV ${exposureState.selectedValue.label}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
            }
        }

        AnimatedVisibility(
            visible = compositionZoom.factor > CompositionZoom.MIN_FACTOR,
            enter = fadeIn() + scaleIn(initialScale = .9f),
            exit = fadeOut() + scaleOut(targetScale = .9f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(top = 14.dp),
        ) {
            ViewfinderPill {
                Text(
                    text = "${formatCompositionZoom(compositionZoom.factor)}× CROP",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
            }
        }

        ElectronicLevelIndicator(
            state = levelState,
            enabled = electronicLevelEnabled,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-46).dp),
        )

        FocusReticle(
            point = focusPoint,
            visible = focusVisible,
            successful = focusSuccessful,
        )

        // A tap on the viewfinder outside the wheel closes it. The camera controls are composed
        // above this layer and dismiss explicitly, so a single tap can still open settings,
        // capture, switch stock, or open the library.
        if (exposureWheelVisible && exposureState.isSupported) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(exposureWheelVisible) {
                        detectTapGestures { exposureWheelVisible = false }
                    },
            )
        }

        CameraControls(
            profiles = profiles,
            selectedProfileId = selectedProfile.id,
            latestThumbnail = latestThumbnail,
            isCapturing = isCapturing,
            lenses = availableLenses,
            selectedLens = selectedLens,
            isLensSwitching = isLensSwitching,
            onFilterSelected = { filterId ->
                exposureWheelVisible = false
                onFilterSelected(filterId)
            },
            onLensSelected = { lens ->
                exposureWheelVisible = false
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCompositionZoomChanged(CompositionZoom.Identity)
                cameraSession.selectLens(lens) { successful ->
                    if (!successful) {
                        lensStatusMessage = "${lens.ratioLabel} camera view unavailable"
                    }
                }
            },
            onCapture = {
                exposureWheelVisible = false
                onCapture()
            },
            onOpenGallery = {
                exposureWheelVisible = false
                onOpenGallery()
            },
            onOpenSettings = {
                exposureWheelVisible = false
                onOpenSettings()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = exposureWheelVisible && exposureState.isSupported,
            enter = fadeIn() + scaleIn(initialScale = .94f),
            exit = fadeOut() + scaleOut(targetScale = .94f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 104.dp),
        ) {
            ExposureThumbwheel(
                value = exposureState.selectedValue,
                enabled = !isCapturing && !exposureState.isApplying,
                onValueChange = { value ->
                    exposureWheelEpoch++
                    cameraSession.setExposureCompensation(value) { successful ->
                        if (!successful) lensStatusMessage = "Exposure compensation unavailable"
                    }
                },
            )
        }

        // Above the controls, so the scrim really does cover the whole viewfinder: the film
        // carousel is the one control the shutter state does not already disable, and a stock
        // chosen mid-render would not be the stock in the pipeline.
        AnimatedVisibility(
            visible = captureStage != null && countdownSeconds == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            CaptureProgress(stage = captureStage, run = captureRun)
        }

        if (countdownSeconds != null) {
            Countdown(number = countdownSeconds, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = displayedStatus != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 28.dp, vertical = 60.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.76f),
                shape = RoundedCornerShape(100),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = displayedStatus.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }

        if (captureShadeVisible) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        }
    }
}

/** Small translucent badge for persistent viewfinder state (flash, self timer). */
@Composable
private fun ViewfinderPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = .55f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/**
 * Names the stage the capture is on, with the pipeline that capture will run laid out as a trail
 * of dots.
 *
 * Rendering a full-resolution frame through the whole pipeline is not instant, and the app would
 * rather say what it is doing than cut the work short to feel quicker. The stage and the run are
 * both retained while the indicator fades out, so it never blanks mid-animation. A dim scrim
 * swallows stray taps while the photograph is being made.
 */
@Composable
private fun CaptureProgress(stage: CaptureStage?, run: CaptureRun?) {
    var lastStage by remember { mutableStateOf(stage) }
    if (stage != null) lastStage = stage
    var lastRun by remember { mutableStateOf(run) }
    if (run != null) lastRun = run
    // The run is set before the first stage is reported and cleared with the last, so this only
    // holds before the first capture of a session.
    val reported = lastRun ?: return
    val label = when (lastStage) {
        CaptureStage.EXPOSING -> "EXPOSING"
        CaptureStage.READING -> "READING THE FRAME"
        CaptureStage.DEVELOPING -> "DEVELOPING"
        CaptureStage.SKY -> "RECOVERING SKY"
        CaptureStage.PRINTING -> "PRINTING ${reported.profile.displayName.uppercase()}"
        CaptureStage.HALATION -> "HALATION"
        CaptureStage.GRAIN -> "GRAIN"
        CaptureStage.SAVING -> "SAVING"
        null -> ""
    }
    val currentIndex = reported.stages.indexOf(lastStage)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .48f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = CamePalette.Overlay.copy(alpha = .94f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .15f)),
            // Stage names differ in length, so the card would jump width as they change.
            modifier = Modifier.animateContentSize(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilmPackagingThumbnail(
                        profile = reported.profile,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    reported.stages.forEachIndexed { index, _ ->
                        val color = when {
                            index < currentIndex -> Color.White.copy(alpha = .9f)
                            index == currentIndex -> CamePalette.Accent
                            else -> Color.White.copy(alpha = .18f)
                        }
                        Box(Modifier.size(5.dp).background(color, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilteredPreview(
    cameraSession: CameraSession,
    colorMatrix: FloatArray,
    onFocus: (Offset) -> Unit,
    onStreamState: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(colorMatrix.size == COLOR_MATRIX_SIZE)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(cameraSession, lifecycleOwner) {
        onDispose(cameraSession::unbind)
    }
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // TextureView is required so the GPU color effect is part of the live frame.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                // Fit, never fill: filling a tall screen with a 4:3 frame crops the sides away, so
                // the viewfinder showed a tighter picture than the one that got saved. Letterboxing
                // against the black backdrop keeps what you frame and what you get identical.
                scaleType = PreviewView.ScaleType.FIT_CENTER
                cameraSession.bind(lifecycleOwner, this, onStreamState)
            }
        },
        update = { cameraSession.setPreviewColorMatrix(colorMatrix) },
        modifier = modifier
            .semantics {
                contentDescription = "Camera viewfinder. Tap to focus; pinch to crop zoom."
            }
            .pointerInput(cameraSession) {
                detectTapGestures(onTap = onFocus)
            },
    )
}

@Composable
private fun CameraControls(
    profiles: List<FilmProfile>,
    selectedProfileId: String,
    latestThumbnail: ImageBitmap?,
    isCapturing: Boolean,
    lenses: List<CameraLens>,
    selectedLens: CameraLens,
    isLensSwitching: Boolean,
    onFilterSelected: (String) -> Unit,
    onLensSelected: (CameraLens) -> Unit,
    onCapture: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .72f), Color.Black.copy(alpha = .96f)),
                ),
            )
            .padding(top = 42.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilmCarousel(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onFilterSelected = onFilterSelected,
        )
        AnimatedVisibility(
            visible = lenses.size > 1,
            enter = fadeIn() + scaleIn(initialScale = .9f),
            exit = fadeOut() + scaleOut(targetScale = .9f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                LensSelector(
                    lenses = lenses,
                    selectedLens = selectedLens,
                    enabled = !isCapturing && !isLensSwitching,
                    switching = isLensSwitching,
                    onLensSelected = onLensSelected,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundControl(onClick = onOpenGallery, description = "Open photo library") {
                AnimatedContent(
                    targetState = latestThumbnail,
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(initialScale = .8f, animationSpec = tween(240))) togetherWith
                            fadeOut(tween(120))
                    },
                    label = "thumbnail",
                    modifier = Modifier.fillMaxSize(),
                ) { bitmap ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
            ShutterButton(enabled = !isCapturing && !isLensSwitching, onClick = onCapture)
            RoundControl(onClick = onOpenSettings, description = "Open camera settings") {
                Icon(Icons.Rounded.Settings, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun LensSelector(
    lenses: List<CameraLens>,
    selectedLens: CameraLens,
    enabled: Boolean,
    switching: Boolean,
    onLensSelected: (CameraLens) -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = .66f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .22f)),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .horizontalScroll(rememberScrollState())
                .padding(3.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            lenses.forEach { lens ->
                val selected = lens == selectedLens
                val background by animateColorAsState(
                    if (selected) Color(0xFFF4F2EB) else Color.Transparent,
                    tween(180),
                    label = "lens-background",
                )
                val content by animateColorAsState(
                    if (selected) Color.Black else Color.White,
                    tween(180),
                    label = "lens-content",
                )
                val scale by animateFloatAsState(
                    if (selected) 1f else .94f,
                    tween(180),
                    label = "lens-scale",
                )
                Column(
                    modifier = Modifier
                        .widthIn(min = 64.dp)
                        .heightIn(min = 48.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(background)
                        .clearAndSetSemantics {
                            contentDescription = lens.accessibilityLabel
                            role = Role.RadioButton
                            this.selected = selected
                            focused = false
                            if (enabled) {
                                onClick { onLensSelected(lens); true }
                            } else {
                                disabled()
                            }
                        }
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onLensSelected(lens) },
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = lens.ratioLabel,
                        color = content.copy(alpha = if (switching && selected) .62f else 1f),
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = lens.roleLabel,
                        color = content.copy(alpha = if (selected) .66f else .62f),
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .45.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilmCarousel(
    profiles: List<FilmProfile>,
    selectedProfileId: String,
    onFilterSelected: (String) -> Unit,
) {
    val selectedIndex = profiles.indexOfFirst { it.id == selectedProfileId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { profiles.size }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(selectedProfileId, profiles.size) {
        if (pagerState.settledPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    val currentSelection by rememberUpdatedState(selectedProfileId)
    LaunchedEffect(pagerState, profiles) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val profile = profiles.getOrNull(page) ?: return@collect
                // A settle that only catches the carousel up with the current selection is not a
                // gesture: the page the pager opens on, and the scroll that follows a selection
                // restored from storage, must be neither felt nor written back. Without this the
                // detent fired on every launch and on every return from the menu or the library,
                // since the camera screen is composed afresh each time.
                if (profile.id == currentSelection) return@collect
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onFilterSelected(profile.id)
            }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(218.dp),
            contentPadding = PaddingValues(horizontal = 70.dp),
            pageSpacing = 10.dp,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val profile = profiles[page]
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .absoluteValue
                .coerceIn(0f, 1f)
            val selected = offset < .5f
            Surface(
                onClick = {
                    // Tapping a neighbouring card glides it into the centre; tapping the centred
                    // card confirms the stock already under it.
                    if (page == pagerState.currentPage) {
                        onFilterSelected(profile.id)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                },
                color = Color.Black.copy(alpha = .72f),
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .height(58.dp)
                    .graphicsLayer {
                        alpha = 1f - offset * .46f
                        scaleX = 1f - offset * .08f
                        scaleY = 1f - offset * .08f
                    }
                    .border(
                        width = if (selected) 1.dp else .5.dp,
                        color = if (selected) Color.White.copy(alpha = .8f) else Color.White.copy(alpha = .18f),
                        shape = RoundedCornerShape(9.dp),
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilmPackagingThumbnail(
                        profile = profile,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(targetState = profile.displayName, label = "film-name") { name ->
                            Text(
                                text = name.uppercase(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = .8.sp,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "${page + 1} / ${profiles.size}",
                            color = Color.White.copy(alpha = .55f),
                            fontSize = 9.sp,
                            letterSpacing = .7.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            profiles.forEachIndexed { index, _ ->
                val active = index == pagerState.currentPage
                val width by animateDpAsState(if (active) 13.dp else 4.dp, tween(180), label = "film-dot")
                Box(
                    Modifier
                        .height(4.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(if (active) Color.White else Color.White.copy(alpha = .28f)),
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val inset by animateDpAsState(if (pressed) 8.dp else 5.dp, tween(90), label = "shutter-press")
    Box(
        modifier = Modifier
            .size(82.dp)
            .clip(CircleShape)
            .border(3.dp, Color.White, CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics { contentDescription = "Take photo" }
            .padding(inset)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = .35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!enabled) {
            CircularProgressIndicator(modifier = Modifier.size(25.dp), color = Color.Black, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun RoundControl(
    onClick: () -> Unit,
    description: String,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = .66f))
            .border(1.dp, Color.White.copy(alpha = .34f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun FocusReticle(point: Offset?, visible: Boolean, successful: Boolean?) {
    val density = LocalDensity.current
    val sizePx = with(density) { 62.dp.toPx() }
    val scale by animateFloatAsState(if (visible) 1f else 1.22f, tween(180), label = "focus-scale")
    AnimatedVisibility(
        visible = visible && point != null,
        enter = fadeIn(tween(100)) + scaleIn(initialScale = 1.35f, animationSpec = tween(180)),
        exit = fadeOut(tween(240)) + scaleOut(targetScale = .86f, animationSpec = tween(240)),
        modifier = Modifier.offset {
            val target = point ?: Offset.Zero
            IntOffset((target.x - sizePx / 2).roundToInt(), (target.y - sizePx / 2).roundToInt())
        },
    ) {
        val color = when (successful) {
            true -> Color(0xFF73D68A)
            false -> Color(0xFFFFC14F)
            null -> Color.White
        }
        Box(
            modifier = Modifier
                .size(62.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(1.5.dp, color, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(4.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun Countdown(number: Int, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(number) {
        scale.snapTo(1.28f)
        scale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }
    Surface(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Assertive
            contentDescription = "Photo in $number"
        },
        color = Color.Black.copy(alpha = 0.56f),
        shape = CircleShape,
    ) {
        Text(
            text = number.toString(),
            color = Color.White,
            fontSize = 64.sp,
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier
                .padding(horizontal = 30.dp, vertical = 16.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
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
        modifier = modifier.fillMaxSize().background(CamePalette.Panel),
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

internal fun formatCompositionZoom(factor: Float): String {
    val tenths = (CompositionZoom.of(factor).factor * 10f).roundToInt()
    return if (tenths % 10 == 0) {
        (tenths / 10).toString()
    } else {
        "${tenths / 10}.${tenths % 10}"
    }
}

private const val COLOR_MATRIX_SIZE = 20
private const val VIEWFINDER_ASPECT_RATIO = 3f / 4f
private const val FOCUS_VISIBLE_MILLIS = 1_050L
private const val CAPTURE_FEEDBACK_MILLIS = 72L
private const val LENS_STATUS_VISIBLE_MILLIS = 2_000L
private const val EXPOSURE_WHEEL_VISIBLE_MILLIS = 4_500L
