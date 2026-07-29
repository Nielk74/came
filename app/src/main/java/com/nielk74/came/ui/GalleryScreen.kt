package com.nielk74.came.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nielk74.came.gallery.PhotoItem
import com.nielk74.came.gallery.PhotoRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(
    repository: PhotoRepository,
    refreshKey: Int,
    physicalUiRotationDegrees: Float = 0f,
    initialPhotoUri: Uri? = null,
    onLibraryChanged: () -> Unit,
    onClose: () -> Unit,
) {
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    suspend fun reload() {
        loading = true
        error = null
        runCatching { repository.loadPhotos() }
            .onSuccess { loaded ->
                photos = loaded
                selectedIndex = initialPhotoUri?.let { uri -> loaded.indexOfFirst { it.uri == uri } }
                    ?.takeIf { it >= 0 }
            }
            .onFailure { error = it.message ?: "The photo library could not be loaded" }
        loading = false
    }

    LaunchedEffect(refreshKey, reloadKey) { reload() }
    BackHandler {
        if (selectedIndex != null) selectedIndex = null else onClose()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = CamePalette.Black) {
        val index = selectedIndex
        if (index != null && photos.isNotEmpty()) {
            val safeIndex = index.coerceIn(0, photos.lastIndex)
            PhotoViewer(
                repository = repository,
                photo = photos[safeIndex],
                index = safeIndex,
                total = photos.size,
                onBack = { selectedIndex = null },
                onPrevious = { selectedIndex = (safeIndex - 1).coerceAtLeast(0) },
                onNext = { selectedIndex = (safeIndex + 1).coerceAtMost(photos.lastIndex) },
                onDeleted = {
                    val mutable = photos.toMutableList().apply { removeAt(safeIndex) }
                    photos = mutable
                    selectedIndex = if (mutable.isEmpty()) null else safeIndex.coerceAtMost(mutable.lastIndex)
                    onLibraryChanged()
                },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                LibraryHeader(
                    count = photos.size,
                    loading = loading,
                    onRefresh = { reloadKey++ },
                    onClose = onClose,
                )
                HorizontalDivider(color = CamePalette.Separator)
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CamePalette.Accent, strokeWidth = 2.dp)
                    }
                    error != null -> LibraryMessage(
                        icon = Icons.Rounded.Refresh,
                        title = "LIBRARY UNAVAILABLE",
                        body = error.orEmpty(),
                        onClick = { reloadKey++ },
                    )
                    photos.isEmpty() -> LibraryMessage(
                        icon = Icons.Rounded.PhotoLibrary,
                        title = "NO PHOTOGRAPHS YET",
                        body = "Pictures made with camé will appear here.",
                    )
                    else -> PhotoGrid(
                        repository = repository,
                        photos = photos,
                        physicalUiRotationDegrees = physicalUiRotationDegrees,
                        onOpen = { selectedIndex = photos.indexOf(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(count: Int, loading: Boolean, onRefresh: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(76.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(30.dp).background(CamePalette.Accent))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("PHOTO LIBRARY", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text(
                if (count == 1) "1 PHOTOGRAPH" else "$count PHOTOGRAPHS",
                color = CamePalette.Muted,
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
            )
        }
        IconButton(enabled = !loading, onClick = onRefresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh library", tint = Color.White)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Close library", tint = Color.White)
        }
    }
}

@Composable
private fun PhotoGrid(
    repository: PhotoRepository,
    photos: List<PhotoItem>,
    physicalUiRotationDegrees: Float,
    onOpen: (PhotoItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(photos, key = { it.uri.toString() }) { photo ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(CamePalette.Panel)
                    .clickable { onOpen(photo) },
                contentAlignment = Alignment.Center,
            ) {
                RepositoryImage(
                    repository = repository,
                    uri = photo.uri,
                    maxDimension = 480,
                    contentScale = ContentScale.Crop,
                    rotationDegrees = physicalUiRotationDegrees,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = formatShortDate(photo.dateTakenMillis),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = .58f), RoundedCornerShape(topEnd = 4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun PhotoViewer(
    repository: PhotoRepository,
    photo: PhotoItem,
    index: Int,
    total: Int,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember(photo.uri) { mutableFloatStateOf(1f) }
    var translation by remember(photo.uri) { mutableStateOf(Offset.Zero) }
    var showInfo by remember(photo.uri) { mutableStateOf(false) }
    var confirmDelete by remember(photo.uri) { mutableStateOf(false) }
    var error by remember(photo.uri) { mutableStateOf<String?>(null) }
    var filmFilterName by remember(photo.uri) { mutableStateOf<String?>(null) }
    var filmMetadataLoaded by remember(photo.uri) { mutableStateOf(false) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        scale = nextScale
        translation = if (nextScale == MIN_ZOOM) Offset.Zero else translation + panChange
    }
    LaunchedEffect(photo.uri) {
        filmFilterName = runCatching { repository.loadFilmFilterName(photo.uri) }.getOrNull()
        filmMetadataLoaded = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RepositoryImage(
            repository = repository,
            uri = photo.uri,
            maxDimension = 2_560,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(photo.uri) {
                    // Double-tap is the gesture people reach for first: step in to inspect,
                    // double-tap again to fall back to the full frame.
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > MIN_ZOOM) {
                                scale = MIN_ZOOM
                                translation = Offset.Zero
                            } else {
                                scale = DOUBLE_TAP_ZOOM
                            }
                        },
                    )
                }
                .transformable(transformState)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = translation.x
                    translationY = translation.y
                }
                .semantics { contentDescription = "Photograph. Pinch and pan to inspect." },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = .74f))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to library", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    photo.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${index + 1} / $total", color = CamePalette.Muted, fontSize = 10.sp)
            }
            IconButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, photo.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, "Share photograph"))
            }) {
                Icon(Icons.Rounded.Share, contentDescription = "Share photograph", tint = Color.White)
            }
            IconButton(onClick = { showInfo = !showInfo }) {
                Icon(Icons.Rounded.Info, contentDescription = "Photograph information", tint = Color.White)
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete photograph", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .62f)),
        ) {
            IconButton(enabled = index > 0, onClick = onPrevious) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous photograph", tint = Color.White)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .62f)),
        ) {
            IconButton(enabled = index < total - 1, onClick = onNext) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next photograph", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(100))
                .background(Color.Black.copy(alpha = .78f))
                .border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(100)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { scale = (scale / 1.5f).coerceAtLeast(MIN_ZOOM); if (scale == 1f) translation = Offset.Zero }) {
                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out", tint = Color.White)
            }
            Text(
                text = String.format(Locale.US, "%.1f×", scale),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { scale = 1f; translation = Offset.Zero }.padding(horizontal = 10.dp),
            )
            IconButton(onClick = { scale = (scale * 1.5f).coerceAtMost(MAX_ZOOM) }) {
                Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in", tint = Color.White)
            }
        }

        AnimatedVisibility(
            visible = showInfo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 94.dp, end = 18.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = .86f))
                    .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(8.dp))
                    .padding(16.dp),
            ) {
                Text(photo.name, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(formatFullDate(photo.dateTakenMillis), color = CamePalette.Muted, fontSize = 12.sp)
                Text("${photo.width} × ${photo.height} px", color = CamePalette.Muted, fontSize = 12.sp)
                Text(formatBytes(photo.sizeBytes), color = CamePalette.Muted, fontSize = 12.sp)
                Text(
                    "Film filter: ${
                        when {
                            filmFilterName != null -> filmFilterName
                            filmMetadataLoaded -> "Not recorded"
                            else -> "Reading…"
                        }
                    }",
                    color = CamePalette.Muted,
                    fontSize = 12.sp,
                )
                Text("Pinch or double-tap to zoom • drag to pan • tap 1× to reset", color = CamePalette.Muted, fontSize = 11.sp)
            }
        }

        error?.let { message ->
            Text(
                message,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
                    .background(CamePalette.Accent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete photograph?") },
            text = { Text("This removes ${photo.name} from the device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { repository.delete(photo) }
                            .onSuccess { deleted -> if (deleted) onDeleted() else error = "The photograph could not be deleted" }
                            .onFailure { error = it.message ?: "The photograph could not be deleted" }
                    }
                }) { Text("DELETE", color = CamePalette.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun RepositoryImage(
    repository: PhotoRepository,
    uri: Uri,
    maxDimension: Int,
    contentScale: ContentScale,
    rotationDegrees: Float = 0f,
    modifier: Modifier = Modifier,
) {
    var image by remember(repository, uri, maxDimension) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(repository, uri, maxDimension) {
        image = repository.loadBitmap(uri, maxDimension)?.asImageBitmap()
    }
    if (image != null) {
        Image(
            bitmap = requireNotNull(image),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier.graphicsLayer { rotationZ = rotationDegrees },
        )
    } else {
        Box(modifier.background(CamePalette.Panel), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = CamePalette.Muted, strokeWidth = 1.5.dp)
        }
    }
}

@Composable
private fun LibraryMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = CamePalette.Muted, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(7.dp))
        Text(body, color = CamePalette.Muted, fontSize = 13.sp)
    }
}

private fun formatShortDate(millis: Long): String =
    SHORT_DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun formatFullDate(millis: Long): String =
    FULL_DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
private val FULL_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm", Locale.getDefault())
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 8f
private const val DOUBLE_TAP_ZOOM = 2.5f
