/*
package io.github.dashLauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import io.github.dashLauncher.gesture.GestureHandler
import io.github.dashLauncher.recognition.InkRecognitionManager
import com.google.mlkit.vision.digitalink.recognition.Ink

data class DrawPoint(val x: Float, val y: Float, val isStart: Boolean)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingOverlay(
    modifier: Modifier = Modifier,
    inkManager: InkRecognitionManager,
    onSwipeUp: () -> Unit,
    onClearCanvas: Boolean = false  // trigger clear from outside
) {
    val context = LocalContext.current
    val gestureHandler = remember { GestureHandler(context) }
    val points = remember { mutableStateListOf<DrawPoint>() }

    // Mirrors old DrawingCanvas: ink accumulates across strokes
    var inkBuilder = remember { Ink.builder() }
    var strokeBuilder = remember { Ink.Stroke.builder() }

    val idleHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val idleRunnable = remember {
        Runnable {
            // Fire recognition on full accumulated ink after 300ms idle
            inkManager.recognize(inkBuilder.build())
        }
    }

    // Clear canvas when triggered externally (e.g. user taps X)
    LaunchedEffect(onClearCanvas) {
        if (onClearCanvas) {
            points.clear()
            inkBuilder = Ink.builder()
            idleHandler.removeCallbacks(idleRunnable)
        }
    }

    LaunchedEffect(Unit) {
        gestureHandler.onSwipeUp = onSwipeUp

        gestureHandler.onDrawPoint = { x, y, isStart ->
            points.add(DrawPoint(x, y, isStart))
        }

        gestureHandler.onInkStrokeAdded = { ink ->
            // ink here is the full accumulated ink from GestureHandler
            // Reset 300ms timer on every new stroke (multi-char support)
            idleHandler.removeCallbacks(idleRunnable)
            idleHandler.postDelayed(idleRunnable, 300)
        }
    }

    Canvas(
        modifier = modifier.pointerInteropFilter { event ->
            gestureHandler.onTouchEvent(event)
            true
        }
    ) {
        for (i in 1 until points.size) {
            if (!points[i].isStart) {
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(points[i - 1].x, points[i - 1].y),
                    end = Offset(points[i].x, points[i].y),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}*/
