package com.example.focusgate

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.NumberPicker
import kotlin.math.abs

enum class PickerGestureOwner { UNDECIDED, PAGE, PICKER }

object CompactPickerGesturePolicy {
    fun isInteractiveStart(y: Float, height: Int): Boolean =
        height > 0 && y in height * 0.12f..height * 0.88f

    fun resolve(deltaX: Float, deltaY: Float, touchSlop: Int): PickerGestureOwner {
        if (maxOf(abs(deltaX), abs(deltaY)) <= touchSlop) return PickerGestureOwner.UNDECIDED
        return if (abs(deltaY) >= abs(deltaX)) PickerGestureOwner.PICKER else PickerGestureOwner.PAGE
    }
}

class GuardedNumberPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : NumberPicker(context, attrs) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var ownsGesture = false
    private var dragging = false

    var onDraggingChanged: ((Boolean) -> Unit)? = null
    var onGestureResolved: ((String) -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                ownsGesture = CompactPickerGesturePolicy.isInteractiveStart(event.y, height)
                if (ownsGesture) parent?.requestDisallowInterceptTouchEvent(true)
                if (!ownsGesture) {
                    onGestureResolved?.invoke("page")
                    return false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = abs(event.x - downX)
                val deltaY = abs(event.y - downY)
                if (ownsGesture && !dragging) {
                    when (CompactPickerGesturePolicy.resolve(deltaX, deltaY, touchSlop)) {
                    PickerGestureOwner.PICKER -> {
                        dragging = true
                        onDraggingChanged?.invoke(true)
                        onGestureResolved?.invoke("picker")
                    }
                    PickerGestureOwner.PAGE -> {
                        ownsGesture = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        onGestureResolved?.invoke("page")
                        return false
                    }
                    PickerGestureOwner.UNDECIDED -> Unit
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> releaseGesture()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        releaseGesture()
        super.onDetachedFromWindow()
    }

    private fun releaseGesture() {
        parent?.requestDisallowInterceptTouchEvent(false)
        if (dragging) onDraggingChanged?.invoke(false)
        dragging = false
        ownsGesture = false
    }
}
