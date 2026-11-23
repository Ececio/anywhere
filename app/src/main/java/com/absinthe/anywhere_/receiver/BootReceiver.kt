package com.absinthe.anywhere_.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.services.overlay.HomeFloatService
import timber.log.Timber

/**
 * Restores the floating ball after a reboot or an app update when the user enabled both the
 * floating ball and the boot start option.
 *
 * Starting a foreground service from [Intent.ACTION_BOOT_COMPLETED] and
 * [Intent.ACTION_MY_PACKAGE_REPLACED] is allowed by the platform, so no extra workaround is needed.
 */
class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
      return
    }
    if (!GlobalValues.homeFloat || !GlobalValues.homeFloatBoot) {
      return
    }
    Timber.d("Restore floating ball after %s.", action)
    HomeFloatService.start(context)
  }
}
