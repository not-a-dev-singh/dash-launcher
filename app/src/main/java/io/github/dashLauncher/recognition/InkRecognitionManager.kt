package io.github.dashLauncher.recognition

import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import io.github.dashLauncher.ModelDownloadStatus
import java.util.Locale

class InkRecognitionManager {

    private var recognizer: DigitalInkRecognizer? = null
    private val TAG = "InkRecognition"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var downloadRunnable: Runnable? = null
    private var downloadStartElapsed = 0L
    private val estimatedDownloadWindowMs = 45_000L

    var onResults: ((List<String>) -> Unit)? = null
    var onModelReady: (() -> Unit)? = null
    var onDownloadStatusChanged: ((ModelDownloadStatus) -> Unit)? = null

    fun initialize(languageTag: String = "en-US") {
        val modelId = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            ?: return

        val model = DigitalInkRecognitionModel.builder(modelId).build()
        val languageLabel = "English (US)"

        onDownloadStatusChanged?.invoke(
            ModelDownloadStatus(
                isDownloading = true,
                isReady = false,
                message = "Preparing $languageLabel handwriting model",
                etaText = "Estimated remaining: calculating..."
            )
        )

        RemoteModelManager.getInstance()
            .isModelDownloaded(model)
            .addOnSuccessListener { downloaded ->
                if (downloaded) {
                    recognizer = DigitalInkRecognition.getClient(
                        DigitalInkRecognizerOptions.builder(model).build()
                    )
                    stopDownloadTicker()
                    onDownloadStatusChanged?.invoke(
                        ModelDownloadStatus(
                            isDownloading = false,
                            isReady = true,
                            message = "$languageLabel handwriting model ready"
                        )
                    )
                    onModelReady?.invoke()
                    Log.d(TAG, "Model ready")
                } else {
                    startDownloadTicker(languageLabel)
                    RemoteModelManager.getInstance()
                        .download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            recognizer = DigitalInkRecognition.getClient(
                                DigitalInkRecognizerOptions.builder(model).build()
                            )
                            stopDownloadTicker()
                            onDownloadStatusChanged?.invoke(
                                ModelDownloadStatus(
                                    isDownloading = false,
                                    isReady = true,
                                    message = "$languageLabel handwriting model ready"
                                )
                            )
                            onModelReady?.invoke()
                            Log.d(TAG, "Model ready")
                        }
                        .addOnFailureListener { exception ->
                            stopDownloadTicker()
                            onDownloadStatusChanged?.invoke(
                                ModelDownloadStatus(
                                    isDownloading = false,
                                    isReady = false,
                                    message = "$languageLabel handwriting model failed to download",
                                    errorText = exception.message ?: "Unknown error"
                                )
                            )
                            Log.e(TAG, "Model download failed: ${exception.message}")
                        }
                }
            }
            .addOnFailureListener { exception ->
                startDownloadTicker(languageLabel)
                RemoteModelManager.getInstance()
                    .download(model, DownloadConditions.Builder().build())
                    .addOnSuccessListener {
                        recognizer = DigitalInkRecognition.getClient(
                            DigitalInkRecognizerOptions.builder(model).build()
                        )
                        stopDownloadTicker()
                        onDownloadStatusChanged?.invoke(
                            ModelDownloadStatus(
                                isDownloading = false,
                                isReady = true,
                                message = "$languageLabel handwriting model ready"
                            )
                        )
                        onModelReady?.invoke()
                        Log.d(TAG, "Model ready")
                    }
                    .addOnFailureListener { downloadException ->
                        stopDownloadTicker()
                        onDownloadStatusChanged?.invoke(
                            ModelDownloadStatus(
                                isDownloading = false,
                                isReady = false,
                                message = "$languageLabel handwriting model failed to download",
                                errorText = downloadException.message ?: "Unknown error"
                            )
                        )
                        Log.e(
                            TAG,
                            "Model availability check failed: ${exception.message}; " +
                                "download failed: ${downloadException.message}"
                        )
                    }
            }
    }

    // monotonically increasing counter; used to discard results from earlier strokes
    // that arrive after a newer recognition has already been dispatched
    private var lastRecognitionSeq = 0

    fun recognize(ink: Ink) {
        val seq = ++lastRecognitionSeq
        recognizer?.recognize(ink)
            ?.addOnSuccessListener { result ->
                // a higher seq means a newer call was made; this result is stale
                if (seq < lastRecognitionSeq) return@addOnSuccessListener
                val candidates = result.candidates.map { it.text }
                onResults?.invoke(candidates)
            }
            ?.addOnFailureListener { Log.e(TAG, "Recognition error: ${it.message}") }
    }

    fun close() {
        stopDownloadTicker()
        recognizer?.close()
    }

    private fun startDownloadTicker(languageLabel: String) {
        downloadStartElapsed = SystemClock.elapsedRealtime()
        downloadRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.elapsedRealtime() - downloadStartElapsed
                val remaining = (estimatedDownloadWindowMs - elapsed).coerceAtLeast(0L)

                onDownloadStatusChanged?.invoke(
                    ModelDownloadStatus(
                        isDownloading = true,
                        isReady = false,
                        message = "Downloading $languageLabel handwriting model",
                        etaText = "Estimated remaining: ${formatDuration(remaining)}"
                    )
                )

                mainHandler.postDelayed(this, 1000L)
            }
        }

        downloadRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopDownloadTicker() {
        downloadRunnable?.let { mainHandler.removeCallbacks(it) }
        downloadRunnable = null
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0) {
            String.format(Locale.US, "%dm %02ds", minutes, seconds)
        } else {
            String.format(Locale.US, "%ds", seconds)
        }
    }
}
