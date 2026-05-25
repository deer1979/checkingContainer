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
    private val resultEmitted = AtomicBoolean(false)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (resultEmitted.get()) { imageProxy.close(); return }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { visionText ->
                when (mode) {
                    ScannerMode.CONTAINER -> processContainer(visionText.text)
                    ScannerMode.DATA_PLATE -> processDataPlate(visionText.text)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun processContainer(text: String) {
        val cleaned = text.replace(Regex("\\s+"), "")
        val match = Regex("[A-Z]{4}[0-9]{7}").find(cleaned) ?: return
        if (resultEmitted.compareAndSet(false, true)) {
            onSuccess(mapOf("Container No." to match.value))
        }
    }

    private fun processDataPlate(text: String) {
        val result = mutableMapOf<String, String>()

        Regex("""(?:UNIT MODEL|MACHINE MODEL|MODEL NO\.|MODEL)[\s:]*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?.let { result["Unit Model"] = it }

        Regex("""(?:UNIT SERIAL NO\.?|SERIAL NO\.?|S/N)[\s:]*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?.let { result["Unit Serial No."] = it }

        Regex("""(?:MFG\.?\s*DATE|DATE OF MANUFACTURE|BUILT)[\s:]*([0-9]{2}/[0-9]{4}|[0-9]{4})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?.let { result["Year of Built"] = it.takeLast(4) }

        if (result.isNotEmpty() && resultEmitted.compareAndSet(false, true)) {
            onSuccess(result)
        }
    }
}