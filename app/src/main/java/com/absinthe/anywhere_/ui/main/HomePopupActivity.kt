package com.absinthe.anywhere_.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.absinthe.anywhere_.BaseActivity
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.constants.Const
import com.absinthe.anywhere_.constants.EventTag
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.databinding.ActivityHomePopupBinding
import com.absinthe.anywhere_.services.overlay.CollectorService
import com.absinthe.anywhere_.services.overlay.HomeFloatService
import com.absinthe.anywhere_.services.overlay.ICollectorService
import com.absinthe.anywhere_.ui.settings.SettingsActivity
import com.absinthe.anywhere_.viewmodel.AnywhereViewModel
import com.absinthe.libraries.utils.extensions.dp
import com.blankj.utilcode.util.ActivityUtils
import com.microsoft.appcenter.analytics.Analytics
import java.lang.ref.WeakReference

/**
 * Lightweight home screen used by the "popup" home mode.
 *
 * It only contains the grouped card grid and the settings shortcut: no drawer, no ViewPager and
 * no fragments. The window itself is transparent, so the launcher stays visible behind the
 * floating panel.
 *
 * This activity is also the target of the launcher aliases. Deep links, shares and the full screen
 * home mode are forwarded to [MainActivity] without inflating the popup layout.
 */
class HomePopupActivity : BaseActivity<ActivityHomePopupBinding>() {

  private lateinit var controller: CardGridController
  private lateinit var customizePanel: CustomizePanel
  private val viewModel by viewModels<AnywhereViewModel>()
  private var appliedHomeMode = Const.HOME_MODE_POPUP
  private var splashDismissRunnable: Runnable? = null
  private var forwarding = false
  private var panelHidden = false
  private var hiding = false
  private var layoutReady = false
  private var finishAfterHandoff = false
  private var isBound = false
  private var collectorService: ICollectorService? = null

  private val hideHandler = Handler(Looper.getMainLooper())

  /**
   * Releases the activity shortly after it was hidden. Keeping it warm makes showing it again
   * instant, but it must not stay in memory forever, so it is finished after a short delay.
   */
  private val releaseRunnable = Runnable {
    if (!isFinishing && !isDestroyed &&
      !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    ) {
      finish()
    }
  }

  private val collectorConnection = object : ServiceConnection {
    override fun onServiceDisconnected(name: ComponentName?) {
      isBound = false
      collectorService = null
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      isBound = true
      collectorService = ICollectorService.Stub.asInterface(service)
      collectorService?.startCollector()
      ActivityUtils.startHomeActivity()
    }
  }

  override fun setViewBinding(): ActivityHomePopupBinding? {
    // Forwarding launches do not need the popup layout at all, so skip the inflation.
    return if (forwarding) null else ActivityHomePopupBinding.inflate(layoutInflater)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    forwarding = shouldForward()
    super.onCreate(savedInstanceState)
    if (forwarding) {
      instance = WeakReference(this)
      handOffToMain(forwardIntent())
      return
    }
    instance = WeakReference(this)
    HomeFloatService.startIfEnabled(this)
    splashDismissRunnable =
      SplashAnimation.play(this, savedInstanceState, binding.splashOverlay, binding.ivSplash)
  }

  override fun onStart() {
    super.onStart()
    hideHandler.removeCallbacks(releaseRunnable)
  }

  override fun onStop() {
    super.onStop()
    if (!isFinishing && !isChangingConfigurations) {
      scheduleRelease()
    }
  }

  override fun initView() {
    if (forwarding) {
      return
    }
    layoutReady = true
    appliedHomeMode = GlobalValues.homeMode

    controller = CardGridController(
      context = this,
      lifecycleOwner = this,
      lifecycleScope = lifecycleScope,
      recyclerView = binding.rvCards,
      category = null,
      applySystemBarPadding = false
    )
    controller.setup()

    applyPanelBackground()

    customizePanel = CustomizePanel(this, binding.btnPopupCustomize) { startCollector() }

    binding.btnPopupCustomize.setOnClickListener {
      customizePanel.toggle()
    }

    binding.btnPopupSettings.setOnClickListener {
      startActivity(Intent(this, SettingsActivity::class.java))
    }

    binding.btnPopupFullscreen.setOnClickListener {
      enterFullscreen()
    }

    // Tapping outside the panel dismisses the popup, just like a system dialog.
    binding.root.setOnClickListener { closePopup() }
    binding.homePanel.setOnClickListener { /* Consume clicks inside the panel. */ }

    animatePanelIn()
  }

