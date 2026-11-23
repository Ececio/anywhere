package com.absinthe.anywhere_.view.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import com.absinthe.anywhere_.constants.GlobalValues
import kotlin.math.floor

/**
 * A single-line [AppCompatTextView] that scrolls its content when the text is wider than the view.
 *
 * Unlike the platform marquee, the scrolling speed follows the "Animation speed" setting.
 *
 * The text is laid out left-aligned inside a very wide layout (required for horizontal
 * scrolling). Scrolling therefore uses the platform scroll position: [TextView] clips its
 * content to a fixed window in view coordinates, so translating the canvas would shrink that
 * window from the right instead of panning the text inside it. Only the sub-pixel remainder of
 * the offset is applied as a canvas translation to keep the motion smooth.
 */
class SpeedMarqueeTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

  /** Whether text that fits the view should be centered instead of left-aligned. */
  var centerWhenFits = true

  private var animator: ValueAnimator? = null
  private var marqueeOffset = 0f
  private var marqueeText: String? = null

  init {
    isSingleLine = true
    setHorizontallyScrolling(true)
    ellipsize = null
    gravity = Gravity.START or Gravity.CENTER_VERTICAL
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    startMarquee()
  }

  override fun onDetachedFromWindow() {
    stopMarquee()
    super.onDetachedFromWindow()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    startMarquee()
  }

  override fun setText(text: CharSequence?, type: BufferType?) {
    super.setText(text, type)
    // RecyclerView rebinds an item with the same name whenever something else about the card
    // changes. Restarting the marquee then would make the text jump back to the start, which
    // looks like a speed change, so only restart when the name really changed.
    val newText = text?.toString()
    if (newText != marqueeText) {
      marqueeText = newText
      startMarquee()
    }
  }

  override fun onDraw(canvas: Canvas) {
    val contentWidth = contentWidth()
    val availableWidth = width - compoundPaddingLeft - compoundPaddingRight
    // Center the text when it fits. Scrolling instead pans the text through [scrollX].
    val centered = centerWhenFits && contentWidth <= availableWidth && scrollX == 0
    if (!centered) {
      // Apply the fraction of a pixel that [scrollX] cannot represent, so the text keeps moving
      // smoothly instead of jumping one pixel at a time at slow speeds.
      val fraction = marqueeOffset - floor(marqueeOffset)
      if (fraction == 0f) {
        super.onDraw(canvas)
      } else {
        canvas.save()
        canvas.translate(-fraction, 0f)
        super.onDraw(canvas)
        canvas.restore()
      }
      return
    }

    val centerOffset = (availableWidth - contentWidth) / 2f
    canvas.save()
    canvas.translate(centerOffset, 0f)
    super.onDraw(canvas)
    canvas.restore()
  }

  private fun contentWidth(): Float {
    return paint.measureText(text?.toString().orEmpty())
  }

  private fun startMarquee() {
    stopMarquee()

    val speed = GlobalValues.marqueeSpeedDpPerSecond
    if (speed <= 0f || width == 0) {
      return
    }

    val contentWidth = contentWidth()
    val availableWidth = width - compoundPaddingLeft - compoundPaddingRight
    if (contentWidth <= availableWidth) {
      return
    }

    val density = resources.displayMetrics.density
    // Scroll just far enough to reveal the end, then hold there before starting over. A constant
    // speed keeps every card scrolling at the same pace no matter how long its name is; the small
    // minimum only prevents near-fitting names from jumping to the end in a single frame.
    val distance = contentWidth - availableWidth
    val scrollDuration = maxOf((distance / (speed * density) * 1000).toLong(), MIN_SCROLL_DURATION_MS)
    if (scrollDuration <= 0) {
      return
    }
    val pauseDuration = 1600L
    val totalDuration = scrollDuration + pauseDuration

    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = totalDuration
      repeatCount = ValueAnimator.INFINITE
      interpolator = LinearInterpolator()
      addUpdateListener {
        val elapsed = (it.animatedFraction * totalDuration).toLong()
        marqueeOffset = if (elapsed < scrollDuration) {
          distance * elapsed / scrollDuration
        } else {
          distance
        }
        // [TextView] keeps its clipping window fixed when the view is scrolled, unlike a canvas
        // translation, which would clip away the tail of the text.
        scrollTo(marqueeOffset.toInt(), 0)
        invalidate()
      }
      start()
    }
  }

  private fun stopMarquee() {
    animator?.cancel()
    animator = null
    marqueeOffset = 0f
    scrollTo(0, 0)
    invalidate()
  }

  private companion object {
    /** Minimum time a single scrolling pass takes so near-fitting names do not jump to the end. */
    const val MIN_SCROLL_DURATION_MS = 1000L
  }
}
