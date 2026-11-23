package com.absinthe.anywhere_

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import cn.vove7.andro_accessibility_api.AccessibilityApi
import com.absinthe.anywhere_.constants.Const
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.database.AnywhereRepository
import com.absinthe.anywhere_.model.Settings
import com.absinthe.anywhere_.services.IzukoService
import com.absinthe.anywhere_.utils.manager.IzukoHelper.checkSignature
import com.absinthe.anywhere_.utils.manager.PoliceMan
import com.absinthe.anywhere_.utils.timber.ReleaseTree
import com.absinthe.anywhere_.utils.timber.ThreadAwareDebugTree
import com.absinthe.libraries.utils.utils.Utility
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import jonathanfinerty.once.Once
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.sui.Sui
import timber.log.Timber

class AnywhereApplication : Application() {

  override fun onCreate() {
    super.onCreate()

    if (BuildConfig.DEBUG) {
      Timber.plant(ThreadAwareDebugTree())
    } else {
      checkSignature()
      PoliceMan.checkApplicationClass(this)
      PoliceMan.checkPMProxy(this)
      Timber.plant(ReleaseTree())
      AppCenter.start(
        this, BuildConfig.APP_CENTER_SECRET,
        Analytics::class.java, Crashes::class.java
      )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      HiddenApiBypass.addHiddenApiExemptions("")
    }

    app = this
    Once.initialise(this)
    Settings.initMMKV(this)
    Settings.init()
    applyBuildDefaults()
    Utility.init(this)
    Sui.init(BuildConfig.APPLICATION_ID)
    AppCompatDelegate.setDefaultNightMode(Settings.getTheme())
    Global.start()
    sRepository = AnywhereRepository(this)

    AccessibilityApi.apply {
      BASE_SERVICE_CLS = IzukoService::class.java
      GESTURE_SERVICE_CLS = IzukoService::class.java
    }
  }

  /**
   * Applies this build's preferred defaults once, so that installations upgraded from an older
   * build also pick them up:
   * - execute commands with root
   * - group the home page by app
   * - use the compact card layout
   */
  private fun applyBuildDefaults() {
    val mmkv = GlobalValues.mmkv
    if (mmkv.decodeBool(Const.PREF_DEFAULTS_APPLIED, false)) {
      return
    }

    GlobalValues.workingMode = Const.WORKING_MODE_ROOT
    GlobalValues.groupMode = Const.GROUP_MODE_PACKAGE
    GlobalValues.cardMode = Const.PREF_CARD_MODE_SMALL

    // Keep the settings screen in sync with the values above.
    getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
      .edit()
      .putString(Const.PREF_WORKING_MODE, Const.WORKING_MODE_ROOT)
      .putString(Const.PREF_CARD_MODE, Const.PREF_CARD_MODE_SMALL)
      .apply()

    mmkv.encode(Const.PREF_DEFAULTS_APPLIED, true)
  }

  companion object {
    lateinit var sRepository: AnywhereRepository
    lateinit var app: AnywhereApplication
  }
}
