package io.github.not-a-dev-singh.gesture

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlin.math.abs

class GestureHandler(context: Context) {

    private val swipeZoneHeight = (context.resources.displayMetrics.density * 120).toInt()
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
    var onBackspace: (() -> Unit)? = null
    var onInkStrokeAdded: ((Ink) -> Unit)? = null
    var onDrawPoint: ((Float, Float, Boolean) -> Unit)? = null // x, y, isStart

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
                    
                    val movedEnough = absDy > 30 || absDx > 30

                    if (movedEnough) {
                        gestureDecided = true
                        
                        // Swipe Up (Bottom zone)
                        val startedInSwipeZone = startY > screenHeight - swipeZoneHeight
                        isSwipeGesture = startedInSwipeZone && dy > 0 && absDy > absDx * 1.5f
                        
                        // Backspace Swipe: strictly Right to Left (dx is negative)
                        // Using a threshold for horizontal dominance
                        isBackspaceGesture = dx < -100 && absDx > absDy * 2f
                    }
                }

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
        isSwipeGesture = false
        isBackspaceGesture = false
        gestureDecided = false
    }
}