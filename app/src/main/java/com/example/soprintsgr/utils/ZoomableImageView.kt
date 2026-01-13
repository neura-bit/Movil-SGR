package com.example.soprintsgr.utils

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.GestureDetector
import android.view.View
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), View.OnTouchListener {

    private var matrix_transform = Matrix()
    private var savedMatrix = Matrix()
    private var mode = NONE
    private var start = PointF()
    private var mid = PointF()
    private var oldDist = 1f

    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var gestureDetector: GestureDetector? = null

    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
    }

    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetZoom()
                return true
            }
        })
        this.setOnTouchListener(this)
        this.scaleType = ScaleType.MATRIX
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val scaleDetector = scaleGestureDetector ?: return false
        
        scaleDetector.onTouchEvent(event)
        gestureDetector?.onTouchEvent(event)
        
        val currentPoint = PointF(event.x, event.y)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix_transform)
                start.set(event.x, event.y)
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(matrix_transform)
                    midPoint(mid, event)
                    mode = ZOOM
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    matrix_transform.set(savedMatrix)
                    matrix_transform.postTranslate(event.x - start.x, event.y - start.y)
                }
            }
        }
        
        // Ensure the image stays within bounds (basic implementation)
        // For production apps, advanced boundary checking is recommended
        
        this.imageMatrix = matrix_transform
        return true
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var scaleFactor = detector.scaleFactor
            
            // Limit zoom scale
            val values = FloatArray(9)
            matrix_transform.getValues(values)
            val currentScale = values[Matrix.MSCALE_X]
            
            if (currentScale * scaleFactor < 0.5f) {
                scaleFactor = 0.5f / currentScale
            } else if (currentScale * scaleFactor > 5.0f) {
                scaleFactor = 5.0f / currentScale
            }
            
            if (scaleFactor == 1.0f) return true
            
            matrix_transform.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    }
    
    // Reset zoom when image changes
    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }
    
    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        resetZoom()
    }
    
    private fun resetZoom() {
        matrix_transform.reset()
        imageMatrix = matrix_transform
        mode = NONE
        // Center crop/fit logic could be added here if needed, keeping it simple for now
        // Usually fitting to center is good initial state
        fitToScreen()
    }

    private fun fitToScreen() {
        val d = drawable ?: return
        val imageW = d.intrinsicWidth.toFloat()
        val imageH = d.intrinsicHeight.toFloat()
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        
        if (viewW <= 0 || viewH <= 0) return

        matrix_transform.reset()
        
        val scale: Float
        var dx = 0f
        var dy = 0f

        if (imageW * viewH > viewW * imageH) {
            scale = viewW / imageW
            dy = (viewH - imageH * scale) * 0.5f
        } else {
            scale = viewH / imageH
            dx = (viewW - imageW * scale) * 0.5f
        }

        matrix_transform.setScale(scale, scale)
        matrix_transform.postTranslate(dx, dy)
        imageMatrix = matrix_transform
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitToScreen()
    }
}
