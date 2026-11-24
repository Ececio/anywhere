package com.absinthe.anywhere_.adapter.shortcut

import android.content.pm.ResolveInfo
import com.absinthe.anywhere_.model.ThirdAppShortcut
import com.chad.library.adapter.base.entity.MultiItemEntity

const val TYPE_HEADER = 1
const val TYPE_APP = 2
const val TYPE_SHORTCUT = 3

sealed class ThirdAppsShortcutItem : MultiItemEntity {

  data class Header(val title: String) : ThirdAppsShortcutItem() {
    override val itemType: Int = TYPE_HEADER
  }

  data class App(val info: ResolveInfo) : ThirdAppsShortcutItem() {
    override val itemType: Int = TYPE_APP
  }

  data class Shortcut(val shortcut: ThirdAppShortcut) : ThirdAppsShortcutItem() {
    override val itemType: Int = TYPE_SHORTCUT
  }
}
