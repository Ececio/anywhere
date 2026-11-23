package com.absinthe.anywhere_.constants

import androidx.core.text.HtmlCompat
import androidx.lifecycle.MutableLiveData
import com.absinthe.anywhere_.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV

object GlobalValues {

  var spName = if (BuildConfig.DEBUG) {
    Const.SP_NAME_DEBUG
  } else {
    Const.SP_NAME
  }

  val mmkv: MMKV =
    MMKV.mmkvWithID(spName) ?: throw IllegalStateException("mmkv instance is null")

  var sIsDebugMode = false
  var shouldListenClipBoard = true

  var cardModeLiveData = MutableLiveData<Any>()
  var groupModeLiveData = MutableLiveData<Any>()
  var animationSpeedLiveData = MutableLiveData<Any>()
  var compactLayoutLiveData = MutableLiveData<Any>()
  var groupOrderLiveData = MutableLiveData<Any>()

  var shortcutsList = listOf<String>()
    get() = try {
      Gson().fromJson<List<String>>(
        mmkv.decodeString(Const.SHORTCUTS_LIST),
        object : TypeToken<List<String>>() {}.type
      )
        ?: listOf()
    } catch (e: JsonSyntaxException) {
      listOf()
    }
    set(value) {
      field = value
      mmkv.encode(Const.SHORTCUTS_LIST, Gson().toJson(value))
      shortcutListChanged = true
    }
  var shortcutListChanged = false

  var cardMode
    get() = mmkv.decodeString(Const.PREF_CARD_MODE) ?: Const.PREF_CARD_MODE_SMALL
    set(value) {
      mmkv.encode(Const.PREF_CARD_MODE, value)
    }

  var closeAfterLaunch
    get() = mmkv.decodeBool(Const.PREF_CLOSE_AFTER_LAUNCH, false)
    set(value) {
      mmkv.encode(Const.PREF_CLOSE_AFTER_LAUNCH, value)
    }

  /** Number of compact cards per row. */
  var compactColumns
    get() = mmkv.decodeInt(Const.PREF_COMPACT_COLUMNS, 6).coerceIn(2, 6)
    set(value) {
      mmkv.encode(Const.PREF_COMPACT_COLUMNS, value)
      compactLayoutLiveData.value = Any()
    }

  /** Spacing preset of compact cards: tight / normal / loose. */
  var compactSpacing
    get() = mmkv.decodeString(Const.PREF_COMPACT_SPACING, Const.COMPACT_SPACING_TIGHT)
      ?: Const.COMPACT_SPACING_TIGHT
    set(value) {
      mmkv.encode(Const.PREF_COMPACT_SPACING, value)
      compactLayoutLiveData.value = Any()
    }

  /** Size preset of compact cards: small / medium / large. */
  var compactSize
    get() = mmkv.decodeString(Const.PREF_COMPACT_SIZE, Const.COMPACT_SIZE_MEDIUM)
      ?: Const.COMPACT_SIZE_MEDIUM
    set(value) {
      mmkv.encode(Const.PREF_COMPACT_SIZE, value)
      compactLayoutLiveData.value = Any()
    }

  /** Icon size of compact cards, in dp. */
  val compactIconSizeDp: Int
    get() = when (compactSize) {
      Const.COMPACT_SIZE_SMALL -> 30
      Const.COMPACT_SIZE_LARGE -> 44
      else -> 36
    }

  /** Name text size of compact cards, in sp. */
  val compactTextSizeSp: Float
    get() = when (compactSize) {
      Const.COMPACT_SIZE_SMALL -> 10f
      Const.COMPACT_SIZE_LARGE -> 14f
      else -> 12f
    }

  /** Padding of compact cards: left, top, right, bottom in dp. */
  val compactPadding: IntArray
    get() = when (compactSpacing) {
      Const.COMPACT_SPACING_NORMAL -> intArrayOf(3, 4, 3, 3)
      Const.COMPACT_SPACING_LOOSE -> intArrayOf(5, 6, 5, 5)
      else -> intArrayOf(1, 2, 1, 1)
    }

  /** Gap between the icon and the name of compact cards, in dp. */
  val compactIconTextGapDp: Int
    get() = when (compactSpacing) {
      Const.COMPACT_SPACING_NORMAL -> 2
      Const.COMPACT_SPACING_LOOSE -> 3
      else -> 1
    }

