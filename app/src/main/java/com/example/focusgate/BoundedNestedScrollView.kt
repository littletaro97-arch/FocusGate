package com.example.focusgate

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.widget.NestedScrollView

/** A nested list that grows naturally until its explicit viewport cap, then scrolls. */
class BoundedNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : NestedScrollView(context, attrs) {
    var maxViewportHeightPx: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val parentSize = View.MeasureSpec.getSize(heightMeasureSpec)
        val cap = when (parentMode) {
            View.MeasureSpec.UNSPECIFIED -> maxViewportHeightPx
            else -> minOf(parentSize, maxViewportHeightPx)
        }
        val boundedHeightSpec = if (cap == Int.MAX_VALUE) {
            heightMeasureSpec
        } else {
            View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, boundedHeightSpec)
    }
}
