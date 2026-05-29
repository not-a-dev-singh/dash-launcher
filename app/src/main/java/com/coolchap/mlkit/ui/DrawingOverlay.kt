package io.github.not-a-dev-singh.ui

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.not-a-dev-singh.gesture.GestureHandler
import io.github.not-a-dev-singh.recognition.InkRecognitionManager
import kotlin.math.abs

data class DrawPoint(val x: Float, val y: Float, val isStart: Boolean)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingOverlay(
    modifier: Modifier = Modifier,
    inkManager: InkRecognitionManager,
    recognizedText: String,
    onSwipeUp: () -> Unit,
    onBackspace: () -> Unit,
    onCommitActiveScribble: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val gestureHandler = remember { GestureHandler(context) }
    val points = remember { mutableStateListOf<DrawPoint>() }
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 8.dp.toPx() }

    val idleHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val idleRunnable = remember {
        Runnable {
            points.clear()
            gestureHandler.reset()
            onCommitActiveScribble()
        }
    }

    LaunchedEffect(recognizedText) {
        if (recognizedText.isEmpty()) {
            points.clear()
            gestureHandler.reset()
            idleHandler.removeCallbacks(idleRunnable)
        }
    }

    LaunchedEffect(Unit) {
        gestureHandler.onSwipeUp = onSwipeUp
        gestureHandler.onBackspace = onBackspace

        gestureHandler.onDrawPoint = { x, y, isStart ->
            points.add(DrawPoint(x, y, isStart))
        }

        gestureHandler.onInkStrokeAdded = { ink ->
            inkManager.recognize(ink)
            idleHandler.removeCallbacks(idleRunnable)
            idleHandler.postDelayed(idleRunnable, 1000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            idleHandler.removeCallbacks(idleRunnable)
        }
    }

    Box(
        modifier = modifier.pointerInput(recognizedText) {
            awaitPointerEventScope {
                while (true) {
                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val downChange = downEvent.changes.first()
                    val startPos = downChange.position
                    var isIntercepted = false

                    val downMotionEvent = downEvent.motionEvent
                    if (downMotionEvent != null) {
                        idleHandler.removeCallbacks(idleRunnable)
                        gestureHandler.onTouchEvent(downMotionEvent)
                    }

                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull()

                        if (change == null || !change.pressed) {
                            if (change != null && isIntercepted) {
                                change.consume()
                                val upMotionEvent = event.motionEvent
                                if (upMotionEvent != null) {
                                    gestureHandler.onTouchEvent(upMotionEvent)
                                }
                            } else if (change != null) {
                                // Reset if it was a simple tap
                                points.clear()
                                gestureHandler.reset()
                            }
                            finished = true
                            break
                        }

                        val diffX = abs(change.position.x - startPos.x)
                        val diffY = abs(change.position.y - startPos.y)

                        if (!isIntercepted && (diffX > touchSlopPx || diffY > touchSlopPx)) {
                            isIntercepted = true
                        }

                        if (isIntercepted) {
                            change.consume()
                            val moveMotionEvent = event.motionEvent
                            if (moveMotionEvent != null) {
                                gestureHandler.onTouchEvent(moveMotionEvent)
                            }
                        }
                    }
                }
            }
        }
    ) {
        content()

        Canvas(modifier = Modifier.matchParentSize()) {
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
    }
}