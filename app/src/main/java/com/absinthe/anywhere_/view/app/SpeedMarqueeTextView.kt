package com.absinthe.anywhere_.view.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import com.absinthe.anywhere_.constants.GlobalValues

/**
 * A single-line [AppCompatTextView] that scrolls its content when the text is wider than the view.
 *
 * Unlike the platform marquee, the scrolling speed follows the "Animation speed" setting.
 *
 * The text is laid out left-aligned inside a very wide layout (required for horizontal
 * scrolling), so horizontal centering and scrolling are both applied in [onDraw] instead of
 * relying on the platform's scroll position.
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
    // Center the text when it fits; otherwise scroll it from the leading edge.
    val centerOffset = if (centerWhenFits && contentWidth <= availableWidth) {
      (availableWidth - contentWidth) / 2f
    } else {
      0f
    }

    canvas.save()
    canvas.translate(centerOffset - marqueeOffset, 0f)
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
        invalidate()
      }
      start()
    }
  }

  private fun stopMarquee() {
    animator?.cancel()
    animator = null
    marqueeOffset = 0f
    invalidate()
  }

  private companion object {
    /** Minimum time a single scrolling pass takes so near-fitting names do not jump to the end. */
    const val MIN_SCROLL_DURATION_MS = 1000L
  }
}
