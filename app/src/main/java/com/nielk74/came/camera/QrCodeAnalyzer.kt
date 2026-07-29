package com.nielk74.came.camera

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

/** CameraX-to-ML-Kit bridge that publishes at most one safe web link. */
internal class QrCodeAnalyzer(
    private val onLinkChanged: (String?) -> Unit,
    private val clockMillis: () -> Long = SystemClock::elapsedRealtime,
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    ),
) : ImageAnalysis.Analyzer, Closeable {
    private val stateLock = Any()
    private val linkTracker = QrLinkTracker()
    private var generation = 0
    private var active = false

    fun start() {
        synchronized(stateLock) {
            generation++
            active = true
            linkTracker.clear()
            onLinkChanged(null)
        }
    }

    fun stop() {
        synchronized(stateLock) {
            generation++
            active = false
            linkTracker.clear()
            onLinkChanged(null)
        }
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val frameGeneration = synchronized(stateLock) {
            if (!active) {
                imageProxy.close()
                return
            }
            generation
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val task = runCatching { scanner.process(input) }.getOrElse {
            imageProxy.close()
            return
        }
        task.addOnSuccessListener { barcodes ->
            val detectedLink = firstQrWebLink(
                barcodes.asSequence().flatMap { barcode ->
                    sequenceOf(barcode.url?.url, barcode.rawValue)
                }.asIterable(),
            )
            synchronized(stateLock) {
                if (active && generation == frameGeneration) {
                    onLinkChanged(linkTracker.update(detectedLink, clockMillis()))
                }
            }
        }.addOnCompleteListener {
            imageProxy.close()
        }
    }

    override fun close() {
        stop()
        scanner.close()
    }
}
