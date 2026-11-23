package com.absinthe.anywhere_.services.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.ui.main.HomePopupActivity
import com.absinthe.anywhere_.utils.NotifyUtils
import com.absinthe.anywhere_.view.home.HomeFloatView
import com.blankj.utilcode.util.PermissionUtils
import timber.log.Timber

/**
 * Keeps a draggable floating ball on top of other apps. Tapping the ball shows or hides the home
 * popup, so the home screen can be reached without going back to the launcher.
 *
 * When the foreground service option is enabled the service runs with a persistent, low priority
 * notification, which makes the system far less likely to kill it while the app is in the
 * background. The option can be turned off in settings.
 */
class HomeFloatService : Service() {

  private lateinit var windowManager: WindowManager
  private var floatView: HomeFloatView? = null

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // "Stop" action from the foreground notification.
    if (intent?.action == ACTION_STOP) {
      GlobalValues.homeFloat = false
      stopSelf()
      return START_NOT_STICKY
    }

    if (!GlobalValues.homeFloat || !PermissionUtils.isGrantedDrawOverlays()) {
      // Satisfy the startForegroundService contract before stopping in case the state changed
      // between the start request and this callback.
      if (GlobalValues.homeFloatForeground) {
        NotifyUtils.createHomeFloatNotification(this)
      }
      stopSelf()
      return START_NOT_STICKY
    }

    if (GlobalValues.homeFloatForeground) {
      NotifyUtils.createHomeFloatNotification(this)
    } else {
      NotifyUtils.cancelHomeFloatNotification(this)
    }

    if (floatView == null) {
      addFloatView()
    }
    return START_STICKY
  }

  private fun addFloatView() {
    val view = HomeFloatView(this).apply {
      setOnClickListener { HomePopupActivity.toggle(this@HomeFloatService) }
    }
    try {
      windowManager.addView(view, view.windowParams)
      floatView = view
      Timber.d("HomeFloat addView.")
    } catch (e: Exception) {
      Timber.e(e)
      stopSelf()
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    // Rotation changes the screen width, so keep the ball glued to its edge instead of
    // leaving it in the middle of the screen.
    floatView?.onScreenSizeChanged()
  }

  override fun onDestroy() {
    floatView?.let {
      runCatching { windowManager.removeView(it) }
    }
    floatView = null
    NotifyUtils.cancelHomeFloatNotification(this)
    Timber.d("HomeFloat removeView.")
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val ACTION_STOP = "com.absinthe.anywhere_.action.STOP_HOME_FLOAT"

    fun start(context: Context) {
      if (!GlobalValues.homeFloat || !PermissionUtils.isGrantedDrawOverlays()) {
        return
      }
      val intent = Intent(context, HomeFloatService::class.java)
      runCatching {
        if (GlobalValues.homeFloatForeground) {
          ContextCompat.startForegroundService(context, intent)
        } else {
          context.startService(intent)
        }
      }.onFailure { Timber.e(it) }
    }

    fun stop(context: Context) {
      runCatching { context.stopService(Intent(context, HomeFloatService::class.java)) }
        .onFailure { Timber.e(it) }
    }

    /** Starts the floating ball only when it is enabled and the overlay permission is granted. */
    fun startIfEnabled(context: Context) {
      if (GlobalValues.homeFloat && PermissionUtils.isGrantedDrawOverlays()) {
        start(context)
      }
    }
  }
}
