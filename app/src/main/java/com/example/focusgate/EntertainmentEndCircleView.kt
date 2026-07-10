package com.example.focusgate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

class EntertainmentEndCircleView(context: Context) : View(context) {
    var accessibleClickAction: (() -> Unit)? = null
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 32, 37, 41)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = resources.getDimension(R.dimen.entertainment_end_text_size)
        isFakeBoldText = true
    }
    private val ringBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 204, 102)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val textBounds = Rect()
    private var progress = 0f
    private var holding = false

    fun setHoldProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        holding = progress > 0f
        invalidate()
    }

    fun clearHoldProgress() {
        progress = 0f
        holding = false
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        accessibleClickAction?.invoke()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = width.coerceAtMost(height).toFloat()
        val center = size / 2f
        val radius = center - ringBackPaint.strokeWidth - 1f
        canvas.drawCircle(center, center, radius, fillPaint)
        if (holding) {
            canvas.drawCircle(center, center, radius, ringBackPaint)
            canvas.drawArc(
                center - radius,
                center - radius,
                center + radius,
                center + radius,
                -90f,
                progress * 360f,
                false,
                ringPaint
            )
        }
        val text = "结束"
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, center, center - textBounds.exactCenterY(), textPaint)
    }
}
