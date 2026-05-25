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

        // Carrier 69NT40-511-353 or Star Cool SCI-40-CA
        Regex("""(69NT40[-\s]*\d{3}[-\s]*\d{3}|SCI-\d{2}-[A-Z]{2})""")
            .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?.let { result["Unit Model"] = it.replace(Regex("\\s+"), "") }

        // Carrier WSC61225053 (3 letters + optional space + 8 digits)
        // or Star Cool AA00-00000 (2 letters + 2 digits + hyphen + 5 digits)
        Regex("""([A-Z]{3}[\s]?\d{8}|[A-Z]{2}\d{2}-\d{5})""")
            .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?.let { result["Unit Serial No."] = it.replace(Regex("\\s+"), "") }

        // MM/YYYY → extract only the 4-digit year
        Regex("""(?:0[1-9]|1[0-2])/((?:19|20)\d{2})""")
            .find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?.let { result["Year of Built"] = it }

        if (result.isNotEmpty() && resultEmitted.compareAndSet(false, true)) {
            onSuccess(result)
        }
    }
}