package com.absinthe.anywhere_.ui.preview

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal image view with pinch / double-tap zoom and one-finger panning.
 *
 * The drawable is always fitted inside the view when [userScale] is 1, and panning is clamped so
 * the image never leaves a gap on the sides that are larger than the view.
 */
class ZoomImageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

  private val transform = Matrix()
  private var userScale = 1f
  private var offsetX = 0f
  private var offsetY = 0f

  private var lastX = 0f
  private var lastY = 0f
  private var activePointerId = MotionEvent.INVALID_POINTER_ID

  private val scaleDetector = ScaleGestureDetector(
    context,
    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
      override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
      }

      override fun onScale(detector: ScaleGestureDetector): Boolean {
        val oldScale = userScale
        val target = (oldScale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (abs(target - oldScale) < 0.001f) return true
        zoomBy(target / oldScale, detector.focusX, detector.focusY)
        return true
      }
    }
  )

  private val gestureDetector = GestureDetector(
    context,
    object : GestureDetector.SimpleOnGestureListener() {
      override fun onDown(e: MotionEvent): Boolean = true

      override fun onDoubleTap(e: MotionEvent): Boolean {
        val target = if (userScale > 1.05f) MIN_SCALE else DOUBLE_TAP_SCALE
        zoomBy(target / userScale, e.x, e.y)
        parent?.requestDisallowInterceptTouchEvent(userScale > 1.01f)
        return true
      }
    }
  )

  init {
    scaleType = ScaleType.MATRIX
  }

  override fun setImageDrawable(drawable: Drawable?) {
    super.setImageDrawable(drawable)
    resetTransform()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    resetTransform()
  }

  fun resetTransform() {
    userScale = MIN_SCALE
    offsetX = 0f
    offsetY = 0f
    updateTransform()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    scaleDetector.onTouchEvent(event)
    gestureDetector.onTouchEvent(event)

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        lastX = event.x
        lastY = event.y
        activePointerId = event.getPointerId(0)
        parent?.requestDisallowInterceptTouchEvent(userScale > 1.01f)
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        parent?.requestDisallowInterceptTouchEvent(true)
      }
      MotionEvent.ACTION_MOVE -> {
        if (!scaleDetector.isInProgress && userScale > 1.01f) {
          val index = event.findPointerIndex(activePointerId)
          if (index >= 0) {
            val x = event.getX(index)
            val y = event.getY(index)
            offsetX += x - lastX
            offsetY += y - lastY
            lastX = x
            lastY = y
            clampOffsets()
            updateTransform()
          }
        }
      }
      MotionEvent.ACTION_POINTER_UP -> {
        val index = event.actionIndex
        if (event.getPointerId(index) == activePointerId) {
          val newIndex = if (index == 0) 1 else 0
          activePointerId = event.getPointerId(newIndex)
          lastX = event.getX(newIndex)
          lastY = event.getY(newIndex)
        }
      }
      MotionEvent.ACTION_UP -> {
        parent?.requestDisallowInterceptTouchEvent(false)
        performClick()
      }
      MotionEvent.ACTION_CANCEL -> {
        parent?.requestDisallowInterceptTouchEvent(false)
      }
    }
    return true
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  private val baseScale: Float
    get() {
      val drawable = drawable ?: return 1f
      val imageWidth = drawable.intrinsicWidth
      val imageHeight = drawable.intrinsicHeight
      if (imageWidth <= 0 || imageHeight <= 0 || width == 0 || height == 0) return 1f
      return min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
    }

  private fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
    val drawable = drawable ?: return
    val imageWidth = drawable.intrinsicWidth.toFloat()
    val imageHeight = drawable.intrinsicHeight.toFloat()
    if (imageWidth <= 0f || imageHeight <= 0f || width == 0 || height == 0) return

    val oldScale = baseScale * userScale
    if (oldScale <= 0f) return
    val newUserScale = (userScale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
    val newScale = baseScale * newUserScale
    val ratio = newScale / oldScale

    val oldLeft = (width - imageWidth * oldScale) / 2f
    val oldTop = (height - imageHeight * oldScale) / 2f
    val newLeft = (width - imageWidth * newScale) / 2f
    val newTop = (height - imageHeight * newScale) / 2f

    // Keep the content under the focal point stationary while scaling.
    offsetX = focusX - (focusX - oldLeft - offsetX) * ratio - newLeft
    offsetY = focusY - (focusY - oldTop - offsetY) * ratio - newTop
    userScale = newUserScale
    clampOffsets()
    updateTransform()
  }

  private fun clampOffsets() {
    val drawable = drawable ?: return
    val imageWidth = drawable.intrinsicWidth.toFloat()
    val imageHeight = drawable.intrinsicHeight.toFloat()
    if (imageWidth <= 0f || imageHeight <= 0f) return
    val scale = baseScale * userScale
    val maxX = max(0f, (imageWidth * scale - width) / 2f)
    val maxY = max(0f, (imageHeight * scale - height) / 2f)
    offsetX = offsetX.coerceIn(-maxX, maxX)
    offsetY = offsetY.coerceIn(-maxY, maxY)
  }

  private fun updateTransform() {
    val drawable = drawable ?: return
    val imageWidth = drawable.intrinsicWidth.toFloat()
    val imageHeight = drawable.intrinsicHeight.toFloat()
    if (imageWidth <= 0f || imageHeight <= 0f) return
    val scale = baseScale * userScale
    transform.setScale(scale, scale)
    transform.postTranslate(
      (width - imageWidth * scale) / 2f + offsetX,
      (height - imageHeight * scale) / 2f + offsetY
    )
    imageMatrix = transform
  }

  private companion object {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 6f
    const val DOUBLE_TAP_SCALE = 2.5f
  }
}
