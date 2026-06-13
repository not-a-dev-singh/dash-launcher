package io.github.dashLauncher.ui

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
import com.google.mlkit.vision.digitalink.recognition.Ink
import io.github.dashLauncher.gesture.GestureHandler
import io.github.dashLauncher.recognition.InkRecognitionManager
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
    // When this returns true, the overlay stops intercepting and forwarding touch events
    // so that child gesture detectors (e.g. drag-to-reorder on pinned slots) can operate
    // without DrawingOverlay consuming their move events after the 8dp slop threshold.
    // Using a lambda (not a plain Boolean) so the coroutine reads the CURRENT value at
    // event time — a plain Boolean captured at composition time would be stale because
    // rememberUpdatedState only syncs after the next recomposition frame, which may not
    // have run yet when the first drag MOVE event arrives after onDragStart fires.
    isInputSuspended: () -> Boolean = { false },
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val gestureHandler = remember { GestureHandler(context) }
    val points = remember { mutableStateListOf<DrawPoint>() }
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 8.dp.toPx() }

    val idleHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // rememberUpdatedState ensures the Runnable always calls the latest lambda reference
    // even when the parent recomposes with a new onCommitActiveScribble instance
    val currentOnCommitActiveScribble = rememberUpdatedState(onCommitActiveScribble)
    val idleRunnable = remember {
        Runnable {
            points.clear()
            gestureHandler.reset()
            currentOnCommitActiveScribble.value()
        }
    }
    // holds the latest Ink before the debounced recognize call fires
    val pendingInk = remember { arrayOfNulls<Ink>(1) }
    val recognizeRunnable = remember {
        Runnable { pendingInk[0]?.let { inkManager.recognize(it) } }
    }

    LaunchedEffect(recognizedText) {
        if (recognizedText.isEmpty()) {
            points.clear()
            gestureHandler.reset()
            idleHandler.removeCallbacks(idleRunnable)
        }
    }

    // SideEffect runs after every recomposition, keeping gesture callbacks fresh
    // so a new lambda reference from the parent is never stale inside GestureHandler
    SideEffect {
        gestureHandler.onSwipeUp = onSwipeUp
        // Wrap onBackspace: inkBuilder still holds all strokes from the active session.
        // Without resetting it, the next scribble after a backspace would append to
        // stale ink and ML Kit would try to recognise old-text + new-stroke, causing
        // recognition to regress after every consecutive backspace.        
        gestureHandler.onBackspace = {
            idleHandler.removeCallbacks(recognizeRunnable)
            //   1. Cancel pending recognize/idle timers — prevent a stale recognition
            //      result from arriving after the character has already been removed.
            idleHandler.removeCallbacks(idleRunnable)
            //   2. Clear pendingInk — drop any buffered ink that hasn't been sent yet.
            pendingInk[0] = null
            //   3. Clear canvas draw-points — erase the visible stroke trail.
            points.clear()
            // WARNING: do NOT reorder these steps or skip the reset() call.
            // Skipping reset() is what caused "consecutive backspaces always fail":
            gestureHandler.reset()  // clears inkBuilder so next scribble starts fresh
            onBackspace()
        }
    }

    LaunchedEffect(Unit) {
        gestureHandler.onDrawPoint = { x, y, isStart ->
            points.add(DrawPoint(x, y, isStart))
        }

        gestureHandler.onInkStrokeAdded = { ink ->
            // 150ms debounce before recognizing: lets multi-stroke characters (e.g. 'i', 't')
            // finish their second stroke before recognition fires, reducing partial results
            pendingInk[0] = ink
            idleHandler.removeCallbacks(recognizeRunnable)
            idleHandler.postDelayed(recognizeRunnable, 150)
            // separate 1s idle timer to commit the full scribbled word
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
        // Unit key: Compose won't cancel in-flight touch tracking when recognizedText
        // updates mid-stroke; callbacks stay current via SideEffect above
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val downChange = downEvent.changes.first()
                    val startPos = downChange.position
                    
                    // Force interception if there is active, uncommitted ink on the canvas.
                    // This ensures that mid-scribble taps (e.g. dotting an "i") are treated as drawing,
                    // while taps after the idle timer commits and clears the ink are treated as clicks.
                    var isIntercepted = points.isNotEmpty()
                    if (isIntercepted) {
                        downChange.consume()
                    }

                    val downMotionEvent = downEvent.motionEvent
                    // Skip forwarding while a pin drag is active — prevents the overlay
                    // from classifying the drag start as an ink stroke.
                    if (downMotionEvent != null && !isInputSuspended()) {
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

                        // Do not intercept while a pin drag is active — this is the critical
                        // guard. Without it, the first 8dp of finger movement after a long-press
                        // sets isIntercepted = true and DrawingOverlay consumes all subsequent
                        // move events, silently aborting detectDragGesturesAfterLongPress.
                        if (!isIntercepted && !isInputSuspended() &&
                            (diffX > touchSlopPx || diffY > touchSlopPx)
                        ) {
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