  /** Switches the saved home mode to full screen and opens the full home activity. */
  private fun enterFullscreen() {
    GlobalValues.homeMode = Const.HOME_MODE_FULLSCREEN
    appliedHomeMode = Const.HOME_MODE_FULLSCREEN
    handOffToMain(Intent(this, MainActivity::class.java))
  }

  /**
   * Starts [intent] and marks this activity for a safe finish once the target is resumed.
   * Finishing immediately while this activity is the task root can remove the whole task before
   * the new activity is attached, which makes the full screen home appear to do nothing.
   */
  private fun handOffToMain(intent: Intent) {
    finishAfterHandoff = true
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
  }

  /**
   * Rebuilds the panel background so the transparency chosen in settings is applied. The panel is
   * drawn with a rounded rectangle instead of the static drawable to support a runtime alpha.
   */
  private fun applyPanelBackground() {
    val alpha = GlobalValues.popupTransparency.coerceIn(0, 100) * 255 / 100
    val base = ContextCompat.getColor(this, R.color.home_popup_base)
    val strokeBase = if (isNightMode()) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    val background = GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 24.dp.toFloat()
      setColor(ColorUtils.setAlphaComponent(base, alpha))
      setStroke(1.dp, ColorUtils.setAlphaComponent(strokeBase, 26))
    }
    binding.homePanel.background = background
  }

  /**
   * Fades the panel in. The activity is kept warm after hiding, so this also runs when the popup
   * comes back from the background and makes the reappearance feel instant.
   */
  private fun animatePanelIn() {
    if (!layoutReady) {
      return
    }
    val panel = binding.homePanel
    panel.animate().cancel()
    hideHandler.removeCallbacks(releaseRunnable)
    hiding = false
    panelHidden = false
    panel.scaleX = 1f
    panel.scaleY = 1f
    val duration = panelDuration(show = true)
    if (duration == 0L) {
      panel.alpha = 1f
      return
    }
    panel.alpha = 0f
    panel.animate()
      .alpha(1f)
      .setDuration(duration)
      .setInterpolator(DecelerateInterpolator())
      .start()
  }

  /** Fades the panel out and runs [onEnd] afterwards. Only a short fade is used on purpose. */
  private fun animatePanelOut(onEnd: () -> Unit) {
    if (!layoutReady) {
      onEnd()
      return
    }
    val panel = binding.homePanel
    panel.animate().cancel()
    hiding = true
    panelHidden = true
    val duration = panelDuration(show = false)
    if (duration == 0L) {
      hiding = false
      onEnd()
      return
    }
    panel.animate()
      .alpha(0f)
      .setDuration(duration)
      .setInterpolator(AccelerateInterpolator())
      .withEndAction {
        hiding = false
        if (!isFinishing && !isDestroyed) {
          onEnd()
        }
      }
      .start()
  }

  /** Short, subtle durations: fast by default and never longer than the card animation speed. */
  private fun panelDuration(show: Boolean): Long {
    val base = GlobalValues.itemAnimDuration
    if (base <= 0L) {
      return 0L
    }
    return if (show) base.coerceIn(80L, 140L) else base.coerceIn(60L, 110L)
  }

  /** Schedules a delayed release so a backgrounded popup does not keep occupying memory. */
  private fun scheduleRelease() {
    hideHandler.removeCallbacks(releaseRunnable)
    hideHandler.postDelayed(releaseRunnable, HIDE_RELEASE_DELAY_MS)
  }

  /** Hides the popup but keeps the activity warm so the next tap shows it immediately. */
  private fun hidePopup() {
    if (::customizePanel.isInitialized) {
      customizePanel.dismiss()
    }
    animatePanelOut {
      moveTaskToBack(true)
      // Free the activity after a short while so it does not occupy memory in the background.
      scheduleRelease()
    }
  }

  /** Fades the panel out and closes the activity (outside tap / back key). */
  private fun closePopup() {
    if (hiding) {
      return
    }
    hideHandler.removeCallbacks(releaseRunnable)
    if (::customizePanel.isInitialized) {
      customizePanel.dismiss()
    }
    animatePanelOut { finish() }
  }

  private fun startCollector() {
    viewModel.startCollector(
      this,
      object : AnywhereViewModel.OnStartCollectorListener {
        override fun onStart() {
          if (isBound) {
            collectorService?.startCollector()
            ActivityUtils.startHomeActivity()
          } else {
            bindService(
              Intent(this@HomePopupActivity, CollectorService::class.java),
              collectorConnection,
              Context.BIND_AUTO_CREATE
            )
          }
        }
      })
    Analytics.trackEvent(EventTag.FAB_COLLECTOR_CLICK)
  }

  private fun shouldForward(): Boolean {
    if (intent?.getBooleanExtra(EXTRA_FROM_FLOAT, false) == true) {
      return false
    }
    val action = intent?.action
    val isLauncherIntent = action == null || action == Intent.ACTION_MAIN
    return !isLauncherIntent || GlobalValues.homeMode != Const.HOME_MODE_POPUP
  }

  private fun forwardIntent(): Intent {
    val action = intent?.action
    val isLauncherIntent = action == null || action == Intent.ACTION_MAIN
    return if (isLauncherIntent) {
      Intent(this, MainActivity::class.java)
    } else {
      val source = intent ?: Intent(this, MainActivity::class.java)
      Intent(source).setClass(this, MainActivity::class.java)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    // A deep link or share opened while the popup is already showing must go to MainActivity.
    if (shouldForward()) {
      handOffToMain(forwardIntent())
    }
  }

  override fun onResume() {
    super.onResume()
    if (isFinishing || !::controller.isInitialized) {
      return
    }
    applyPanelBackground()
    if (panelHidden && !hiding) {
      animatePanelIn()
    }
    if (GlobalValues.homeMode != appliedHomeMode) {
      appliedHomeMode = GlobalValues.homeMode
      if (appliedHomeMode != Const.HOME_MODE_POPUP) {
        handOffToMain(Intent(this, MainActivity::class.java))
      }
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (::controller.isInitialized) {
      controller.updateLayoutManager(newConfig)
    }
  }

  override fun onBackPressed() {
    closePopup()
  }

  override fun onDestroy() {
    if (instance?.get() === this) {
      instance = null
    }
    hideHandler.removeCallbacks(releaseRunnable)
    if (::customizePanel.isInitialized) {
      customizePanel.dismiss()
    }
    splashDismissRunnable?.let { binding.splashOverlay.removeCallbacks(it) }
    splashDismissRunnable = null
    super.onDestroy()
  }

  companion object {
    /** Marks an intent that was fired from the floating ball and must always show the popup. */
    const val EXTRA_FROM_FLOAT = "extra_from_float"

    /** How long the hidden popup stays warm before it is finished to free memory. */
    private const val HIDE_RELEASE_DELAY_MS = 30_000L

    private var instance: WeakReference<HomePopupActivity>? = null

    /**
     * Called by [MainActivity] once it is resumed. The popup can only be finished safely after the
     * full screen home is attached; finishing earlier removes the whole task on some devices.
     */
    fun finishHandedOff() {
      val activity = instance?.get() ?: return
      if (activity.finishAfterHandoff && !activity.isFinishing && !activity.isDestroyed) {
        activity.finishAfterHandoff = false
        activity.finish()
      }
    }

    /**
     * Shows the popup when it is hidden, or hides it when it is currently in the foreground.
     * Called by the floating ball overlay.
     */
    fun toggle(context: Context) {
      val activity = instance?.get()
      if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
        if (activity.hiding) {
          return
        }
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
          if (activity.panelHidden) {
            activity.animatePanelIn()
          } else {
            activity.hidePopup()
          }
        } else {
          activity.startActivity(
            Intent(activity, HomePopupActivity::class.java).apply {
              addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
              putExtra(EXTRA_FROM_FLOAT, true)
            }
          )
        }
      } else {
        context.startActivity(
          Intent(context, HomePopupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_FROM_FLOAT, true)
          }
        )
      }
    }
  }
}
