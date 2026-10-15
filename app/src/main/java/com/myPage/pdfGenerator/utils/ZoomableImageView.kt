package com.myPage.pdfGenerator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val currentMatrix = Matrix()
    private var mode = NONE
    private val lastPoint = PointF()
    private var saveScale = 1f
    private val minScale = 1f
    private val maxScale = 4f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = currentMatrix
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var factor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= factor
            if (saveScale > maxScale) {
                saveScale = maxScale
                factor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                factor = minScale / origScale
            }

            if (saveScale > 1.05f) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            currentMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            fixTranslation()
            imageMatrix = currentMatrix
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (saveScale > 1.05f) {
                resetZoom()
            } else {
                saveScale = 2.5f
                fitToCenter()
                currentMatrix.postScale(saveScale, saveScale, e.x, e.y)
                fixTranslation()
                imageMatrix = currentMatrix
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            return true
        }
    }

    fun resetZoom() {
        saveScale = 1f
        fitToCenter()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val curr = PointF(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastPoint.set(curr)
                mode = DRAG
                if (saveScale > 1.05f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = ZOOM
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && saveScale > 1.05f) {
                    val deltaX = curr.x - lastPoint.x
                    val deltaY = curr.y - lastPoint.y
                    currentMatrix.postTranslate(deltaX, deltaY)
                    fixTranslation()
                    lastPoint.set(curr.x, curr.y)
                    imageMatrix = currentMatrix
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                if (saveScale <= 1.05f) {
                    resetZoom()
                }
            }
        }
        return true
    }

    private fun fixTranslation() {
        val m = FloatArray(9)
        currentMatrix.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]
        val scaleX = m[Matrix.MSCALE_X]
        val scaleY = m[Matrix.MSCALE_Y]

        val d = drawable ?: return
        val origW = d.intrinsicWidth * scaleX
        val origH = d.intrinsicHeight * scaleY
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        if (viewW <= 0 || viewH <= 0) return

        var fixTransX = 0f
        var fixTransY = 0f

        if (origW <= viewW) {
            fixTransX = (viewW - origW) / 2f - transX
        } else {
            if (transX > 0) fixTransX = -transX
            else if (transX < viewW - origW) fixTransX = viewW - origW - transX
        }

        if (origH <= viewH) {
            fixTransY = (viewH - origH) / 2f - transY
        } else {
            if (transY > 0) fixTransY = -transY
            else if (transY < viewH - origH) fixTransY = viewH - origH - transY
        }

        currentMatrix.postTranslate(fixTransX, fixTransY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitToCenter()
    }

    private fun fitToCenter() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return

        val bmWidth = d.intrinsicWidth.toFloat()
        val bmHeight = d.intrinsicHeight.toFloat()
        if (bmWidth <= 0 || bmHeight <= 0) return

        currentMatrix.reset()
        val scale = minOf(width.toFloat() / bmWidth, height.toFloat() / bmHeight)
        currentMatrix.setScale(scale, scale)

        val redundantYSpace = (height - scale * bmHeight) / 2f
        val redundantXSpace = (width - scale * bmWidth) / 2f
        currentMatrix.postTranslate(redundantXSpace, redundantYSpace)
        imageMatrix = currentMatrix
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        saveScale = 1f
        fitToCenter()
    }
}
