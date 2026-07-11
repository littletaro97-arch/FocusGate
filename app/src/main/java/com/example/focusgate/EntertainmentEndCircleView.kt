package com.example.focusgate

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class EntertainmentEndCircleView(context: Context) : View(context) {
    var accessibleClickAction: (() -> Unit)? = null
    private val density = resources.displayMetrics.density
    private val bodyRadius = 24f * density
    private val ringRadius = 27f * density
    private val shadowRadius = 4f * density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(shadowRadius, 0f, density, Color.argb(105, 0, 0, 0))
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
    private var visualScale = 1f
    private var scaleAnimator: ValueAnimator? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

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

    fun animateVisualScale(targetScale: Float, durationMs: Long) {
        val cleanTarget = targetScale.coerceIn(0.9f, 1.18f)
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(visualScale, cleanTarget).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                visualScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun releaseVisualResources() {
        scaleAnimator?.cancel()
        scaleAnimator = null
        fillPaint.shader = null
    }

    override fun performClick(): Boolean {
        super.performClick()
        accessibleClickAction?.invoke()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        if (fillPaint.shader == null) {
            fillPaint.shader = RadialGradient(
                centerX - bodyRadius * 0.22f,
                centerY - bodyRadius * 0.28f,
                bodyRadius * 1.45f,
                intArrayOf(
                    Color.argb(218, 73, 78, 82),
                    Color.argb(222, 31, 35, 39)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val outerRadius = ringRadius + ringPaint.strokeWidth / 2f
        val maxSafeScale = ((min(width, height) / 2f) - density) / outerRadius
        val safeScale = min(visualScale, maxSafeScale)
        canvas.save()
        canvas.scale(safeScale, safeScale, centerX, centerY)
        canvas.drawCircle(centerX, centerY, bodyRadius, fillPaint)
        if (holding) {
            canvas.drawCircle(centerX, centerY, ringRadius, ringBackPaint)
            canvas.drawArc(
                centerX - ringRadius,
                centerY - ringRadius,
                centerX + ringRadius,
                centerY + ringRadius,
                -90f,
                progress * 360f,
                false,
                ringPaint
            )
        }
        val text = "结束"
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, centerX, centerY - textBounds.exactCenterY(), textPaint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        releaseVisualResources()
        super.onDetachedFromWindow()
    }
}
