package com.nielk74.came.camera

import android.animation.ValueAnimator
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Size
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle and hardware boundary for camé's quality-first CameraX still pipeline. */
class CameraSession(context: android.content.Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val providerFuture = ProcessCameraProvider.getInstance(appContext)
    private var bindingGeneration = 0
    private var camera: Camera? = null
    private var previewView = WeakReference<PreviewView>(null)
    private var streamObserver: Observer<PreviewView.StreamState>? = null
    private var matrixAnimator: ValueAnimator? = null
    private var displayedMatrix = IDENTITY_MATRIX.copyOf()
    private var requestedMatrix = IDENTITY_MATRIX.copyOf()
    private var requestedLensRatio = 1f
    private var lensSwitchGeneration = 0
    private var observedZoomInfo: CameraInfo? = null
    private var pendingZoomObserver: Observer<ZoomState>? = null
    private val defaultLens = recommendCameraLenses(emptyList(), 1f, 1f).first()
    private var exposureRequestGeneration = 0
    private var requestedExposureValue = ExposureValue.ZERO
    private val _availableLenses = MutableStateFlow(listOf(defaultLens))
    private val _selectedLens = MutableStateFlow(defaultLens)
    private val _isLensSwitching = MutableStateFlow(false)
    private val _exposureControlState = MutableStateFlow(ExposureControlState())
    private val _recognizedQrLink = MutableStateFlow<String?>(null)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val qrCodeAnalyzer = QrCodeAnalyzer(
        onLinkChanged = { link -> _recognizedQrLink.value = link },
    )
    private val qrAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(qrFrameShape())
        .build()
        .also { it.setAnalyzer(analysisExecutor, qrCodeAnalyzer) }
    private var closed = false

    val availableLenses: StateFlow<List<CameraLens>> = _availableLenses.asStateFlow()
    val selectedLens: StateFlow<CameraLens> = _selectedLens.asStateFlow()
    val isLensSwitching: StateFlow<Boolean> = _isLensSwitching.asStateFlow()
    val exposureControlState: StateFlow<ExposureControlState> =
        _exposureControlState.asStateFlow()
    val recognizedQrLink: StateFlow<String?> = _recognizedQrLink.asStateFlow()

    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setJpegQuality(JPEG_QUALITY)
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .setResolutionSelector(frameShape())
        .build()

    fun bind(
        lifecycleOwner: LifecycleOwner,
        view: PreviewView,
        onStreamState: (Boolean) -> Unit = {},
    ) {
        if (closed) return
        val generation = ++bindingGeneration
        resetLensState()
        resetExposureState()
        qrCodeAnalyzer.stop()
        removePendingZoomObserver()
        previewView.get()?.let { previous ->
            streamObserver?.let(previous.previewStreamState::removeObserver)
        }
        previewView = WeakReference(view)
        streamObserver = Observer<PreviewView.StreamState> { state ->
            onStreamState(state == PreviewView.StreamState.STREAMING)
        }.also { observer: Observer<PreviewView.StreamState> ->
            view.previewStreamState.observe(lifecycleOwner, observer)
        }
        applyMatrix(view, requestedMatrix)
        providerFuture.addListener(
            {
                if (generation != bindingGeneration) return@addListener
                val provider = providerFuture.get()
                provider.unbindAll()
                val extensionsFuture = ExtensionsManager.getInstanceAsync(appContext, provider)
                extensionsFuture.addListener(
                    {
                        if (generation != bindingGeneration) return@addListener
                        val selected = runCatching {
                            chooseQualitySelector(extensionsFuture.get())
                        }.getOrDefault(CameraSelection(CameraSelector.DEFAULT_BACK_CAMERA, false))
                        bindUseCases(
                            provider = provider,
                            lifecycleOwner = lifecycleOwner,
                            view = view,
                            selection = selected,
                            generation = generation,
                        )
                    },
                    mainExecutor,
                )
            },
            mainExecutor,
        )
    }

    /** Applies the preview approximation entirely on the render thread, without per-frame CPU work. */
    fun setPreviewColorMatrix(matrix: FloatArray, animate: Boolean = true) {
        require(matrix.size == MATRIX_SIZE)
        if (matrix.contentEquals(requestedMatrix) && previewView.get() != null) return
        requestedMatrix = matrix.copyOf()
        val view = previewView.get() ?: return
        matrixAnimator?.cancel()
        if (!animate) {
            displayedMatrix = requestedMatrix.copyOf()
            applyMatrix(view, displayedMatrix)
            return
        }

        val start = displayedMatrix.copyOf()
        val end = requestedMatrix.copyOf()
        matrixAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FILTER_ANIMATION_MILLIS
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                displayedMatrix = FloatArray(MATRIX_SIZE) { index ->
                    start[index] + (end[index] - start[index]) * fraction
                }
                previewView.get()?.let { applyMatrix(it, displayedMatrix) }
            }
            start()
        }
    }

    fun focusAt(x: Float, y: Float, onComplete: (Boolean) -> Unit = {}) {
        val boundCamera = camera
        val view = previewView.get()
        if (boundCamera == null || view == null) {
            onComplete(false)
            return
        }
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        val result = boundCamera.cameraControl.startFocusAndMetering(action)
        result.addListener(
            {
                onComplete(runCatching { result.get().isFocusSuccessful }.getOrDefault(false))
            },
            mainExecutor,
        )
    }

    /** Requests a device-reported field of view while leaving physical selection to the HAL. */
    fun selectLens(lens: CameraLens, onComplete: (Boolean) -> Unit = {}) {
        val boundCamera = camera
        val target = _availableLenses.value.firstOrNull {
            abs(it.zoomRatio - lens.zoomRatio) < LENS_RATIO_EPSILON
        }
        if (boundCamera == null || target == null) {
            onComplete(false)
            return
        }
        if (!_isLensSwitching.value && target == _selectedLens.value) {
            onComplete(true)
            return
        }
        switchToLens(boundCamera, target, rememberOnSuccess = true, onComplete = onComplete)
    }

    /**
     * Applies one of camé's five EV detents using the closest index supported by this camera.
     *
     * The requested detent is remembered across lifecycle rebinds. Unsupported or temporarily
     * unbound cameras report failure without sending an invalid request to the HAL.
     */
    fun setExposureCompensation(
        value: ExposureValue,
        onComplete: (Boolean) -> Unit = {},
    ) {
        requestedExposureValue = value
        val boundCamera = camera
        if (boundCamera == null) {
            _exposureControlState.value = ExposureControlState(selectedValue = value)
            onComplete(false)
            return
        }
        applyExposureCompensation(boundCamera, value, onComplete)
    }

    fun unbind() {
        bindingGeneration++
        lensSwitchGeneration++
        _isLensSwitching.value = false
        resetExposureState()
        qrCodeAnalyzer.stop()
        removePendingZoomObserver()
        matrixAnimator?.cancel()
        matrixAnimator = null
        camera = null
        previewView.get()?.let { view -> streamObserver?.let(view.previewStreamState::removeObserver) }
        streamObserver = null
        previewView.clear()
        if (providerFuture.isDone) providerFuture.get().unbindAll()
    }

    fun close() {
        if (closed) return
        closed = true
        unbind()
        qrAnalysis.clearAnalyzer()
        qrCodeAnalyzer.close()
        analysisExecutor.shutdown()
    }

    private fun chooseQualitySelector(manager: ExtensionsManager): CameraSelection {
        val base = CameraSelector.DEFAULT_BACK_CAMERA
        val mode = when {
            manager.isExtensionAvailable(base, ExtensionMode.AUTO) &&
                manager.isImageAnalysisSupported(base, ExtensionMode.AUTO) -> ExtensionMode.AUTO
            manager.isExtensionAvailable(base, ExtensionMode.HDR) &&
                manager.isImageAnalysisSupported(base, ExtensionMode.HDR) -> ExtensionMode.HDR
            else -> return CameraSelection(base, false)
        }
        return CameraSelection(manager.getExtensionEnabledCameraSelector(base, mode), true)
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        view: PreviewView,
        selection: CameraSelection,
        generation: Int,
    ) {
        if (generation != bindingGeneration) return
        val rotation = view.display?.rotation
        val previewBuilder = Preview.Builder().setResolutionSelector(frameShape())
        if (rotation != null) {
            previewBuilder.setTargetRotation(rotation)
            imageCapture.targetRotation = rotation
            qrAnalysis.targetRotation = rotation
        }
        val preview = previewBuilder.build().apply { surfaceProvider = view.surfaceProvider }
        provider.unbindAll()
        qrCodeAnalyzer.start()
        val bound = runCatching {
            provider.bindToLifecycle(
                lifecycleOwner,
                selection.selector,
                preview,
                imageCapture,
                qrAnalysis,
            )
        }.recoverCatching { error ->
            if (!selection.isExtension) throw error
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                qrAnalysis,
            )
        }.getOrNull() ?: run {
            qrCodeAnalyzer.stop()
            camera = null
            resetLensState()
            resetExposureState()
            return
        }
        camera = bound
        // A previous CameraX session or device default must never leave the torch on.
        bound.cameraControl.enableTorch(false)
        imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
        configureExposureCompensation(bound)
        awaitZoomState(provider, lifecycleOwner, bound, generation)
    }

    private fun configureExposureCompensation(boundCamera: Camera) {
        val exposureState = boundCamera.cameraInfo.exposureState
        if (!exposureState.isExposureCompensationSupported) {
            _exposureControlState.value = ExposureControlState(
                selectedValue = requestedExposureValue,
                appliedIndex = exposureState.exposureCompensationIndex,
                appliedEv = exposureEvForIndex(
                    index = exposureState.exposureCompensationIndex,
                    numerator = exposureState.exposureCompensationStep.numerator,
                    denominator = exposureState.exposureCompensationStep.denominator,
                ),
            )
            return
        }
        applyExposureCompensation(boundCamera, requestedExposureValue)
    }

    private fun applyExposureCompensation(
        boundCamera: Camera,
        value: ExposureValue,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val exposureState = boundCamera.cameraInfo.exposureState
        if (!exposureState.isExposureCompensationSupported) {
            _exposureControlState.value = ExposureControlState(
                selectedValue = value,
                appliedIndex = exposureState.exposureCompensationIndex,
                appliedEv = exposureEvForIndex(
                    index = exposureState.exposureCompensationIndex,
                    numerator = exposureState.exposureCompensationStep.numerator,
                    denominator = exposureState.exposureCompensationStep.denominator,
                ),
            )
            onComplete(false)
            return
        }

        val range = exposureState.exposureCompensationRange
        val step = exposureState.exposureCompensationStep
        val mapping = mapExposureCompensation(
            value = value,
            minimumIndex = range.lower,
            maximumIndex = range.upper,
            stepNumerator = step.numerator,
            stepDenominator = step.denominator,
        )
        val request = ++exposureRequestGeneration
        _exposureControlState.value = ExposureControlState(
            selectedValue = value,
            isSupported = true,
            isApplying = exposureState.exposureCompensationIndex != mapping.index,
            appliedIndex = exposureState.exposureCompensationIndex,
            appliedEv = exposureEvForIndex(
                index = exposureState.exposureCompensationIndex,
                numerator = step.numerator,
                denominator = step.denominator,
            ),
        )
        if (exposureState.exposureCompensationIndex == mapping.index) {
            _exposureControlState.value = _exposureControlState.value.copy(
                isApplying = false,
                appliedIndex = mapping.index,
                appliedEv = mapping.ev,
            )
            onComplete(true)
            return
        }

        val result = runCatching {
            boundCamera.cameraControl.setExposureCompensationIndex(mapping.index)
        }.getOrElse {
            _exposureControlState.value = _exposureControlState.value.copy(isApplying = false)
            onComplete(false)
            return
        }
        result.addListener(
            {
                if (request != exposureRequestGeneration || camera !== boundCamera) {
                    return@addListener
                }
                val appliedIndex = runCatching { result.get() }.getOrNull()
                val successful = appliedIndex != null
                val actualIndex = appliedIndex ?: exposureState.exposureCompensationIndex
                _exposureControlState.value = ExposureControlState(
                    selectedValue = value,
                    isSupported = true,
                    isApplying = false,
                    appliedIndex = actualIndex,
                    appliedEv = exposureEvForIndex(
                        index = actualIndex,
                        numerator = step.numerator,
                        denominator = step.denominator,
                    ),
                )
                onComplete(successful)
            },
            mainExecutor,
        )
    }

    private fun awaitZoomState(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        boundCamera: Camera,
        binding: Int,
    ) {
        removePendingZoomObserver()
        val cameraInfo = boundCamera.cameraInfo
        lateinit var observer: Observer<ZoomState>
        observer = Observer { zoomState ->
            if (
                binding != bindingGeneration || camera !== boundCamera
            ) {
                return@Observer
            }
            cameraInfo.zoomState.removeObserver(observer)
            if (pendingZoomObserver === observer) {
                pendingZoomObserver = null
                observedZoomInfo = null
            }
            configureLensOptions(provider, boundCamera, zoomState)
        }
        observedZoomInfo = cameraInfo
        pendingZoomObserver = observer
        cameraInfo.zoomState.observe(lifecycleOwner, observer)
    }

    private fun configureLensOptions(
        provider: ProcessCameraProvider,
        boundCamera: Camera,
        zoomState: ZoomState,
    ) {
        val defaultBackInfo = runCatching {
            CameraSelector.DEFAULT_BACK_CAMERA
                .filter(provider.availableCameraInfos)
                .firstOrNull()
        }.getOrNull()
        val physicalRatios = defaultBackInfo
            ?.physicalCameraInfos
            .orEmpty()
            .mapNotNull { info ->
                runCatching { info.intrinsicZoomRatio }
                    .getOrNull()
                    ?.takeIf { it.isFinite() && it > 0f }
            }
        val lenses = recommendCameraLenses(
            physicalCameraRatios = physicalRatios,
            minimumZoomRatio = zoomState.minZoomRatio,
            maximumZoomRatio = zoomState.maxZoomRatio,
        )
        _availableLenses.value = lenses

        val actualRatio = zoomState.zoomRatio
        _selectedLens.value = closestCameraLens(lenses, actualRatio) ?: lenses.first()
        val requested = closestCameraLens(lenses, requestedLensRatio) ?: _selectedLens.value
        if (abs(requested.zoomRatio - actualRatio) >= LENS_RATIO_EPSILON) {
            switchToLens(boundCamera, requested, rememberOnSuccess = false)
        } else {
            requestedLensRatio = requested.zoomRatio
            _selectedLens.value = requested
        }
    }

    private fun switchToLens(
        boundCamera: Camera,
        target: CameraLens,
        rememberOnSuccess: Boolean,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val zoomState = boundCamera.cameraInfo.zoomState.value
        if (zoomState == null || target.zoomRatio !in zoomState.minZoomRatio..zoomState.maxZoomRatio) {
            onComplete(false)
            return
        }
        val generation = ++lensSwitchGeneration
        _isLensSwitching.value = true
        val result = runCatching {
            boundCamera.cameraControl.setZoomRatio(target.zoomRatio)
        }.getOrElse {
            _isLensSwitching.value = false
            onComplete(false)
            return
        }
        result.addListener(
            {
                if (generation != lensSwitchGeneration || camera !== boundCamera) {
                    return@addListener
                }
                val failure = runCatching { result.get() }.exceptionOrNull()
                val successful = failure == null
                if (successful) {
                    _selectedLens.value = target
                    if (rememberOnSuccess) requestedLensRatio = target.zoomRatio
                } else {
                    val actualRatio = boundCamera.cameraInfo.zoomState.value?.zoomRatio
                        ?: _selectedLens.value.zoomRatio
                    val remaining = if (shouldRemoveLensAfter(failure)) {
                        _availableLenses.value.filterNot {
                            it != defaultLens &&
                                abs(it.zoomRatio - target.zoomRatio) < LENS_RATIO_EPSILON
                        }.ifEmpty { listOf(defaultLens) }
                    } else {
                        _availableLenses.value
                    }
                    _availableLenses.value = remaining
                    _selectedLens.value = closestCameraLens(remaining, actualRatio)
                        ?: closestCameraLens(remaining, 1f)
                        ?: remaining.first()
                }
                _isLensSwitching.value = false
                onComplete(successful)
            },
            mainExecutor,
        )
    }

    private fun removePendingZoomObserver() {
        val info = observedZoomInfo
        val observer = pendingZoomObserver
        if (info != null && observer != null) info.zoomState.removeObserver(observer)
        observedZoomInfo = null
        pendingZoomObserver = null
    }

    private fun resetLensState() {
        lensSwitchGeneration++
        _isLensSwitching.value = false
        _availableLenses.value = listOf(defaultLens)
        _selectedLens.value = defaultLens
    }

    private fun resetExposureState() {
        exposureRequestGeneration++
        _exposureControlState.value = ExposureControlState(
            selectedValue = requestedExposureValue,
        )
    }

    @Suppress("DEPRECATION")
    /**
     * Pins the viewfinder and the capture to one frame shape.
     *
     * Left to their own defaults the two use cases can settle on different aspect ratios, which
     * would reintroduce the mismatch between what is framed and what is saved even with the
     * viewfinder fitting rather than filling. 4:3 is the native shape of the sensors in question,
     * so it keeps the whole frame and the full resolution.
     */
    private fun frameShape(): ResolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .build()

    /** Keeps live QR work bounded while retaining enough detail to read a code at arm's length. */
    private fun qrFrameShape(): ResolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(QR_ANALYSIS_WIDTH, QR_ANALYSIS_HEIGHT),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            ),
        )
        .build()

    private fun applyMatrix(view: PreviewView, values: FloatArray) {
        applyMatrixWhenReady(view, values, remainingAttempts = 8)
    }

    @Suppress("DEPRECATION")
    private fun applyMatrixWhenReady(view: PreviewView, values: FloatArray, remainingAttempts: Int) {
        val filter = ColorMatrixColorFilter(ColorMatrix(values))
        val texture = findTextureView(view)
        if (texture != null) {
            texture.setLayerType(
                View.LAYER_TYPE_HARDWARE,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = filter },
            )
        } else if (remainingAttempts > 0) {
            // PreviewView creates its internal texture after receiving the SurfaceProvider.
            view.postDelayed(
                { applyMatrixWhenReady(view, values, remainingAttempts - 1) },
                TEXTURE_RETRY_MILLIS,
            )
        }
    }

    private fun findTextureView(view: View): TextureView? {
        if (view is TextureView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findTextureView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private companion object {
        const val MATRIX_SIZE = 20
        const val JPEG_QUALITY = 100
        const val LENS_RATIO_EPSILON = .01f
        const val FILTER_ANIMATION_MILLIS = 240L
        const val TEXTURE_RETRY_MILLIS = 24L
        const val QR_ANALYSIS_WIDTH = 1_280
        const val QR_ANALYSIS_HEIGHT = 960
        val IDENTITY_MATRIX = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private data class CameraSelection(
        val selector: CameraSelector,
        val isExtension: Boolean,
    )
}
