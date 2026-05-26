package com.checkingcontainer.feature.units

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class TextRecognitionAnalyzer(
    private val mode: ScannerMode,
    private val onSuccess: (Map<String, String>) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val captureRequested = AtomicBoolean(false)
    private val done = AtomicBoolean(false)

    fun triggerCapture() {
        captureRequested.set(true)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (done.get() || !captureRequested.get()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { visionText ->
                val result = when (mode) {
                    ScannerMode.CONTAINER -> parseContainer(visionText.text)
                    ScannerMode.DATA_PLATE -> parseDataPlate(visionText.text)
                }
                if (result != null && done.compareAndSet(false, true)) {
                    onSuccess(result)
                } else {
                    captureRequested.set(false) // nothing found — allow retry
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    companion object {
        fun parseContainer(text: String): Map<String, String>? {
            val cleaned = text.replace(Regex("\\s+"), "")
            val match = Regex("[A-Z]{4}[0-9]{7}").find(cleaned) ?: return null
            return mapOf("Container No." to match.value)
        }

        fun parseDataPlate(text: String): Map<String, String>? {
            val result = mutableMapOf<String, String>()
            Regex("""(69NT40[-\s]*\d{3}[-\s]*\d{3}|SCI-\d{2}-[A-Z]{2})""")
                .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let { result["Unit Model"] = it.replace(Regex("\\s+"), "") }
            Regex("""([A-Z]{3}[\s]?\d{8}|[A-Z]{2}\d{2}-\d{5})""")
                .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let { result["Unit Serial No."] = it.replace(Regex("\\s+"), "") }
            Regex("""(?:0[1-9]|1[0-2])/((?:19|20)\d{2})""")
                .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let { result["Year of Built"] = it }
            return result.takeIf { it.isNotEmpty() }
        }
    }
}
