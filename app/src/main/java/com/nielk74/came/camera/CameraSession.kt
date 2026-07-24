package com.nielk74.came.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/** Small lifecycle boundary around the two CameraX use cases camé actually needs. */
class CameraSession(context: Context) {
    private val appContext = context.applicationContext
    private val providerFuture = ProcessCameraProvider.getInstance(appContext)
    private var bindingGeneration = 0

    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
        .build()

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val generation = ++bindingGeneration
        providerFuture.addListener(
            {
                if (generation != bindingGeneration) return@addListener
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun unbind() {
        bindingGeneration++
        if (providerFuture.isDone) providerFuture.get().unbindAll()
    }
}

