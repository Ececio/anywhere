package com.absinthe.anywhere_

import com.absinthe.anywhere_.utils.ShortcutDumpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutDumpParserTest {

  private val dump = """
    Now: [1795512913551] 2026-11-24 17:35:13
      Package: com.android.settings  UID: 1000
        Shortcuts:
          ShortcutInfo {id=manifest-shortcut-wifi, flags=0x1a4 [ImManIc-rStr]
            packageName=com.android.settings
            activity=ComponentInfo{com.android.settings/com.android.settings.Settings}
            shortLabel=WLAN, resId=2131826226[wifi_settings]
            longLabel=null, resId=0[null]
            disabledMessage=null, resId=0[null]
            disabledReason=[Not disabled]
            categories=null
            persons=[Landroid.app.Person;@ce1bfa7
            icon=null
            rank=0, timestamp=1790438249470
            intents=[Intent { act=android.settings.WIFI_SETTINGS flg=0x1000c000 }/null]
            extras=null
            iconRes=2131231443[drawable/ic_shortcut_wireless], bitmapPath=null, iconUri=null}
        Total bitmap size: 0 (0 B)

      Package: io.github.huskydg.magisk  UID: 10150
        Shortcuts:
          ShortcutInfo {id=modules, flags=0x85 [DynIc-rStr]
            packageName=io.github.huskydg.magisk
            activity=ComponentInfo{io.github.huskydg.magisk/com.topjohnwu.magisk.ui.MainActivity}
            shortLabel=模块, resId=0[null]
            longLabel=null, resId=0[null]
            disabledMessage=null, resId=0[null]
            disabledReason=[Not disabled]
            categories=null
            persons=null
            icon=null
            rank=2, timestamp=1790438249470
            intents=[Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10000000 pkg=io.github.huskydg.magisk cmp=io.github.huskydg.magisk/com.topjohnwu.magisk.ui.MainActivity }/PersistableBundle[{section=modules}]]
            extras=null
            iconRes=0[null], bitmapPath=null, iconUri=null}
          ShortcutInfo {id=new_incognito_tab_shortcut, flags=0x1a4 [ImManIc-rStr]
            packageName=org.lineageos.jelly
            activity=ComponentInfo{org.lineageos.jelly/org.lineageos.jelly.MainActivity}
            shortLabel=新隐私标签页, resId=2131755192[shortcut_new_incognito_tab]
            longLabel=null, resId=0[null]
            disabledMessage=null, resId=0[null]
            disabledReason=[Not disabled]
            categories=null
            persons=null
            icon=null
            rank=0, timestamp=1790438249470
            intents=[Intent { act=android.intent.action.VIEW flg=0x1000c000 cmp=org.lineageos.jelly/.MainActivity }/PersistableBundle[{extra_incognito=true}]]
            extras=null
            iconRes=0[null], bitmapPath=null, iconUri=null}
          ShortcutInfo {id=disabled_one, flags=0x85 [DynIc-rStr]
            packageName=com.example.disabled
            activity=ComponentInfo{com.example.disabled/com.example.disabled.MainActivity}
            shortLabel=Disabled, resId=0[null]
            longLabel=null, resId=0[null]
            disabledMessage=null, resId=0[null]
            disabledReason=[Disabled by app]
            categories=null
            persons=null
            icon=null
            rank=0, timestamp=1790438249470
            intents=[Intent { act=android.intent.action.MAIN flg=0x10000000 }/null]
            extras=null
            iconRes=0[null], bitmapPath=null, iconUri=null}
          ShortcutInfo {id=pinned_only, flags=0x84 [PinIc-rStr]
            packageName=com.example.pinned
            activity=ComponentInfo{com.example.pinned/com.example.pinned.MainActivity}
            shortLabel=Pinned, resId=0[null]
            longLabel=null, resId=0[null]
            disabledMessage=null, resId=0[null]
            disabledReason=[Not disabled]
            categories=null
            persons=null
            icon=null
            rank=0, timestamp=1790438249470
            intents=[Intent { act=android.intent.action.MAIN flg=0x10000000 }/null]
            extras=null
            iconRes=0[null], bitmapPath=null, iconUri=null}
  """.trimIndent()

  @Test
  fun parsesStaticAndDynamicShortcuts() {
    val shortcuts = ShortcutDumpParser.parse(dump)

    assertEquals(3, shortcuts.size)

    val wifi = shortcuts.first { it.id == "manifest-shortcut-wifi" }
    assertEquals(ShortcutDumpParser.Kind.STATIC, wifi.kind)
    assertEquals("com.android.settings", wifi.packageName)
    assertEquals("WLAN", wifi.label)
    assertEquals(2131231443, wifi.iconResId)
    assertEquals("android.settings.WIFI_SETTINGS", wifi.action)

    val modules = shortcuts.first { it.id == "modules" }
    assertEquals(ShortcutDumpParser.Kind.DYNAMIC, modules.kind)
    assertEquals("io.github.huskydg.magisk", modules.packageName)
    assertEquals("模块", modules.label)
    assertEquals(2, modules.rank)
  }

  @Test
  fun parsesIntentFields() {
    val modules = ShortcutDumpParser.parse(dump).first { it.id == "modules" }

    assertEquals("android.intent.action.MAIN", modules.action)
    assertEquals("io.github.huskydg.magisk", modules.targetPackage)
    assertEquals(
      "io.github.huskydg.magisk/com.topjohnwu.magisk.ui.MainActivity",
      modules.component
    )
    assertEquals(listOf("android.intent.category.LAUNCHER"), modules.categories)
    assertEquals(0x10000000, modules.flags)
    assertEquals(mapOf("section" to "modules"), modules.extras)
    assertNull(modules.data)
  }

  @Test
  fun parsesBooleanExtras() {
    val jelly = ShortcutDumpParser.parse(dump).first { it.id == "new_incognito_tab_shortcut" }

    assertEquals(mapOf("extra_incognito" to "true"), jelly.extras)
    assertEquals("org.lineageos.jelly/.MainActivity", jelly.component)
  }

  @Test
  fun tracksUserOfEachShortcut() {
    val multiUser = """
      User: 0  Known locales: [en-US]
        Package: com.example.zero  UID: 10000
          Shortcuts:
            ShortcutInfo {id=zero, flags=0x85 [DynIc-rStr]
              packageName=com.example.zero
              shortLabel=Zero, resId=0[null]
              disabledReason=[Not disabled]
              intents=[Intent { act=android.intent.action.VIEW dat=foo://zero flg=0x10000000 }/null]
              iconRes=0[null], bitmapPath=null, iconUri=null}
      User: 10  Known locales: [en-US]
        Package: com.example.ten  UID: 1010000
          Shortcuts:
            ShortcutInfo {id=ten, flags=0x85 [DynIc-rStr]
              packageName=com.example.ten
              shortLabel=Ten, resId=0[null]
              disabledReason=[Not disabled]
              intents=[Intent { act=android.intent.action.VIEW dat=foo://ten flg=0x10000000 }/null]
              iconRes=0[null], bitmapPath=null, iconUri=null}
    """.trimIndent()

    val shortcuts = ShortcutDumpParser.parse(multiUser)

    assertEquals(0, shortcuts.first { it.id == "zero" }.userId)
    assertEquals(10, shortcuts.first { it.id == "ten" }.userId)
  }

  @Test
  fun skipsDisabledAndPinnedOnlyShortcuts() {
    val ids = ShortcutDumpParser.parse(dump).map { it.id }

    assertTrue("disabled_one" !in ids)
    assertTrue("pinned_only" !in ids)
  }

  @Test
  fun skipsShortcutsWithoutIntent() {
    val broken = """
      ShortcutInfo {id=no_intent, flags=0x1a4 [ImManIc-rStr]
        packageName=com.example.broken
        shortLabel=Broken, resId=0[null]
        disabledReason=[Not disabled]
        intents=[null]
        iconRes=0[null], bitmapPath=null, iconUri=null}
    """.trimIndent()

    assertTrue(ShortcutDumpParser.parse(broken).isEmpty())
  }

  @Test
  fun skipsShortcutsWithoutPackageName() {
    val broken = """
      ShortcutInfo {id=no_package, flags=0x1a4 [ImManIc-rStr]
        shortLabel=NoPackage, resId=0[null]
        disabledReason=[Not disabled]
        intents=[Intent { act=android.intent.action.MAIN flg=0x10000000 }/null]
        iconRes=0[null], bitmapPath=null, iconUri=null}
    """.trimIndent()

    assertTrue(ShortcutDumpParser.parse(broken).isEmpty())
  }

  @Test
  fun fallsBackToIdWhenLabelIsMissing() {
    val noLabel = """
      ShortcutInfo {id=label_less, flags=0x85 [DynIc-rStr]
        packageName=com.example.label
        shortLabel=null, resId=0[null]
        longLabel=null, resId=0[null]
        disabledReason=[Not disabled]
        intents=[Intent { act=android.intent.action.MAIN flg=0x10000000 }/null]
        iconRes=0[null], bitmapPath=null, iconUri=null}
    """.trimIndent()

    assertEquals("label_less", ShortcutDumpParser.parse(noLabel).single().label)
  }
}
