package io.github.not-a-dev-singh.recognition

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink

class InkRecognitionManager {

    private var recognizer: DigitalInkRecognizer? = null
    private val TAG = "InkRecognition"

    var onResults: ((List<String>) -> Unit)? = null
    var onModelReady: (() -> Unit)? = null

    fun initialize(languageTag: String = "en-US") {
        val modelId = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            ?: return

        val model = DigitalInkRecognitionModel.builder(modelId).build()

        RemoteModelManager.getInstance()
            .download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                recognizer = DigitalInkRecognition.getClient(
                    DigitalInkRecognizerOptions.builder(model).build()
                )
                onModelReady?.invoke()
                Log.d(TAG, "Model ready")
            }
            .addOnFailureListener { Log.e(TAG, "Model download failed: ${it.message}") }
    }

    fun recognize(ink: Ink) {
        recognizer?.recognize(ink)
            ?.addOnSuccessListener { result ->
                val candidates = result.candidates.map { it.text }
                onResults?.invoke(candidates)
            }
            ?.addOnFailureListener { Log.e(TAG, "Recognition error: ${it.message}") }
    }

    fun close() {
        recognizer?.close()
    }
}