  /** Gap between compact cards, in dp. */
  val compactItemGapDp: Int
    get() = when (compactSpacing) {
      Const.COMPACT_SPACING_NORMAL -> 6
      Const.COMPACT_SPACING_LOOSE -> 8
      else -> 4
    }

  var animationSpeed
    get() = mmkv.decodeString(Const.PREF_ANIMATION_SPEED, Const.ANIM_SPEED_NORMAL)
      ?: Const.ANIM_SPEED_NORMAL
    set(value) {
      mmkv.encode(Const.PREF_ANIMATION_SPEED, value)
      animationSpeedLiveData.value = Any()
    }

  /** Whether the logo animation plays while the launch splash screen is shown. */
  var splashAnimation
    get() = mmkv.decodeBool(Const.PREF_SPLASH_ANIMATION, false)
    set(value) {
      mmkv.encode(Const.PREF_SPLASH_ANIMATION, value)
    }

  /** How the home screen is presented when launched from the launcher icon. */
  var homeMode
    get() = mmkv.decodeString(Const.PREF_HOME_MODE, Const.HOME_MODE_POPUP)
      ?: Const.HOME_MODE_POPUP
    set(value) {
      mmkv.encode(Const.PREF_HOME_MODE, value)
    }

  /** Opacity of the popup panel background, in percent (0 = transparent, 100 = opaque). */
  var popupTransparency
    get() = mmkv.decodeInt(Const.PREF_POPUP_TRANSPARENCY, 90).coerceIn(0, 100)
    set(value) {
      mmkv.encode(Const.PREF_POPUP_TRANSPARENCY, value.coerceIn(0, 100))
    }

  /** Whether the floating ball overlay is enabled. */
  var homeFloat
    get() = mmkv.decodeBool(Const.PREF_HOME_FLOAT, false)
    set(value) {
      mmkv.encode(Const.PREF_HOME_FLOAT, value)
    }

  /** Whether the floating ball runs as a foreground service with a persistent notification. */
  var homeFloatForeground
    get() = mmkv.decodeBool(Const.PREF_HOME_FLOAT_FOREGROUND, true)
    set(value) {
      mmkv.encode(Const.PREF_HOME_FLOAT_FOREGROUND, value)
    }

  /** Whether the floating ball is restored automatically after a reboot. */
  var homeFloatBoot
    get() = mmkv.decodeBool(Const.PREF_HOME_FLOAT_BOOT, true)
    set(value) {
      mmkv.encode(Const.PREF_HOME_FLOAT_BOOT, value)
    }

  /** Duration of RecyclerView item animations such as group expanding/collapsing. */
  val itemAnimDuration: Long
    get() = when (animationSpeed) {
      Const.ANIM_SPEED_OFF -> 0L
      Const.ANIM_SPEED_SLOW -> 320L
      Const.ANIM_SPEED_FAST -> 90L
      else -> 180L
    }

  /** Scrolling speed of card names that are too long, in dp per second. */
  val marqueeSpeedDpPerSecond: Float
    get() = when (animationSpeed) {
      Const.ANIM_SPEED_OFF -> 0f
      Const.ANIM_SPEED_SLOW -> 6f
      Const.ANIM_SPEED_FAST -> 10f
      else -> 8f
    }

  var isPages
    get() = mmkv.decodeBool(Const.PREF_PAGES)
    set(value) {
      mmkv.encode(Const.PREF_PAGES, value)
    }

  var isCollectorPlus
    get() = mmkv.decodeBool(Const.PREF_COLLECTOR_PLUS)
    set(value) {
      mmkv.encode(Const.PREF_COLLECTOR_PLUS, value)
    }

  var isExcludeFromRecent
    get() = mmkv.decodeBool(Const.PREF_EXCLUDE_FROM_RECENT)
    set(value) {
      mmkv.encode(Const.PREF_EXCLUDE_FROM_RECENT, value)
    }

  var showShellResultMode
    get() = mmkv.decodeString(Const.PREF_SHOW_SHELL_RESULT_MODE) ?: Const.SHELL_RESULT_TOAST
    set(value) {
      mmkv.encode(Const.PREF_SHOW_SHELL_RESULT_MODE, value)
    }

