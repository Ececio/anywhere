package com.absinthe.anywhere_.view.home

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.model.Settings
import com.absinthe.anywhere_.utils.AppUtils
import com.absinthe.libraries.utils.extensions.dp
import kotlin.math.abs

/**
 * The floating ball shown on top of other apps.
 *
 * Dragging moves the overlay window; a tap (without movement) is forwarded to the click listener,
 * which is used to show or hide the home popup.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class HomeFloatView(context: Context) : ImageView(context) {

  private val windowManager =
    context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

  /** Layout params used when this view is added to the [WindowManager]. */
  val windowParams: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
    width = 52.dp
    height = 52.dp
    x = 0
    y = 360.dp
    gravity = Gravity.TOP or Gravity.END
    format = PixelFormat.TRANSLUCENT
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    type = if (AppUtils.atLeastO()) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      WindowManager.LayoutParams.TYPE_PHONE
    }
  }

  private var downRawX = 0f
  private var downRawY = 0f
  private var startX = 0
  private var startY = 0
  private var dragging = false
  private var snapAnimator: ValueAnimator? = null
  private var screenWidth = 0
  private var screenHeight = 0

  init {
    setImageResource(R.drawable.ic_logo)
    imageTintList = ColorStateList.valueOf(Color.WHITE)
    background = createBackground()
    scaleType = ScaleType.FIT_CENTER
    val padding = 13.dp
    setPadding(padding, padding, padding, padding)
    contentDescription = context.getString(R.string.settings_home_float)
    setOnTouchListener { _, event -> handleTouch(event) }
  }

  /**
   * Builds the circular background with the accent color of the app theme. The service context
   * follows the system night mode, so the accent is resolved from the app's own mode instead.
   */
  private fun createBackground(): GradientDrawable {
    val accent = appAccentColor()
    return GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(ColorUtils.setAlphaComponent(accent, 0xE6))
      setStroke(1.dp, 0x40FFFFFF)
    }
  }

  private fun appAccentColor(): Int {
    val isNight = when (Settings.getTheme()) {
      AppCompatDelegate.MODE_NIGHT_YES -> true
      AppCompatDelegate.MODE_NIGHT_NO -> false
      else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    }
    val config = Configuration(resources.configuration).apply {
      uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        (if (isNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
    }
    val context = context.createConfigurationContext(config)
    return ContextCompat.getColor(context, R.color.colorAccent)
  }

  private fun handleTouch(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        snapAnimator?.cancel()
        snapAnimator = null
        refreshScreenSize()
        downRawX = event.rawX
        downRawY = event.rawY
        startX = windowParams.x
        startY = windowParams.y
        dragging = false
      }

      MotionEvent.ACTION_MOVE -> {
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
          dragging = true
        }
        if (dragging) {
          // gravity is TOP|END, so moving the finger right decreases x.
          windowParams.x = (startX - dx).toInt().coerceIn(0, maxX())
          windowParams.y = (startY + dy).toInt().coerceIn(0, maxY())
          updateViewLayout()
        }
      }

      MotionEvent.ACTION_UP -> {
        if (dragging) {
          // Let go near the middle and the ball still glides to the closest screen edge.
          snapToNearestEdge()
        } else {
          performClick()
        }
        dragging = false
      }

      MotionEvent.ACTION_CANCEL -> {
        if (dragging) {
          snapToNearestEdge()
        }
        dragging = false
      }
    }
    return true
  }

  /** Keeps the ball on screen and against its edge after a rotation or display size change. */
  fun onScreenSizeChanged() {
    snapAnimator?.cancel()
    snapAnimator = null
    refreshScreenSize()
    windowParams.x = windowParams.x.coerceIn(0, maxX())
    windowParams.y = windowParams.y.coerceIn(0, maxY())
    updateViewLayout()
  }

  /** Slides the ball to the left or right edge, whichever is closer. */
  private fun snapToNearestEdge() {
    val target = if (windowParams.x > maxX() / 2f) maxX() else 0
    if (windowParams.x == target) {
      return
    }

    val configured = GlobalValues.itemAnimDuration
    if (configured <= 0L) {
      windowParams.x = target
      updateViewLayout()
      return
    }

    snapAnimator?.cancel()
    snapAnimator = ValueAnimator.ofInt(windowParams.x, target).apply {
      duration = configured.coerceAtLeast(MIN_SNAP_DURATION_MS)
      interpolator = DecelerateInterpolator()
      addUpdateListener {
        windowParams.x = it.animatedValue as Int
        updateViewLayout()
      }
      start()
    }
  }

  private fun updateViewLayout() {
    runCatching { windowManager.updateViewLayout(this, windowParams) }
  }

  private fun refreshScreenSize() {
    val size = screenSize()
    screenWidth = size.x
    screenHeight = size.y
  }

  private fun maxX(): Int = (screenWidth - windowParams.width).coerceAtLeast(0)

  private fun maxY(): Int = (screenHeight - windowParams.height).coerceAtLeast(0)

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    updateGestureExclusion()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    updateGestureExclusion()
  }

  /**
   * The ball sits right on the screen edge, which is also where the system back gesture starts.
   * Excluding the ball from that gesture keeps it draggable even when it is flush with the edge.
   */
  private fun updateGestureExclusion() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && width > 0 && height > 0) {
      setSystemGestureExclusionRects(listOf(Rect(0, 0, width, height)))
    }
  }

  private fun screenSize(): Point {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val metrics = windowManager.currentWindowMetrics
      val bounds = metrics.bounds
      // The overlay window is laid out below the status bar, so its usable height is smaller
      // than the raw display height.
      val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
        WindowInsets.Type.statusBars()
      )
      Point(bounds.width(), bounds.height() - insets.top)
    } else {
      Point().also {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getSize(it)
      }
    }
  }

  override fun onDetachedFromWindow() {
    snapAnimator?.cancel()
    snapAnimator = null
    super.onDetachedFromWindow()
  }

  private companion object {
    /** A very short snap still needs to be visible; the setting can go down to 90 ms. */
    const val MIN_SNAP_DURATION_MS = 140L
  }
}
