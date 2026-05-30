package io.github.dashLauncher.gesture

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlin.math.abs

class GestureHandler(context: Context) {

    private val density = context.resources.displayMetrics.density
    private val swipeZoneHeight = (density * 120).toInt()
    private val screenHeight = context.resources.displayMetrics.heightPixels

    private var velocityTracker: VelocityTracker? = null
    private var startX = 0f
    private var startY = 0f

    private var inkBuilder = Ink.builder()
    private var strokeBuilder = Ink.Stroke.builder()
    private var isSwipeGesture = false
    private var isBackspaceGesture = false
    private var gestureDecided = false

    var onSwipeUp: (() -> Unit)? = null
    var onInkStrokeAdded: ((Ink) -> Unit)? = null
    var onDrawPoint: ((Float, Float, Boolean) -> Unit)? = null // x, y, isStart

    // -------------------------------------------------------------------------
    // BACKSPACE GESTURE — detection layer
    // Responsibility: recognise a right-to-left swipe and invoke onBackspace.
    // Do NOT add any text-state or ink-state mutation here.
    // Ink cleanup and ViewModel state update happen in DrawingOverlay (orchestration)
    // and LauncherViewModel (state) respectively — see those files.
    // -------------------------------------------------------------------------
    var onBackspace: (() -> Unit)? = null

    fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker = velocityTracker ?: VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isSwipeGesture = false
                isBackspaceGesture = false
                gestureDecided = false
                strokeBuilder = Ink.Stroke.builder()
                strokeBuilder.addPoint(
                    Ink.Point.create(event.x, event.y, System.currentTimeMillis())
                )
                onDrawPoint?.invoke(event.x, event.y, true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!gestureDecided) {
                    val dy = startY - event.y
                    val dx = event.x - startX
                    val absDx = abs(dx)
                    val absDy = abs(dy)
                    val startedInSwipeZone = startY > screenHeight - swipeZoneHeight

                    // --- BACKSPACE DETECTION: gesture classification rules ---
                    // Each branch locks gestureDecided only when the gesture is
                    // unambiguous. The backspace branch intentionally waits for the
                    // full density-scaled distance so a slow leftward scribble stroke
                    // is never prematurely classified as ink before the threshold is
                    // reached. Do NOT lower the backspace threshold or merge these
                    // branches — that is what caused the original "registers as dot" bug.
                    when {
                        // Swipe-up: lock in as soon as upward movement is dominant
                        startedInSwipeZone && dy > 0 && absDy > 30 && absDy > absDx * 1.5f -> {
                            isSwipeGesture = true
                            gestureDecided = true
                        }
                        // Backspace: RIGHT-TO-LEFT swipe.
                        // Threshold: ~100 dp (density-scaled). Must be horizontally dominant
                        // (absDx > absDy * 2) to avoid treating diagonal strokes as backspace.
                        // WARNING: do not reduce this threshold or remove the horizontal-dominance
                        // check — doing so re-introduces misclassification of ink strokes.
                        dx < -(100 * density) && absDx > absDy * 2f -> {
                            isBackspaceGesture = true
                            gestureDecided = true
                        }
                        // Ink: moved enough AND direction rules out a left swipe
                        (absDy > 30 || absDx > 30) && (dx >= 0 || absDy >= absDx) -> {
                            gestureDecided = true
                        }
                        // Still ambiguous: leftward but below backspace threshold.
                        // Keep accumulating points without locking gestureDecided.
                    }
                    // --- END BACKSPACE DETECTION ---
                }

                // Accumulate stroke points whether the gesture is decided-as-ink or still
                // ambiguous. If ACTION_UP reveals this was a backspace/swipe, the stroke
                // is discarded there and gestureHandler.reset() cleans up strokeBuilder.
                if (!isSwipeGesture && !isBackspaceGesture) {
                    strokeBuilder.addPoint(
                        Ink.Point.create(event.x, event.y, System.currentTimeMillis())
                    )
                    onDrawPoint?.invoke(event.x, event.y, false)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isSwipeGesture) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = velocityTracker?.yVelocity ?: 0f
                    if (vy < -200) onSwipeUp?.invoke()
                } else if (isBackspaceGesture) {
                    // Invoke the callback wired by DrawingOverlay — see that file for
                    // ink cleanup and canvas reset before the ViewModel is notified.
                    onBackspace?.invoke()
                } else {
                    strokeBuilder.addPoint(
                        Ink.Point.create(event.x, event.y, System.currentTimeMillis())
                    )
                    inkBuilder.addStroke(strokeBuilder.build())
                    onInkStrokeAdded?.invoke(inkBuilder.build())
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    fun reset() {
        inkBuilder = Ink.builder()
        // reset strokeBuilder so partial points from an interrupted stroke are not
        // carried into the next character's ink when reset fires mid-draw
        strokeBuilder = Ink.Stroke.builder()
        isSwipeGesture = false
        isBackspaceGesture = false
        gestureDecided = false
    }
}