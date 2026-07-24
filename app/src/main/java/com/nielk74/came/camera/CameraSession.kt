package com.nielk74.came.camera

import android.animation.ValueAnimator
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/** Lifecycle and hardware boundary for camé's low-latency CameraX use cases. */
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

    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .build()

    fun bind(
        lifecycleOwner: LifecycleOwner,
        view: PreviewView,
        onStreamState: (Boolean) -> Unit = {},
    ) {
        val generation = ++bindingGeneration
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
                val rotation = view.display?.rotation
                val previewBuilder = Preview.Builder()
                if (rotation != null) {
                    previewBuilder.setTargetRotation(rotation)
                    imageCapture.targetRotation = rotation
                }
                val preview = previewBuilder.build().apply {
                    surfaceProvider = view.surfaceProvider
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                ).also { boundCamera ->
                    // A previous CameraX session or device default must never leave the torch on.
                    boundCamera.cameraControl.enableTorch(false)
                    imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
                }
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

    fun unbind() {
        bindingGeneration++
        matrixAnimator?.cancel()
        matrixAnimator = null
        camera = null
        previewView.get()?.let { view -> streamObserver?.let(view.previewStreamState::removeObserver) }
        streamObserver = null
        previewView.clear()
        if (providerFuture.isDone) providerFuture.get().unbindAll()
    }

    @Suppress("DEPRECATION")
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
        const val FILTER_ANIMATION_MILLIS = 240L
        const val TEXTURE_RETRY_MILLIS = 24L
        val IDENTITY_MATRIX = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