  var isAutoBackup
    get() = mmkv.decodeBool(Const.PREF_WEBDAV_AUTO_BACKUP, true)
    set(value) {
      mmkv.encode(Const.PREF_WEBDAV_AUTO_BACKUP, value)
    }

  var workingMode
    get() = mmkv.decodeString(Const.PREF_WORKING_MODE, Const.WORKING_MODE_ROOT)
      ?: Const.WORKING_MODE_ROOT
    set(value) {
      mmkv.encode(Const.PREF_WORKING_MODE, value)
    }

  var actionBarType
    get() = mmkv.decodeString(Const.PREF_ACTION_BAR_TYPE, "").orEmpty()
    set(value) {
      mmkv.encode(Const.PREF_ACTION_BAR_TYPE, value)
    }

  var darkMode
    get() = mmkv.decodeString(Const.PREF_DARK_MODE, "").orEmpty()
    set(value) {
      mmkv.encode(Const.PREF_DARK_MODE, value)
    }

  var backgroundUri
    get() = mmkv.decodeString(Const.PREF_CHANGE_BACKGROUND, "").orEmpty()
    set(value) {
      mmkv.encode(Const.PREF_CHANGE_BACKGROUND, value)
    }

  var sCardBackgroundMode
    get() = mmkv.decodeString(Const.PREF_CARD_BACKGROUND, "off") ?: "off"
    set(value) {
      mmkv.encode(Const.PREF_CARD_BACKGROUND, value)
    }

  var sortMode
    get() = mmkv.decodeString(Const.PREF_SORT_MODE, "").orEmpty()
    set(value) {
      mmkv.encode(Const.PREF_SORT_MODE, value)
    }

  var groupMode
    get() = mmkv.decodeString(Const.PREF_GROUP_MODE, Const.GROUP_MODE_PACKAGE)
      ?: Const.GROUP_MODE_PACKAGE
    set(value) {
      mmkv.encode(Const.PREF_GROUP_MODE, value)
      groupModeLiveData.value = Any()
    }

  /** Saved custom order of group keys for the current grouping mode. */
  val groupOrder: List<String>
    get() {
      val key = when (groupMode) {
        Const.GROUP_MODE_PACKAGE -> Const.PREF_GROUP_ORDER_PACKAGE
        Const.GROUP_MODE_TAG -> Const.PREF_GROUP_ORDER_TAG
        else -> return emptyList()
      }
      return try {
        Gson().fromJson<List<String>>(
          mmkv.decodeString(key),
          object : TypeToken<List<String>>() {}.type
        ) ?: emptyList()
      } catch (e: JsonSyntaxException) {
        emptyList()
      }
    }

  fun setGroupOrder(keys: List<String>) {
    val key = when (groupMode) {
      Const.GROUP_MODE_PACKAGE -> Const.PREF_GROUP_ORDER_PACKAGE
      Const.GROUP_MODE_TAG -> Const.PREF_GROUP_ORDER_TAG
      else -> return
    }
    mmkv.encode(key, Gson().toJson(keys))
    groupOrderLiveData.value = Any()
  }

  var iconPack
    get() = mmkv.decodeString(Const.PREF_ICON_PACK, "").orEmpty()
    set(value) {
      mmkv.encode(Const.PREF_ICON_PACK, value)
    }

  var category
    get() = mmkv.decodeString(Const.PREF_CURR_CATEGORY, AnywhereType.Category.DEFAULT_CATEGORY)
      ?: AnywhereType.Category.DEFAULT_CATEGORY
    set(value) {
      mmkv.encode(Const.PREF_CURR_CATEGORY, value)
    }

  var defrostMode
    get() = mmkv.decodeString(Const.PREF_DEFROST_MODE, "").orEmpty()
    set(value) {
      mmkv.encode(Const.PREF_DEFROST_MODE, value)
    }

  var webdavHost
    get() = mmkv.decodeString(Const.PREF_WEBDAV_HOST, "").orEmpty()
    set(value) {
      mmkv.encode(Const.PREF_WEBDAV_HOST, value)
    }

