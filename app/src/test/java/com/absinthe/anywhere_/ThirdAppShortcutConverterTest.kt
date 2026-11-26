package com.absinthe.anywhere_

import com.absinthe.anywhere_.constants.AnywhereType
import com.absinthe.anywhere_.model.ExtraBean
import com.absinthe.anywhere_.model.TYPE_BOOLEAN
import com.absinthe.anywhere_.model.TYPE_INT
import com.absinthe.anywhere_.model.TYPE_STRING
import com.absinthe.anywhere_.model.ThirdAppShortcut
import com.absinthe.anywhere_.model.database.AnywhereEntity
import com.absinthe.anywhere_.utils.ShortcutDumpParser
import com.absinthe.anywhere_.utils.ThirdAppShortcutConverter
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdAppShortcutConverterTest {

  private fun shortcut(
    id: String = "shortcut",
    packageName: String = "com.example.app",
    appLabel: String = "Example",
    label: String = "Shortcut",
    action: String? = null,
    data: String? = null,
    targetPackage: String? = null,
    component: String? = null,
    extras: Map<String, String> = emptyMap()
  ) = ThirdAppShortcut(
    id = id,
    packageName = packageName,
    appLabel = appLabel,
    label = label,
    kind = ShortcutDumpParser.Kind.STATIC,
    rank = 0,
    action = action,
    data = data,
    mimeType = null,
    flags = 0,
    targetPackage = targetPackage,
    component = component,
    categories = emptyList(),
    extras = extras,
    icon = null
  )

  private fun bean(entity: AnywhereEntity) =
    Gson().fromJson(entity.param3, ExtraBean::class.java)

  @Test
  fun convertsExplicitComponentAndInfersExtraTypes() {
    val entity = ThirdAppShortcutConverter.toEntity(
      shortcut(
        packageName = "io.github.huskydg.magisk",
        appLabel = "Kitsune Mask",
        label = "模块",
        action = "android.intent.action.MAIN",
        component = "io.github.huskydg.magisk/com.topjohnwu.magisk.ui.MainActivity",
        extras = mapOf("section" to "modules", "enabled" to "true", "count" to "3")
      )
    )

    assertEquals(AnywhereType.Card.ACTIVITY, entity.type)
    assertEquals("模块", entity.appName)
    assertEquals("", entity.description)
    assertEquals("io.github.huskydg.magisk", entity.param1)
    assertEquals("com.topjohnwu.magisk.ui.MainActivity", entity.param2)
    assertEquals("", entity.category)

    val bean = bean(entity)
    assertEquals("android.intent.action.MAIN", bean.action)
    assertEquals("", bean.data)
    assertEquals(3, bean.extras.size)
    assertEquals(TYPE_STRING, bean.extras.first { it.key == "section" }.type)
    assertEquals(TYPE_BOOLEAN, bean.extras.first { it.key == "enabled" }.type)
    assertEquals(TYPE_INT, bean.extras.first { it.key == "count" }.type)
  }

  @Test
  fun keepsRelativeClassNameForOpener() {
    val entity = ThirdAppShortcutConverter.toEntity(
      shortcut(
        packageName = "com.android.settings",
        label = "WLAN",
        action = "android.intent.action.MAIN",
        component = "com.android.settings/.Settings\$WifiSettingsActivity"
      )
    )

    assertEquals("com.android.settings", entity.param1)
    assertEquals(".Settings\$WifiSettingsActivity", entity.param2)
  }

  @Test
  fun usesResolvedComponentForImplicitIntent() {
    val entity = ThirdAppShortcutConverter.toEntity(
      shortcut(
        packageName = "com.example.app",
        label = "Scan",
        action = "com.example.app.action.SCAN"
      ),
      resolvedPackage = "com.example.app",
      resolvedClass = "com.example.app.ScanActivity"
    )

    assertEquals("com.example.app", entity.param1)
    assertEquals("com.example.app.ScanActivity", entity.param2)
    assertEquals("com.example.app.action.SCAN", bean(entity).action)
  }

  @Test
  fun keepsActionOnlyIntentImplicit() {
    val entity = ThirdAppShortcutConverter.toEntity(
      shortcut(
        packageName = "com.example.app",
        label = "Battery",
        action = "android.intent.action.POWER_USAGE_SUMMARY"
      )
    )

    assertEquals("", entity.param1)
    assertEquals("", entity.param2)
    assertEquals("android.intent.action.POWER_USAGE_SUMMARY", bean(entity).action)
  }

  @Test
  fun fallsBackToTargetPackageWhenImplicitCannotBeResolved() {
    val entity = ThirdAppShortcutConverter.toEntity(
      shortcut(
        packageName = "com.example.app",
        label = "Open",
        targetPackage = "com.example.target"
      )
    )

    assertEquals("com.example.target", entity.param1)
    assertEquals("", entity.param2)
  }

  @Test
  fun keepsDataForResolvedIntent() {
    val entity = ThirdAppShortcutConverter.toEntity(
      shortcut(
        label = "Dial",
        data = "tel:10086"
      ),
      resolvedPackage = "com.android.contacts",
      resolvedClass = "com.android.contacts.DialActivity"
    )

    assertEquals("com.android.contacts", entity.param1)
    assertEquals("com.android.contacts.DialActivity", entity.param2)
    assertEquals("tel:10086", bean(entity).data)
  }

  @Test
  fun fallsBackToAppLabelWhenShortcutLabelIsBlank() {
    val entity = ThirdAppShortcutConverter.toEntity(
      shortcut(appLabel = "Example", label = "", component = "com.example.app/.Main")
    )

    assertEquals("Example", entity.appName)
  }

  @Test
  fun keepsTypedExtrasFromCreateShortcutResult() {
    val entity = ThirdAppShortcutConverter.toEntity(
      name = "Shortcut",
      appLabel = "Example",
      packageName = "com.example.app",
      className = "com.example.app.Main",
      action = "android.intent.action.VIEW",
      data = "",
      extras = listOf(
        ExtraBean.ExtraItem(TYPE_BOOLEAN, "flag", "true"),
        ExtraBean.ExtraItem(TYPE_INT, "count", "7"),
        ExtraBean.ExtraItem(TYPE_STRING, "name", "value")
      )
    )

    val bean = bean(entity)
    assertEquals(3, bean.extras.size)
    assertEquals(TYPE_BOOLEAN, bean.extras.first { it.key == "flag" }.type)
    assertEquals(TYPE_INT, bean.extras.first { it.key == "count" }.type)
    assertEquals(TYPE_STRING, bean.extras.first { it.key == "name" }.type)
    assertTrue(bean.toString().contains("-a 'android.intent.action.VIEW'"))
  }
}
