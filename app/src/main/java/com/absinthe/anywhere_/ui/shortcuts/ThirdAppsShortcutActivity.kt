package com.absinthe.anywhere_.ui.shortcuts

import android.animation.LayoutTransition
import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.CallSuper
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.drawable.toDrawable
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
import com.absinthe.anywhere_.model.ExtraBean
import com.absinthe.anywhere_.model.TYPE_BOOLEAN
import com.absinthe.anywhere_.model.TYPE_DOUBLE
import com.absinthe.anywhere_.model.TYPE_FLOAT
import com.absinthe.anywhere_.model.TYPE_INT
import com.absinthe.anywhere_.model.TYPE_LONG
import com.absinthe.anywhere_.model.TYPE_STRING
import com.absinthe.anywhere_.model.ThirdAppShortcut
import com.absinthe.anywhere_.model.database.AnywhereEntity
import com.absinthe.anywhere_.ui.editor.EXTRA_EDIT_MODE
import com.absinthe.anywhere_.ui.editor.EXTRA_ENTITY
import com.absinthe.anywhere_.ui.editor.EditorActivity
import com.absinthe.anywhere_.utils.CardIconStore
import com.absinthe.anywhere_.utils.CommandUtils
import com.absinthe.anywhere_.utils.ShortcutDumpParser
import com.absinthe.anywhere_.utils.ThirdAppShortcutConverter
import com.absinthe.anywhere_.utils.ToastUtil
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.widget.borderview.BorderView
import timber.log.Timber

