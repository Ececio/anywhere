package com.absinthe.anywhere_.adapter.shortcut

import android.content.pm.ResolveInfo
import com.absinthe.anywhere_.model.ThirdAppShortcut

sealed class ThirdAppsShortcutItem {

  data class App(val info: ResolveInfo) : ThirdAppsShortcutItem()

  data class Shortcut(val shortcut: ThirdAppShortcut) : ThirdAppsShortcutItem()
}
