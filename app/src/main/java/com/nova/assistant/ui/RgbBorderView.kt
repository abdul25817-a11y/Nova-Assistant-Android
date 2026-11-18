package com.nova.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

class RgbBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        alpha = 70
    }
    private val rect = RectF()
    private val matrix = Matrix()
    private var gradient: LinearGradient? = null
    private var animator: ValueAnimator? = null
    private var phase = 0f

    private val colors = intArrayOf(
        0xFF00E5FF.toInt(),
        0xFF2979FF.toInt(),
        0xFF9C27B0.toInt(),
        0xFFFF4081.toInt(),
        0xFFFF1744.toInt(),
        0xFFFFD600.toInt(),
        0xFF00E676.toInt(),
        0xFF00E5FF.toInt()
    )

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val smallest = w.coerceAtMost(h).toFloat()
        val stroke = (smallest * 0.008f).coerceIn(3f, 7f)
        val half = stroke / 2f + 1f
        rect.set(half, half, w - half, h - half)

        strokePaint.strokeWidth = stroke
        glowPaint.strokeWidth = stroke * 3.5f

        gradient = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            colors, null, Shader.TileMode.REPEAT
        )
        strokePaint.shader = gradient
        glowPaint.shader = gradient
    }

    fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 7000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                phase = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shader = gradient ?: return
        matrix.setTranslate(phase * width.coerceAtLeast(1), 0f)
        shader.setLocalMatrix(matrix)
        val radius = (width.coerceAtMost(height) * 0.045f).coerceIn(16f, 42f)
        canvas.drawRoundRect(rect, radius, radius, glowPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