class ThirdAppsShortcutActivity : AppBarActivity<ActivityThirdAppsShortcutBinding>(),
  SearchView.OnQueryTextListener {

  override fun setViewBinding() = ActivityThirdAppsShortcutBinding.inflate(layoutInflater)

  override fun getToolBar() = binding.toolbar.toolBar

  override fun getAppBarLayout() = binding.toolbar.appBar

  private val mAdapter = ThirdAppsShortcutAdapter()

  private var apps: List<ResolveInfo> = emptyList()
  private var staticShortcuts: List<ThirdAppShortcut> = emptyList()
  private var dynamicShortcuts: List<ThirdAppShortcut> = emptyList()

  /** Current search text, applied to whichever group is visible. */
  private var query: String = ""

  private var currentGroupId: Int = 0
  private var isListReady = false

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

    val resultLauncher = registerForActivityResult(OpenCreateShortcut()) { draft ->
      draft?.let { openEditor(it) } ?: run {
        Timber.d("resultLauncher: intent is null")
      }
    }

    mAdapter.setOnItemClickListener { _, _, position ->
      when (val item = mAdapter.data.getOrNull(position)) {
        is ThirdAppsShortcutItem.App -> resultLauncher.launch(item.info)
        is ThirdAppsShortcutItem.Shortcut -> openEditor(item.shortcut)
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

    isListReady = true
    invalidateOptionsMenu()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.third_apps_shortcut_menu, menu)

    val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
    val searchView = menu.findItem(R.id.search).actionView as SearchView
    searchView.findViewById<LinearLayout>(androidx.appcompat.R.id.search_bar)?.layoutTransition =
      LayoutTransition()

    searchView.apply {
      isQueryRefinementEnabled = true
      setIconifiedByDefault(false)
      setSearchableInfo(searchManager.getSearchableInfo(componentName))
      setQueryHint(getString(R.string.shortcuts_search_hint))
      setOnQueryTextListener(this@ThirdAppsShortcutActivity)
    }

    menu.findItem(R.id.search).isVisible = isListReady

    return true
  }

  override fun onQueryTextSubmit(query: String): Boolean {
    return false
  }

  override fun onQueryTextChange(newText: String): Boolean {
    query = newText.trim()
    if (currentGroupId != 0) {
      showGroup(currentGroupId)
    }
    return false
  }

  private fun updateTab(button: MaterialButton, titleRes: Int, count: Int) {
    button.text = getString(titleRes, count)
    button.isVisible = count > 0
  }

  private fun showGroup(checkedId: Int) {
    currentGroupId = checkedId
    val items: List<ThirdAppsShortcutItem> = when (checkedId) {
      R.id.btn_static -> staticShortcuts.map { ThirdAppsShortcutItem.Shortcut(it) }
      R.id.btn_dynamic -> dynamicShortcuts.map { ThirdAppsShortcutItem.Shortcut(it) }
      R.id.btn_apps -> apps.map { ThirdAppsShortcutItem.App(it) }
      else -> emptyList()
    }

    val filtered = if (query.isBlank()) items else items.filter { it.matches(query) }

    mAdapter.setNewData(filtered.toMutableList())
    binding.list.scrollToPosition(0)
  }

  /**
   * Matches a shortcut or an app by its displayed name, the app name and the package name, so a
   * shortcut can be found by either the shortcut label or the app it belongs to.
   */
  private fun ThirdAppsShortcutItem.matches(query: String): Boolean {
    return when (this) {
      is ThirdAppsShortcutItem.App -> {
        val activityInfo = info.activityInfo
        val label = activityInfo.loadLabel(packageManager)?.toString().orEmpty()
        val appLabel = activityInfo.applicationInfo?.loadLabel(packageManager)?.toString().orEmpty()
        activityInfo.packageName.contains(query, ignoreCase = true) ||
          label.contains(query, ignoreCase = true) ||
          appLabel.contains(query, ignoreCase = true)
      }

      is ThirdAppsShortcutItem.Shortcut -> {
        shortcut.label.contains(query, ignoreCase = true) ||
          shortcut.appLabel.contains(query, ignoreCase = true) ||
          shortcut.packageName.contains(query, ignoreCase = true)
      }
    }
  }

  /**
   * Opens the regular activity editor pre-filled with the shortcut, so the user can review the
   * fields and save the card to the current page.
   */
  private fun openEditor(shortcut: ThirdAppShortcut) {
    val component = resolveComponent(shortcut.toIntent())
    val entity = ThirdAppShortcutConverter.toEntity(
      shortcut = shortcut,
      resolvedPackage = component?.packageName,
      resolvedClass = component?.className
    )
    // Keep the shortcut's own icon; the card would otherwise fall back to the app icon.
    entity.iconUri = saveShortcutIcon(
      key = "${shortcut.packageName}_${shortcut.id}",
      drawable = shortcut.icon
    ).orEmpty()

    openEditor(entity)
  }

  private fun openEditor(draft: ShortcutDraft) {
    val component = resolveComponent(draft.intent)
    val packageName = component?.packageName ?: draft.intent.`package`.orEmpty()
    val appLabel = if (packageName.isBlank()) {
      ""
    } else {
      runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
          .toString()
      }.getOrDefault(packageName)
    }

    val entity = ThirdAppShortcutConverter.toEntity(
      name = draft.name,
      appLabel = appLabel,
      packageName = packageName,
      className = component?.className,
      action = draft.intent.action.orEmpty(),
      data = draft.intent.dataString.orEmpty(),
      extras = draft.intent.extras.toExtraItems()
    )
    entity.iconUri = saveShortcutIcon(
      key = "draft_${draft.name}_${draft.intent.toUri(0)}",
      drawable = draft.icon
    ).orEmpty()

    openEditor(entity)
  }

  /**
   * Saves a shortcut icon next to the database and returns a `file://` URI for
   * [AnywhereEntity.iconUri], which the card and the pinned shortcut list already understand.
   */
  private fun saveShortcutIcon(key: String, drawable: Drawable?): String? =
    CardIconStore.saveDrawable(this, key, drawable)

  private fun openEditor(entity: AnywhereEntity) {
    entity.category = GlobalValues.category
    startActivity(Intent(this, EditorActivity::class.java).apply {
      putExtra(EXTRA_ENTITY, entity)
      putExtra(EXTRA_EDIT_MODE, false)
    })
  }

  private fun resolveComponent(intent: Intent): ComponentName? {
    intent.component?.let { return it }
    // Do not use MATCH_DEFAULT_ONLY: shortcut intents often carry MAIN/LAUNCHER categories
    // instead of CATEGORY_DEFAULT.
    return packageManager.resolveActivity(intent, 0)
      ?.activityInfo
      ?.let { ComponentName(it.packageName, it.name) }
  }

  private fun Bundle?.toExtraItems(): List<ExtraBean.ExtraItem> {
    if (this == null) {
      return emptyList()
    }

    return keySet().mapNotNull { key ->
      when (val value = get(key)) {
        is Boolean -> ExtraBean.ExtraItem(TYPE_BOOLEAN, key, value.toString())
        is Int -> ExtraBean.ExtraItem(TYPE_INT, key, value.toString())
        is Long -> ExtraBean.ExtraItem(TYPE_LONG, key, value.toString())
        is Float -> ExtraBean.ExtraItem(TYPE_FLOAT, key, value.toString())
        is Double -> ExtraBean.ExtraItem(TYPE_DOUBLE, key, value.toString())
        is String -> ExtraBean.ExtraItem(TYPE_STRING, key, value)
        is CharSequence -> ExtraBean.ExtraItem(TYPE_STRING, key, value.toString())
        else -> null
      }
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

  private inner class OpenCreateShortcut : ActivityResultContract<ResolveInfo, ShortcutDraft?>() {
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
    ): SynchronousResult<ShortcutDraft?>? = null

    override fun parseResult(resultCode: Int, intent: Intent?): ShortcutDraft? {
      if (resultCode != Activity.RESULT_OK || intent == null) {
        return null
      }

      val shortcutIntent =
        intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent ?: return null
      val name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME).orEmpty()

      return ShortcutDraft(name, shortcutIntent, parseIcon(intent))
    }

    private fun parseIcon(intent: Intent): Drawable? {
      val iconResource = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
        as? Intent.ShortcutIconResource
      if (iconResource != null) {
        return runCatching {
          val resources = packageManager.getResourcesForApplication(iconResource.packageName)
          val id = resources.getIdentifier(
            iconResource.resourceName, null, iconResource.packageName
          )
          if (id != 0) resources.getDrawable(id, null) else null
        }.getOrNull()
      }

      return (intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON) as? Bitmap)
        ?.toDrawable(resources)
    }
  }

  private data class ShortcutDraft(
    val name: String,
    val intent: Intent,
    val icon: Drawable?
  )
}
