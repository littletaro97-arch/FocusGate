package com.example.focusgate

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/** Small dependency-free wrapping layout for selectable app-name chips. */
class CompactChipFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    private val horizontalGap = dp(8)
    private val verticalGap = dp(8)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        var lineWidth = 0
        var lineHeight = 0
        var contentHeight = 0

        children().forEach { child ->
            child.measure(
                MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            val childWidth = child.measuredWidth
            if (lineWidth > 0 && lineWidth + horizontalGap + childWidth > availableWidth) {
                contentHeight += lineHeight + verticalGap
                lineWidth = 0
                lineHeight = 0
            }
            lineWidth += if (lineWidth == 0) childWidth else horizontalGap + childWidth
            lineHeight = maxOf(lineHeight, child.measuredHeight)
        }
        if (lineWidth > 0) contentHeight += lineHeight

        setMeasuredDimension(
            resolveSize(availableWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(contentHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val availableWidth = (right - left - paddingLeft - paddingRight).coerceAtLeast(0)
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0

        children().forEach { child ->
            if (x > paddingLeft && x + horizontalGap + child.measuredWidth > paddingLeft + availableWidth) {
                x = paddingLeft
                y += lineHeight + verticalGap
                lineHeight = 0
            }
            if (x > paddingLeft) x += horizontalGap
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth
            lineHeight = maxOf(lineHeight, child.measuredHeight)
        }
    }

    private fun children(): Sequence<View> = sequence {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != GONE) yield(child)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
