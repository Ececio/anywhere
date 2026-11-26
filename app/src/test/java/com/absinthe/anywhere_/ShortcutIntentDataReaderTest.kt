package com.absinthe.anywhere_

import com.absinthe.anywhere_.utils.ShortcutIntentDataReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutIntentDataReaderTest {

  private val output = """
    @@@/data/system_ce/0/shortcut_service/packages/com.ss.android.ugc.aweme.xml
    <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
    <package name="com.ss.android.ugc.aweme" call-count="1" last-reset="1795566511446">
      <shortcut id="shortcut_message" title="消息">
        <intent intent-base="snssdk1128://notification?gd_label=a&amp;launch_mode=os_3dtouch">
          <extras>
            <string name="launch_method">android_3d_touch</string>
          </extras>
        </intent>
      </shortcut>
      <shortcut id="shortcut_clean" title="空间清理">
        <intent intent-base="snssdk1128://polaris/lynxview/?surl=https%3A%2F%2Fx&amp;buriedInfo=%7B%22title%22%3A%22%E5%8D%B8%E8%BD%BD%22%7D#Intent;action=android.intent.action.VIEW;end" />
      </shortcut>
      <shortcut id="shortcut_no_data" title="No data">
        <intent intent-base="#Intent;action=android.intent.action.MAIN;component=com.foo/.Main;end" />
      </shortcut>
      <shortcut id="shortcut_legacy" title="Legacy">
        <intent intent="legacy://host/path?a=1&amp;b=2#Intent;action=android.intent.action.VIEW;end" />
      </shortcut>
    </package>
    @@@/data/system_ce/0/shortcut_service/packages/com.android.settings.xml
    <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
    <package name="com.android.settings">
      <shortcut id="manifest-shortcut-wifi" title="WLAN">
        <intent intent-base="#Intent;action=android.settings.WIFI_SETTINGS;launchFlags=0x1000c000;end" />
      </shortcut>
    </package>
  """.trimIndent()

  @Test
  fun parsesFullIntentDataByPackageAndShortcutId() {
    val data = ShortcutIntentDataReader.parse(output)

    val aweme = data.getValue("com.ss.android.ugc.aweme")
    assertEquals(
      "snssdk1128://notification?gd_label=a&launch_mode=os_3dtouch",
      aweme["shortcut_message"]
    )
    assertEquals(
      "snssdk1128://polaris/lynxview/?surl=https%3A%2F%2Fx&buriedInfo=%7B%22title%22%3A%22%E5%8D%B8%E8%BD%BD%22%7D",
      aweme["shortcut_clean"]
    )
    assertEquals("legacy://host/path?a=1&b=2", aweme["shortcut_legacy"])
    assertNull(aweme["shortcut_no_data"])
  }

  @Test
  fun skipsPackagesWithoutData() {
    val data = ShortcutIntentDataReader.parse(output)

    assertEquals(emptyMap<String, String>(), data["com.android.settings"])
  }

  @Test
  fun ignoresBinaryChunks() {
    val binary = "@@@/data/system_ce/0/shortcut_service/packages/com.example.xml\nABX\u0000binary"

    assertTrue(ShortcutIntentDataReader.parse(binary)["com.example"].isNullOrEmpty())
  }

  @Test
  fun extractsDataFromIntentBase() {
    assertEquals(
      "snssdk1128://search?q=a",
      ShortcutIntentDataReader.dataOfIntentBase("snssdk1128://search?q=a")
    )
    assertEquals(
      "content://com.android.contacts/contacts",
      ShortcutIntentDataReader.dataOfIntentBase(
        "content://com.android.contacts/contacts#Intent;action=android.intent.action.INSERT;end"
      )
    )
    assertEquals(
      "https://example.com/path#fragment",
      ShortcutIntentDataReader.dataOfIntentBase(
        "https://example.com/path#fragment#Intent;action=android.intent.action.VIEW;end"
      )
    )
    assertNull(
      ShortcutIntentDataReader.dataOfIntentBase(
        "#Intent;action=android.settings.WIFI_SETTINGS;launchFlags=0x1000c000;end"
      )
    )
  }

  @Test
  fun buildsOneCommandForAllPackages() {
    val command = ShortcutIntentDataReader.buildCommand(
      listOf("com.b", "com.a"),
      userId = 0
    )

    assertTrue(command.startsWith("for f in "))
    assertTrue(
      command.contains("/data/system_ce/0/shortcut_service/packages/com.a.xml")
    )
    assertTrue(
      command.contains("/data/system_ce/0/shortcut_service/packages/com.b.xml")
    )
    assertTrue(command.indexOf("com.a.xml") < command.indexOf("com.b.xml"))
    assertTrue(command.contains("abx2xml"))
  }

  @Test
  fun usesUserSpecificDirectory() {
    val command = ShortcutIntentDataReader.buildCommand(listOf("com.example"), userId = 10)

    assertTrue(command.contains("/data/system_ce/10/shortcut_service/packages/com.example.xml"))
  }

  @Test
  fun flagsAnyDataForLookup() {
    assertTrue(ShortcutIntentDataReader.needsFullData("snssdk1128://search?q=a"))
    assertTrue(ShortcutIntentDataReader.needsFullData("content://com.foo/bar"))
    assertTrue(ShortcutIntentDataReader.needsFullData("tel:123456"))
    assertFalse(ShortcutIntentDataReader.needsFullData(null))
    assertFalse(ShortcutIntentDataReader.needsFullData(""))
  }
}
