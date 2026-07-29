package com.nielk74.came

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nielk74.came.camera.CameraCaptureStore
import com.nielk74.came.camera.CameraSession
import com.nielk74.came.camera.CompositionZoom
import com.nielk74.came.camera.CaptureRun
import com.nielk74.came.camera.CaptureStage
import com.nielk74.came.filters.FilmCatalog
import com.nielk74.came.gallery.PhotoRepository
import com.nielk74.came.settings.CameraSettings
import com.nielk74.came.settings.SettingsRepository
import com.nielk74.came.ui.CameTheme
import com.nielk74.came.ui.CameraPermissionScreen
import com.nielk74.came.ui.CameraScreen
import com.nielk74.came.ui.GalleryScreen
import com.nielk74.came.ui.SettingsScreen
import com.nielk74.came.update.AppRelease
import com.nielk74.came.update.AppUpdateViewModel
import com.nielk74.came.update.DownloadState
import com.nielk74.came.update.UpdateStatus
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val captureStore by lazy { CameraCaptureStore(applicationContext) }
    private val cameraSessionHolder = lazy { CameraSession(applicationContext) }
    private val cameraSession by cameraSessionHolder
    private val photoRepository by lazy { PhotoRepository(applicationContext) }
    private var volumeShutterAction: (() -> Unit)? = null

    override fun onDestroy() {
        if (cameraSessionHolder.isInitialized()) cameraSession.close()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            CameTheme {
                val scope = rememberCoroutineScope()
                val updateViewModel: AppUpdateViewModel = viewModel()
                val appUpdateStatus by updateViewModel.updateStatus.collectAsStateWithLifecycle()
                val downloadState by updateViewModel.downloadState.collectAsStateWithLifecycle()
                val settings by settingsRepository.state.collectAsStateWithLifecycle(
                    initialValue = CameraSettings(),
                )
                var cameraPermissionGranted by remember {
                    mutableStateOf(hasPermission(Manifest.permission.CAMERA))
                }
                var permissionWasRequested by rememberSaveable { mutableStateOf(false) }
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                var galleryOpen by rememberSaveable { mutableStateOf(false) }
                var galleryInitialUri by remember { mutableStateOf<Uri?>(null) }
                var galleryRefreshKey by remember { mutableIntStateOf(0) }
                var latestPhotoUri by remember { mutableStateOf<Uri?>(null) }
                var countdown by remember { mutableStateOf<Int?>(null) }
                var isCapturing by remember { mutableStateOf(false) }
                var compositionZoomFactor by rememberSaveable { mutableFloatStateOf(1f) }
                // A StateFlow rather than plain state: the render reports its progress from a
                // background dispatcher, and this is written from there.
                val captureStageFlow = remember { MutableStateFlow<CaptureStage?>(null) }
                val captureStage by captureStageFlow.collectAsState()
                var captureRun by remember { mutableStateOf<CaptureRun?>(null) }
                var captureFeedbackKey by remember { mutableIntStateOf(0) }
                var statusMessage by remember { mutableStateOf<String?>(null) }
                var captureJob by remember { mutableStateOf<Job?>(null) }
                var filterSelectionJob by remember { mutableStateOf<Job?>(null) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { results ->
                    cameraPermissionGranted = results[Manifest.permission.CAMERA]
                        ?: hasPermission(Manifest.permission.CAMERA)
                }
                val requestPermissions = {
                    permissionWasRequested = true
                    permissionLauncher.launch(requiredRuntimePermissions())
                }

                LaunchedEffect(Unit) {
                    if (!cameraPermissionGranted && !permissionWasRequested) requestPermissions()
                }
                LaunchedEffect(statusMessage) {
                    val shown = statusMessage ?: return@LaunchedEffect
                    delay(STATUS_VISIBLE_MILLIS)
                    if (statusMessage == shown) statusMessage = null
                }
                LaunchedEffect(galleryRefreshKey) {
                    latestPhotoUri = photoRepository.loadPhotos().firstOrNull()?.uri
                }
                var latestThumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(latestPhotoUri, galleryRefreshKey) {
                    latestThumbnail = latestPhotoUri?.let { uri ->
                        photoRepository.loadBitmap(uri, maxDimension = 320)?.asImageBitmap()
                    }
                }

                val enabledProfiles = FilmCatalog.profiles.filter { profile ->
                    profile.id in settings.enabledFilterIds
                }.ifEmpty { listOf(FilmCatalog.default) }
                val selectedProfile = enabledProfiles.firstOrNull {
                    it.id == settings.selectedFilterId
                } ?: enabledProfiles.first()

                val takePhoto = takePhoto@{
                    if (
                        captureJob?.isActive == true || settingsOpen || galleryOpen ||
                        !cameraPermissionGranted || cameraSession.isLensSwitching.value
                    ) {
                        return@takePhoto
                    }
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    ) {
                        statusMessage = "Allow photo storage to save this shot"
                        requestPermissions()
                        return@takePhoto
                    }

                    val profileAtShutter = selectedProfile
                    val grainAtShutter = settings.grainEnabled
                    val delayAtShutter = settings.timerSeconds
                    val compositionZoomAtShutter = CompositionZoom.of(compositionZoomFactor)
                    captureJob = scope.launch {
                        try {
                            isCapturing = true
                            captureRun = CaptureRun(profileAtShutter, grainAtShutter)
                            if (delayAtShutter > 0) {
                                for (remaining in delayAtShutter downTo 1) {
                                    countdown = remaining
                                    delay(1_000)
                                }
                                countdown = null
                            }
                            captureFeedbackKey++
                            val savedUri = captureStore.capture(
                                imageCapture = cameraSession.imageCapture,
                                profile = profileAtShutter,
                                grainEnabled = grainAtShutter,
                                compositionZoom = compositionZoomAtShutter,
                                onStage = { stage -> captureStageFlow.value = stage },
                            )
                            latestPhotoUri = savedUri
                            galleryRefreshKey++
                            statusMessage = "Saved to Pictures/camé"
                        } catch (error: Throwable) {
                            statusMessage = error.toCameraMessage()
                        } finally {
                            countdown = null
                            isCapturing = false
                            captureStageFlow.value = null
                            captureRun = null
                        }
                    }
                }

                SideEffect { volumeShutterAction = takePhoto }
                DisposableEffect(Unit) {
                    onDispose { volumeShutterAction = null }
                }

                BackHandler(enabled = settingsOpen || galleryOpen) {
                    settingsOpen = false
                    galleryOpen = false
                    hideSystemBars()
                }

                if (!cameraPermissionGranted) {
                    val permanentlyDenied = permissionWasRequested &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                    CameraPermissionScreen(
                        permanentlyDenied = permanentlyDenied,
                        onRequestPermission = {
                            if (permanentlyDenied) openAppSettings() else requestPermissions()
                        },
                    )
                } else if (galleryOpen) {
                    GalleryScreen(
                        repository = photoRepository,
                        refreshKey = galleryRefreshKey,
                        initialPhotoUri = galleryInitialUri,
                        onLibraryChanged = {
                            // Do not let the thumbnail refresh race a URI the gallery just
                            // removed from MediaStore.
                            latestPhotoUri = null
                            galleryRefreshKey++
                        },
                        onClose = {
                            galleryOpen = false
                            galleryInitialUri = null
                            hideSystemBars()
                        },
                    )
                } else if (settingsOpen) {
                    SettingsScreen(
                        settings = settings,
                        profiles = FilmCatalog.profiles,
                        updateStatus = updateSummary(appUpdateStatus, downloadState),
                        updateActionLabel = updateActionLabel(appUpdateStatus, downloadState),
                        isCheckingForUpdates = appUpdateStatus is UpdateStatus.Checking ||
                            downloadState is DownloadState.Downloading ||
                            downloadState is DownloadState.Verifying,
                        onGrainChanged = { enabled ->
                            scope.launch { settingsRepository.setGrainEnabled(enabled) }
                        },
                        onElectronicLevelChanged = { enabled ->
                            scope.launch { settingsRepository.setElectronicLevelEnabled(enabled) }
                        },
                        onFilterEnabledChanged = { filterId, enabled ->
                            scope.launch { settingsRepository.setFilterEnabled(filterId, enabled) }
                        },
                        onTimerChanged = { seconds ->
                            scope.launch { settingsRepository.setTimerSeconds(seconds) }
                        },
                        onOpenGallery = {
                            settingsOpen = false
                            galleryInitialUri = null
                            galleryOpen = true
                        },
                        onCheckForUpdates = {
                            if (appUpdateStatus is UpdateStatus.Available) {
                                updateViewModel.downloadAndInstall()
                            } else {
                                updateViewModel.checkForUpdates()
                            }
                        },
                        onClose = {
                            settingsOpen = false
                            hideSystemBars()
                        },
                    )
                } else {
                    CameraScreen(
                        cameraSession = cameraSession,
                        profiles = enabledProfiles,
                        selectedProfileId = selectedProfile.id,
                        compositionZoom = CompositionZoom.of(compositionZoomFactor),
                        electronicLevelEnabled = settings.electronicLevelEnabled,
                        timerSeconds = settings.timerSeconds,
                        countdownSeconds = countdown,
                        isCapturing = isCapturing,
                        captureStage = captureStage,
                        captureRun = captureRun,
                        captureFeedbackKey = captureFeedbackKey,
                        statusMessage = statusMessage,
                        latestThumbnail = latestThumbnail,
                        onCompositionZoomChanged = { zoom ->
                            compositionZoomFactor = zoom.factor
                        },
                        onFilterSelected = { filterId ->
                            val precedingSelection = filterSelectionJob
                            filterSelectionJob = scope.launch {
                                // Preserve carousel order so an older DataStore write can never
                                // land after the film card the user ultimately settled on.
                                precedingSelection?.join()
                                settingsRepository.selectFilter(filterId)
                            }
                        },
                        onCapture = takePhoto,
                        onOpenGallery = {
                            if (!isCapturing) {
                                galleryInitialUri = latestPhotoUri
                                galleryOpen = true
                            }
                        },
                        onOpenSettings = {
                            if (!isCapturing) settingsOpen = true
                        },
                    )
                }

                val availableUpdate = appUpdateStatus as? UpdateStatus.Available
                if (
                    cameraPermissionGranted &&
                    !settingsOpen &&
                    !galleryOpen &&
                    availableUpdate != null &&
                    downloadState !is DownloadState.ReadyToInstall
                ) {
                    UpdatePrompt(
                        release = availableUpdate.release,
                        downloadState = downloadState,
                        onInstall = updateViewModel::downloadAndInstall,
                        onLater = updateViewModel::dismissUpdate,
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0 && keyCode.isVolumeKey()) {
            volumeShutterAction?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode.isVolumeKey()) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun requiredRuntimePermissions(): Array<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@androidx.compose.runtime.Composable
private fun UpdatePrompt(
    release: AppRelease,
    downloadState: DownloadState,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    val busy = downloadState is DownloadState.Downloading || downloadState is DownloadState.Verifying
    AlertDialog(
        onDismissRequest = { if (!busy) onLater() },
        title = {
            Text(
                text = "CAMÉ ${release.versionName}",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = when (downloadState) {
                    is DownloadState.Downloading ->
                        "Downloading ${(downloadState.fraction * 100).toInt()}%…"
                    DownloadState.Verifying -> "Verifying the published SHA-256 checksum…"
                    is DownloadState.Failed -> downloadState.reason
                    else -> release.notes.take(360).ifBlank {
                        "A new public release is ready to install."
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !busy) {
                Text(if (downloadState is DownloadState.Failed) "RETRY" else "UPDATE")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !busy) { Text("LATER") }
        },
    )
}

private fun Throwable.toCameraMessage(): String {
    val concise = message
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    return concise ?: "The photo could not be saved"
}

private const val STATUS_VISIBLE_MILLIS = 2_800L

private fun Int.isVolumeKey(): Boolean =
    this == KeyEvent.KEYCODE_VOLUME_DOWN || this == KeyEvent.KEYCODE_VOLUME_UP

private fun updateSummary(status: UpdateStatus, download: DownloadState): String = when (download) {
    is DownloadState.Downloading -> "Downloading ${(download.fraction * 100).toInt()}%"
    DownloadState.Verifying -> "Verifying SHA-256 checksum"
    DownloadState.ReadyToInstall -> "Android installer opened"
    is DownloadState.Failed -> download.reason
    DownloadState.Idle -> when (status) {
        UpdateStatus.Idle -> "Install the latest public release"
        UpdateStatus.Checking -> "Checking GitHub Releases…"
        UpdateStatus.UpToDate -> "camé ${BuildConfig.VERSION_NAME} is current"
        is UpdateStatus.Available -> "Version ${status.release.versionName} is ready"
        is UpdateStatus.Failed -> status.reason
    }
}

private fun updateActionLabel(status: UpdateStatus, download: DownloadState): String = when {
    download is DownloadState.Downloading -> "DOWNLOADING UPDATE"
    download is DownloadState.Verifying -> "VERIFYING UPDATE"
    status is UpdateStatus.Available -> "INSTALL ${status.release.versionName}"
    else -> "CHECK FOR UPDATES"
}
