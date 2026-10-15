package com.myPage.pdfGenerator.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.myPage.pdfGenerator.R

class CompressLevelBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Level(val index: Int, val title: String, val quality: Int, val scaleFactor: Float) {
        LOW(0, "Low", 35, 0.5f),
        MEDIUM(1, "Medium", 65, 0.75f),
        HIGH(2, "High", 85, 1.0f)
    }

    private var currentLevel: Level = Level.HIGH
    private var onLevelChangeListener: ((Level) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.compress_track)
        strokeCap = Paint.Cap.ROUND
    }

    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.compress_active)
        strokeCap = Paint.Cap.ROUND
    }

    private val pointInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.compress_point_inactive)
        style = Paint.Style.FILL
    }

    private val pointActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.compress_active)
        style = Paint.Style.FILL
    }

    private val pointWhiteInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    fun setLevel(level: Level) {
        if (currentLevel != level) {
            currentLevel = level
            invalidate()
            onLevelChangeListener?.invoke(level)
        }
    }

    fun getLevel(): Level = currentLevel

    fun setOnLevelChangeListener(listener: (Level) -> Unit) {
        this.onLevelChangeListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (65 * resources.displayMetrics.density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padLeft = this.paddingLeft + (28 * resources.displayMetrics.density)
        val padRight = this.paddingRight + (28 * resources.displayMetrics.density)
        val availableWidth = (width - padLeft - padRight).coerceAtLeast(10f)
        val lineY = height * 0.42f
        val lineStroke = 7 * resources.displayMetrics.density
        val pointRadius = 11 * resources.displayMetrics.density
        val innerWhiteRadius = 4.5f * resources.displayMetrics.density

        trackPaint.strokeWidth = lineStroke
        activeTrackPaint.strokeWidth = lineStroke

        val x0 = padLeft
        val x1 = padLeft + availableWidth / 2f
        val x2 = width - padRight
        val pointsX = floatArrayOf(x0, x1, x2)

        // Draw background line (entire track)
        canvas.drawLine(x0, lineY, x2, lineY, trackPaint)

        // Draw filled/active line up to selected level
        val activeEndX = pointsX[currentLevel.index]
        if (currentLevel.index > 0) {
            canvas.drawLine(x0, lineY, activeEndX, lineY, activeTrackPaint)
        }

        // Draw 3 points and text labels
        val levels = Level.values()
        for (i in 0..2) {
            val px = pointsX[i]
            val isFilled = i <= currentLevel.index
            val isSelected = i == currentLevel.index

            if (isFilled) {
                canvas.drawCircle(px, lineY, if (isSelected) pointRadius * 1.15f else pointRadius, pointActivePaint)
                canvas.drawCircle(px, lineY, innerWhiteRadius, pointWhiteInnerPaint)
            } else {
                canvas.drawCircle(px, lineY, pointRadius, pointInactivePaint)
                canvas.drawCircle(px, lineY, innerWhiteRadius, pointWhiteInnerPaint)
            }

            // Draw label below point
            val label = levels[i].title
            textPaint.color = if (isSelected) {
                ContextCompat.getColor(context, R.color.compress_active)
            } else {
                ContextCompat.getColor(context, R.color.text_secondary)
            }
            textPaint.isFakeBoldText = isSelected
            canvas.drawText(label, px, lineY + pointRadius + (20 * resources.displayMetrics.density), textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val padLeft = this.paddingLeft + (28 * resources.displayMetrics.density)
            val padRight = this.paddingRight + (28 * resources.displayMetrics.density)
            val availableWidth = (width - padLeft - padRight).coerceAtLeast(10f)

            val x0 = padLeft
            val x1 = padLeft + availableWidth / 2f
            val x2 = width - padRight
            val pointsX = floatArrayOf(x0, x1, x2)

            val touchX = event.x.coerceIn(0f, width.toFloat())
            // Find closest level point
            var closestLevel = Level.HIGH
            var minDiff = Float.MAX_VALUE
            for (i in 0..2) {
                val diff = Math.abs(touchX - pointsX[i])
                if (diff < minDiff) {
                    minDiff = diff
                    closestLevel = Level.values()[i]
                }
            }

            if (closestLevel != currentLevel) {
                currentLevel = closestLevel
                invalidate()
                onLevelChangeListener?.invoke(currentLevel)
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
