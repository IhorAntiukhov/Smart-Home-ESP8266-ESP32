package com.arduinoworld.smarthome

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.FloatRange
import kotlin.math.min

class CircularProgressBar : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val rect = RectF()
    private val startAngle = -90f
    private val maxAngle = 360f
    private var maxProgress = 100f
    private var progress = 0f
    private var lastProgress = 0f
    private var diameter = 0f

    private val progressPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val backgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        drawCircle(maxAngle, canvas, backgroundPaint)
        drawCircle(progress, canvas, progressPaint)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        diameter = min(width, height).toFloat()
        updateRect()
    }

    private fun updateRect() {
        val strokeWidth = backgroundPaint.strokeWidth
        rect.set(strokeWidth, strokeWidth, diameter - strokeWidth, diameter - strokeWidth)
    }

    private fun drawCircle(angle: Float, canvas: Canvas, paint: Paint) {
        canvas.drawArc(rect, startAngle, angle, false, paint)
    }

    private fun calculateAngle(progress: Float) = maxAngle / maxProgress * progress

    fun setProgress(@FloatRange(from = 0.0, to = 100.0) progress: Float) {
        val valueAnimator = ValueAnimator.ofFloat(lastProgress, progress)
        valueAnimator.duration = 750
        valueAnimator.addUpdateListener { animation ->
            this.progress = calculateAngle(animation.animatedValue as Float)
            invalidate()
        }
        valueAnimator.addListener(object: Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                lastProgress = progress
            }

            override fun onAnimationEnd(animation: Animator) {}

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}

        })
        valueAnimator.repeatCount = 0
        valueAnimator.start()
    }

    fun setMaxProgress(maxProgress: Float) {
        this.maxProgress = maxProgress
    }

    fun setProgressColor(color: Int) {
        progressPaint.color = color
        invalidate()
    }

    fun setProgressBackgroundColor(color: Int) {
        backgroundPaint.color = color
        invalidate()
    }

    fun setProgressWidth(width: Float) {
        progressPaint.strokeWidth = width
        backgroundPaint.strokeWidth = width
        updateRect()
        invalidate()
    }

    fun setRounded(rounded: Boolean) {
        progressPaint.strokeCap = if (rounded) Paint.Cap.ROUND else Paint.Cap.BUTT
        invalidate()
    }
}