  var webdavUsername
    get() = mmkv.decodeString(Const.PREF_WEBDAV_USERNAME, "").orEmpty()
    set(value) {
      mmkv.encode(Const.PREF_WEBDAV_USERNAME, value)
    }

  var webdavPassword
    get() = mmkv.decodeString(Const.PREF_WEBDAV_PASSWORD, "").orEmpty()
    set(value) {
      mmkv.encode(Const.PREF_WEBDAV_PASSWORD, value)
    }

  var currentPage
    get() = mmkv.decodeInt(Const.PREF_CURR_PAGE_NUM, 0)
    set(value) {
      mmkv.encode(Const.PREF_CURR_PAGE_NUM, value)
    }

  var dumpInterval
    get() = mmkv.decodeInt(Const.PREF_DUMP_INTERVAL, 1000)
    set(value) {
      mmkv.encode(Const.PREF_DUMP_INTERVAL, value)
    }

  var autoDarkModeStart
    get() = mmkv.decodeLong(Const.PREF_AUTO_DARK_MODE_START, 0)
    set(value) {
      mmkv.encode(Const.PREF_AUTO_DARK_MODE_START, value)
    }

  var autoDarkModeEnd
    get() = mmkv.decodeLong(Const.PREF_AUTO_DARK_MODE_END, 0)
    set(value) {
      mmkv.encode(Const.PREF_AUTO_DARK_MODE_END, value)
    }

  var needBackup
    get() = mmkv.decodeBool(Const.PREF_NEED_BACKUP, true)
    set(value) {
      mmkv.encode(Const.PREF_NEED_BACKUP, value)
    }

  var editorEntryAnim
    get() = mmkv.decodeBool(Const.PREF_EDITOR_ENTRY_ANIM, false)
    set(value) {
      mmkv.encode(Const.PREF_EDITOR_ENTRY_ANIM, value)
    }

  var deprecatedScCreatingMethod
    get() = mmkv.decodeBool(Const.PREF_DEPRECATED_SC_CREATING_METHOD, false)
    set(value) {
      mmkv.encode(Const.PREF_DEPRECATED_SC_CREATING_METHOD, value)
    }

  var showSystemApps
    get() = mmkv.decodeBool(Const.PREF_SHOW_SYSTEM_APPS, false)
    set(value) {
      mmkv.encode(Const.PREF_SHOW_SYSTEM_APPS, value)
    }

  var showDefreezingToast
    get() = mmkv.decodeBool(Const.PREF_SHOW_DEFREEZING_TOAST, true)
    set(value) {
      mmkv.encode(Const.PREF_SHOW_DEFREEZING_TOAST, value)
    }

  var shouldListenClipBoardPref
    get() = mmkv.decodeBool(Const.PREF_LISTEN_CLIP_BOARD, true)
    set(value) {
      mmkv.encode(Const.PREF_LISTEN_CLIP_BOARD, value)
    }

  val info: CharSequence
    get() {
      val sb = StringBuilder()
        .append(getInfoLine("Working Mode", workingMode))
        .append(getInfoLine("Background Uri", backgroundUri))
        .append(getInfoLine("ActionBar Type", actionBarType))
        .append(getInfoLine("Sort Mode", sortMode))
        .append(getInfoLine("Group Mode", groupMode))
        .append(getInfoLine("Icon Pack", iconPack))
        .append(getInfoLine("Dark Mode", darkMode))
        .append(getInfoLine("Card Background Mode", sCardBackgroundMode))
        .append(getInfoLine("Dump Interval", dumpInterval.toString()))
        .append(getInfoLine("Current Page", currentPage.toString()))
        .append(getInfoLine("Defrost Mode", defrostMode))
        .append(getInfoLine("Current Category", category))
      return HtmlCompat.fromHtml(sb.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

  val collectorMode: String
    get() = if (isCollectorPlus) {
      "Collector+"
    } else {
      "Collector"
    }

  private fun getInfoLine(infoName: String, infoValue: String?): CharSequence {
    return StringBuilder()
      .append("<b>").append(infoName).append("</b>")
      .append(": ").append(infoValue).append("<br>")
  }

  fun clearActionBarType() {
    actionBarType = ""
  }

  fun setsCategory(sCategory: String, page: Int) {
    category = sCategory
    currentPage = page
  }
}
