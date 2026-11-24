package com.absinthe.anywhere_.ui.shortcuts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.CallSuper
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.absinthe.anywhere_.AppBarActivity
import com.absinthe.anywhere_.BuildConfig
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.adapter.shortcut.ThirdAppsShortcutAdapter
import com.absinthe.anywhere_.adapter.shortcut.ThirdAppsShortcutItem
import com.absinthe.anywhere_.constants.Const
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.databinding.ActivityThirdAppsShortcutBinding
import com.absinthe.anywhere_.model.ThirdAppShortcut
import com.absinthe.anywhere_.utils.CommandUtils
import com.absinthe.anywhere_.utils.ShortcutDumpParser
import com.absinthe.anywhere_.utils.ToastUtil
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.widget.borderview.BorderView
import timber.log.Timber

class ThirdAppsShortcutActivity : AppBarActivity<ActivityThirdAppsShortcutBinding>() {

  override fun setViewBinding() = ActivityThirdAppsShortcutBinding.inflate(layoutInflater)

  override fun getToolBar() = binding.toolbar.toolBar

  override fun getAppBarLayout() = binding.toolbar.appBar

  private val mAdapter = ThirdAppsShortcutAdapter()

  private var apps: List<ResolveInfo> = emptyList()
  private var staticShortcuts: List<ThirdAppShortcut> = emptyList()
  private var dynamicShortcuts: List<ThirdAppShortcut> = emptyList()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding.list.apply {
      adapter = mAdapter
      layoutManager = LinearLayoutManager(this@ThirdAppsShortcutActivity)
      borderVisibilityChangedListener =
        BorderView.OnBorderVisibilityChangedListener { top: Boolean, _: Boolean, _: Boolean, _: Boolean ->
          getAppBarLayout().isLifted = !top
        }
    }

    apps = getResolveInfoList()

    val resultLauncher = registerForActivityResult(OpenCreateShortcut()) {
      it?.let {
        runCatching {
          startActivity(it)
        }.onFailure { t ->
          ToastUtil.Toasty.show(this, t.message ?: "Can not open this activity")
        }
      } ?: run {
        Timber.d("resultLauncher: intent is null")
      }
    }

    mAdapter.setOnItemClickListener { _, _, position ->
      when (val item = mAdapter.data.getOrNull(position)) {
        is ThirdAppsShortcutItem.App -> resultLauncher.launch(item.info)
        is ThirdAppsShortcutItem.Shortcut -> launchShortcut(item.shortcut)
        else -> Timber.d("Unclickable shortcut list item")
      }
    }

    binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
      if (isChecked) {
        showGroup(checkedId)
      }
    }

    binding.progressHorizontal.show()
    lifecycleScope.launch {
      val shortcuts = withContext(Dispatchers.IO) { loadShortcuts() }
      binding.progressHorizontal.hide()

      bindShortcuts(shortcuts)

      if (shortcuts == null) {
        ToastUtil.makeText(this@ThirdAppsShortcutActivity, R.string.shortcuts_load_failed)
      }
    }
  }

  private fun bindShortcuts(shortcuts: List<ThirdAppShortcut>?) {
    staticShortcuts = shortcuts.orEmpty().filter { it.kind == ShortcutDumpParser.Kind.STATIC }
    dynamicShortcuts = shortcuts.orEmpty().filter { it.kind == ShortcutDumpParser.Kind.DYNAMIC }

    updateTab(binding.btnStatic, R.string.shortcuts_tab_static, staticShortcuts.size)
    updateTab(binding.btnDynamic, R.string.shortcuts_tab_dynamic, dynamicShortcuts.size)
    updateTab(binding.btnApps, R.string.shortcuts_tab_apps, apps.size)

    val firstVisible = listOf(binding.btnStatic, binding.btnDynamic, binding.btnApps)
      .firstOrNull { it.isVisible }

    binding.toggleGroup.isVisible = firstVisible != null

    if (firstVisible != null) {
      binding.toggleGroup.check(firstVisible.id)
      showGroup(firstVisible.id)
    } else {
      mAdapter.setNewData(mutableListOf())
    }
  }

  private fun updateTab(button: MaterialButton, titleRes: Int, count: Int) {
    button.text = getString(titleRes, count)
    button.isVisible = count > 0
  }

  private fun showGroup(checkedId: Int) {
    val items: List<ThirdAppsShortcutItem> = when (checkedId) {
      R.id.btn_static -> staticShortcuts.map { ThirdAppsShortcutItem.Shortcut(it) }
      R.id.btn_dynamic -> dynamicShortcuts.map { ThirdAppsShortcutItem.Shortcut(it) }
      R.id.btn_apps -> apps.map { ThirdAppsShortcutItem.App(it) }
      else -> emptyList()
    }

    mAdapter.setNewData(items.toMutableList())
    binding.list.scrollToPosition(0)
  }

  private fun launchShortcut(shortcut: ThirdAppShortcut) {
    try {
      startActivity(shortcut.toIntent())
    } catch (t: Throwable) {
      Timber.w(t, "Can not launch shortcut %s/%s", shortcut.packageName, shortcut.id)
      val messageRes = when (t) {
        is ActivityNotFoundException -> R.string.toast_no_react_url
        is SecurityException -> R.string.toast_security_exception
        else -> R.string.toast_runtime_error
      }
      ToastUtil.makeText(this, messageRes)
    }
  }

  /**
   * Static and dynamic shortcuts of other apps are not readable through the public APIs unless
   * this app is the default launcher, so ask a root or Shizuku shell for the shortcut dump.
   */
  private fun loadShortcuts(): List<ThirdAppShortcut>? {
    if (GlobalValues.workingMode == Const.WORKING_MODE_URL_SCHEME) {
      return null
    }

    val dump = try {
      CommandUtils.execAdbCmd("dumpsys shortcut")
    } catch (t: Throwable) {
      Timber.e(t)
      return null
    }

    if (!dump.contains("ShortcutInfo {")) {
      Timber.w("Shortcut dump is empty: %s", dump.take(120))
      return null
    }

    return ShortcutDumpParser.parse(dump)
      .asSequence()
      .filter { it.packageName != BuildConfig.APPLICATION_ID }
      .mapNotNull { toThirdAppShortcut(it) }
      .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.rank }, { it.label }))
      .toList()
  }

  private fun toThirdAppShortcut(shortcut: ShortcutDumpParser.Shortcut): ThirdAppShortcut? {
    val appInfo = try {
      packageManager.getApplicationInfo(shortcut.packageName, 0)
    } catch (t: PackageManager.NameNotFoundException) {
      Timber.d(t, "Skip shortcut of uninstalled package %s", shortcut.packageName)
      return null
    }

    val appLabel = runCatching {
      packageManager.getApplicationLabel(appInfo).toString()
    }.getOrDefault(shortcut.packageName)

    val appIcon = runCatching {
      packageManager.getApplicationIcon(appInfo)
    }.getOrNull()

    val shortcutIcon = shortcut.iconResId
      .takeIf { it != 0 }
      ?.let { resId ->
        runCatching {
          packageManager.getResourcesForApplication(appInfo).getDrawable(resId, null)
        }.getOrNull()
      }

    return ThirdAppShortcut(
      id = shortcut.id,
      packageName = shortcut.packageName,
      appLabel = appLabel,
      label = shortcut.label,
      kind = shortcut.kind,
      rank = shortcut.rank,
      action = shortcut.action,
      data = shortcut.data,
      mimeType = shortcut.mimeType,
      flags = shortcut.flags,
      targetPackage = shortcut.targetPackage,
      component = shortcut.component,
      categories = shortcut.categories,
      extras = shortcut.extras,
      icon = shortcutIcon ?: appIcon
    )
  }

  private fun getResolveInfoList(): List<ResolveInfo> {
    return packageManager.queryIntentActivities(
      Intent(Intent.ACTION_CREATE_SHORTCUT), PackageManager.MATCH_DEFAULT_ONLY
    ).filter { it.activityInfo.applicationInfo.packageName != BuildConfig.APPLICATION_ID }
  }

  private class OpenCreateShortcut : ActivityResultContract<ResolveInfo, Intent?>() {
    @CallSuper
    override fun createIntent(context: Context, input: ResolveInfo): Intent {
      return Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
        flags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
          or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
          or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
          or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
        setClassName(
          input.activityInfo.applicationInfo.packageName,
          input.activityInfo.name
        )
      }
    }

    override fun getSynchronousResult(
      context: Context,
      input: ResolveInfo
    ): SynchronousResult<Intent?>? = null

    override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
      return intent.takeIf { resultCode == Activity.RESULT_OK }
        ?.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent
    }
  }
}
