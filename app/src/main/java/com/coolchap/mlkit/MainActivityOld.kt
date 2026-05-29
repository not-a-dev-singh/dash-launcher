/*
package io.github.not-a-dev-singh

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink

class MainActivityOld : AppCompatActivity() {

    private lateinit var canvas: DrawingCanvas
    private lateinit var resultText: TextView
    private lateinit var recognizer: DigitalInkRecognizer
    private var modelReady = false
    private val TAG = "DigitalInkPOC"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        canvas = findViewById(R.id.drawingCanvas)
        resultText = findViewById(R.id.resultText)
        val clearBtn = findViewById<Button>(R.id.clearButton)

        // Pick the on-device model from current device locale (example: "en-US").
        val languageTag = Locale.getDefault().toLanguageTag()
        setupRecognizer(languageTag)

        canvas.onStrokeFinished = { ink ->
            if (modelReady) {
                recognize(ink)
            }
        }

        clearBtn.setOnClickListener {
            canvas.clear()
            resultText.text = if (modelReady) {
                "Draw something..."
            } else {
                "Model not ready yet"
            }
        }
    }

    private fun setupRecognizer(languageTag: String) {
        modelReady = false
        canvas.isEnabled = false
        resultText.text = "Downloading model for $languageTag..."

        val modelIdentifier = DigitalInkRecognitionModelIdentifier
            .fromLanguageTag(languageTag)

        if (modelIdentifier == null) {
            Log.e(TAG, "Unsupported language tag: $languageTag")
            resultText.text = "Unsupported language: $languageTag"
            return
        }

        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()

        // Download model if needed
        val remoteModelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
        remoteModelManager.download(model, com.google.mlkit.common.model.DownloadConditions.Builder().build())
            .addOnSuccessListener {
                modelReady = true
                canvas.isEnabled = true
                resultText.text = "Model ready for $languageTag. Draw something..."
                Log.d(TAG, "Model ready for $languageTag")
            }
            .addOnFailureListener {
                modelReady = false
                canvas.isEnabled = false
                resultText.text = "Model download failed: ${it.message}"
                Log.e(TAG, "Download failed: ${it.message}")
            }

        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
    }

    private fun recognize(ink: Ink) {
        recognizer.recognize(ink)
            .addOnSuccessListener { result ->
                val candidates = result.candidates
                if (candidates.isEmpty()) {
                    resultText.text = "No result"
                    return@addOnSuccessListener
                }
                // Log top 3 for POC validation
                candidates.take(3).forEachIndexed { i, c ->
                    Log.d(TAG, "Candidate $i: '${c.text}' score=${c.score}")
                }
                resultText.text = "Top: ${candidates[0].text}"
            }
            .addOnFailureListener {
                Log.e(TAG, "Recognition failed: ${it.message}")
                resultText.text = "Error: ${it.message}"
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::recognizer.isInitialized) {
            recognizer.close()
        }
    }
}*/
