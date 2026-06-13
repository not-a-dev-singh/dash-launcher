package io.github.dashLauncher.gesture

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlin.math.abs
import kotlin.math.hypot

class GestureHandler(context: Context) {

    private val density = context.resources.displayMetrics.density
    private val swipeZoneHeight = (density * 120).toInt()
    private val screenHeight = context.resources.displayMetrics.heightPixels

    private var velocityTracker: VelocityTracker? = null
    private var startX = 0f
    private var startY = 0f
    private var lastTrackedX = 0f
    private var lastTrackedY = 0f
    private var totalPathLength = 0f

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
                lastTrackedX = event.x
                lastTrackedY = event.y
                totalPathLength = 0f
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

                    when {
                        // Swipe-up: lock in as soon as upward movement is dominant
                        startedInSwipeZone && dy > 0 && absDy > 30 && absDy > absDx * 1.5f -> {
                            isSwipeGesture = true
                            gestureDecided = true
                        }
                        // Ink: moved enough AND direction rules out a left swipe
                        (absDy > 30 || absDx > 30) && (dx >= 0 || absDy >= absDx) -> {
                            gestureDecided = true
                        }
                        // Still ambiguous: leftward but below backspace threshold.
                        // Keep accumulating points without locking gestureDecided.
                    }
                }

                // Accumulate stroke points whether the gesture is decided-as-ink or still
                // ambiguous. If ACTION_UP reveals this was a backspace/swipe, the stroke
                // is discarded there and gestureHandler.reset() cleans up strokeBuilder.
                if (!isSwipeGesture && !isBackspaceGesture) {
                    val dist = hypot(event.x - lastTrackedX, event.y - lastTrackedY)
                    totalPathLength += dist
                    lastTrackedX = event.x
                    lastTrackedY = event.y

                    strokeBuilder.addPoint(
                        Ink.Point.create(event.x, event.y, System.currentTimeMillis())
                    )
                    onDrawPoint?.invoke(event.x, event.y, false)
                }
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f
                val dx = event.x - startX
                val dy = event.y - startY
                val absDx = abs(dx)
                val absDy = abs(dy)

                val dist = hypot(event.x - lastTrackedX, event.y - lastTrackedY)
                totalPathLength += dist

                val straightLineDistance = hypot(dx, dy)

                // A gesture is classified as a backspace ONLY if:
                // 1. It is horizontally leftward (dx < 0)
                // 2. It is horizontally dominant (absDx > absDy * 2f)
                val isStraightLine = straightLineDistance > 0 && totalPathLength < straightLineDistance * 1.3f

                // Backspace is classified ONLY on ACTION_UP based on the final completed stroke.
                // It must be leftward (dx < 0), horizontally dominant (absDx > absDy * 2f),
                // and exceed either a deliberate long distance (80 dp) or a quick flick distance/velocity (35 dp / vx < -700f).
                val isBackspace = dx < 0 &&
                        absDx > absDy * 2f &&
                        isStraightLine &&
                        (absDx > 80 * density || (absDx > 35 * density && vx < -700f))

                if (isSwipeGesture) {
                    if (vy < -200) onSwipeUp?.invoke()
                } else if (isBackspace || isBackspaceGesture) {
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