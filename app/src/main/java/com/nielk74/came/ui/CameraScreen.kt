package com.nielk74.came.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import com.nielk74.came.filters.FilmProfile
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/** A camera-first surface with explicit shutter, focus, film carousel, library, and settings. */
@Composable
fun CameraScreen(
    cameraSession: CameraSession,
    profiles: List<FilmProfile>,
    selectedProfileId: String,
    countdownSeconds: Int?,
    isCapturing: Boolean,
    captureFeedbackKey: Int,
    statusMessage: String?,
    latestThumbnail: ImageBitmap?,
    onFilterSelected: (String) -> Unit,
    onCapture: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusEpoch by remember { mutableIntStateOf(0) }
    var focusVisible by remember { mutableStateOf(false) }
    var focusSuccessful by remember { mutableStateOf<Boolean?>(null) }
    var captureShadeVisible by remember { mutableStateOf(false) }
    var previewStreaming by remember { mutableStateOf(false) }
    var lensStatusMessage by remember { mutableStateOf<String?>(null) }
    val availableLenses by cameraSession.availableLenses.collectAsStateWithLifecycle()
    val selectedLens by cameraSession.selectedLens.collectAsStateWithLifecycle()
    val isLensSwitching by cameraSession.isLensSwitching.collectAsStateWithLifecycle()

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
                focusPoint = point
                focusSuccessful = null
                focusEpoch++
                cameraSession.focusAt(point.x, point.y) { successful ->
                    focusSuccessful = successful
                }
            },
            onStreamState = { previewStreaming = it },
            modifier = Modifier.fillMaxSize(),
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
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(Icons.Rounded.FlashOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(
                text = "FLASH OFF",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
        }

        FocusReticle(
            point = focusPoint,
            visible = focusVisible,
            successful = focusSuccessful,
        )

        CameraControls(
            profiles = profiles,
            selectedProfileId = selectedProfile.id,
            latestThumbnail = latestThumbnail,
            isCapturing = isCapturing,
            lenses = availableLenses,
            selectedLens = selectedLens,
            isLensSwitching = isLensSwitching,
            onFilterSelected = onFilterSelected,
            onLensSelected = { lens ->
                cameraSession.selectLens(lens) { successful ->
                    if (!successful) {
                        lensStatusMessage = "${lens.ratioLabel} camera view unavailable"
                    }
                }
            },
            onCapture = onCapture,
            onOpenGallery = onOpenGallery,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (countdownSeconds != null) {
            Countdown(number = countdownSeconds, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = displayedStatus != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 28.dp, vertical = 56.dp),
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
            .semantics { contentDescription = "Camera viewfinder. Tap to focus." }
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
                if (latestThumbnail != null) {
                    Image(
                        bitmap = latestThumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = Color.White)
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
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .22f)),
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

    LaunchedEffect(selectedProfileId, profiles.size) {
        if (pagerState.settledPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    LaunchedEffect(pagerState, profiles) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> profiles.getOrNull(page)?.let { onFilterSelected(it.id) } }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "FILM",
            color = Color.White.copy(alpha = .64f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(7.dp))
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
                    if (page == pagerState.currentPage) onFilterSelected(profile.id)
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
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(profile.swatchTop), Color(profile.swatchBottom)),
                                ),
                            ),
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
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
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
                onClick = onClick,
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
        modifier = modifier.fillMaxSize().background(Color(0xFF111111)),
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
private const val FOCUS_VISIBLE_MILLIS = 1_050L
private const val CAPTURE_FEEDBACK_MILLIS = 72L
private const val LENS_STATUS_VISIBLE_MILLIS = 2_000L
