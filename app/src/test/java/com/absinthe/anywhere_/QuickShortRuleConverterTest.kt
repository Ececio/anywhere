package com.absinthe.anywhere_

import com.absinthe.anywhere_.constants.AnywhereType
import com.absinthe.anywhere_.model.cloud.QuickShortRule
import com.absinthe.anywhere_.utils.QuickShortRuleConverter
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickShortRuleConverterTest {

  private fun parse(json: String): QuickShortRule = Gson().fromJson(json, QuickShortRule::class.java)

  @Test
  fun convertsActivityRule() {
    val rule = parse(
      """
      {
        "title": "电池信息",
        "extras": "{\"action\":\"android.intent.action.POWER_USAGE_SUMMARY\",\"category\":\"\",\"data\":\"\",\"extras\":[]}",
        "type": 0
      }
      """
    )

    val result = QuickShortRuleConverter.convert(rule)

    assertTrue(result is QuickShortRuleConverter.Result.Supported)
    val entity = (result as QuickShortRuleConverter.Result.Supported).entity
    assertEquals(AnywhereType.Card.ACTIVITY, entity.type)
    assertEquals("电池信息", entity.appName)
    assertEquals("", entity.param1)
    assertEquals("", entity.param2)
    assertTrue(entity.param3.orEmpty().contains("POWER_USAGE_SUMMARY"))
  }

  @Test
  fun convertsUrlSchemeRule() {
    val rule = parse(
      """
      {
        "name": "搜索",
        "path": "snssdk1128://search/",
        "title": "抖音 搜索",
        "type": 2
      }
      """
    )

    val result = QuickShortRuleConverter.convert(rule)

    assertTrue(result is QuickShortRuleConverter.Result.Supported)
    val entity = (result as QuickShortRuleConverter.Result.Supported).entity
    assertEquals(AnywhereType.Card.URL_SCHEME, entity.type)
    assertEquals("抖音 搜索", entity.appName)
    assertEquals("snssdk1128://search/", entity.param1)
  }

  @Test
  fun convertsShellRule() {
    val rule = parse(
      """
      {
        "name": "截图",
        "path": "input keyevent 120",
        "title": "shell截图",
        "type": 3
      }
      """
    )

    val result = QuickShortRuleConverter.convert(rule)

    assertTrue(result is QuickShortRuleConverter.Result.Supported)
    val entity = (result as QuickShortRuleConverter.Result.Supported).entity
    assertEquals(AnywhereType.Card.SHELL, entity.type)
    assertEquals("input keyevent 120", entity.param1)
  }

  @Test
  fun convertsShellSwitchRule() {
    val rule = parse(
      """
      {
        "name": "谷歌服务",
        "path": "{\"item\":{\"name\":\"冻结\",\"path\":\"pm disable-user com.android.vending\",\"type\":3}}",
        "pkg": "{\"item\":{\"name\":\"解冻\",\"path\":\"pm enable com.android.vending\",\"type\":3}}",
        "title": "谷歌服务开关",
        "type": 7
      }
      """
    )

    val result = QuickShortRuleConverter.convert(rule)

    assertTrue(result is QuickShortRuleConverter.Result.Supported)
    val entity = (result as QuickShortRuleConverter.Result.Supported).entity
    assertEquals(AnywhereType.Card.SWITCH_SHELL, entity.type)
    assertEquals("pm enable com.android.vending", entity.param1)
    assertEquals("pm disable-user com.android.vending", entity.param2)
    assertEquals("off", entity.param3)
  }

  @Test
  fun rejectsSwitchRuleWithoutShellCommands() {
    val rule = parse(
      """
      {
        "name": "蓝牙",
        "path": "{\"item\":{\"name\":\"关蓝牙\",\"path\":\"com.syyf.quickpay.act.ToggleBleActivity\"}}",
        "pkg": "{\"item\":{\"name\":\"开蓝牙\",\"path\":\"com.syyf.quickpay.act.ToggleBleActivity\"}}",
        "title": "蓝牙开关",
        "type": 7
      }
      """
    )

    assertTrue(QuickShortRuleConverter.convert(rule) is QuickShortRuleConverter.Result.Unsupported)
  }

  @Test
  fun rejectsActivityRuleWithoutPackageAndAction() {
    val rule = parse("""{"title":"Empty","type":0}""")

    assertTrue(QuickShortRuleConverter.convert(rule) is QuickShortRuleConverter.Result.Unsupported)
  }

  @Test
  fun detectsRuleCollection() {
    val rule = parse(
      """
      {
        "title": "A 支付宝合集",
        "pkg": "com.syyf.quickpay",
        "path": "com.syyf.quickpay.act.RulesActivity",
        "extras": "{\"action\":\"\",\"category\":\"\",\"data\":\"\",\"extras\":[{\"key\":\"url\",\"type\":\"--es\",\"value\":\"https://gitee.com/night99/filebackup/raw/master/quickpay/rule/rules-alipay.json\"},{\"key\":\"title\",\"type\":\"--es\",\"value\":\"支付宝合集\"}]}",
        "type": 0
      }
      """
    )

    val collection = QuickShortRuleConverter.collectionOf(rule)

    assertTrue(collection != null)
    assertEquals("支付宝合集", collection?.title)
    assertEquals(
      "https://gitee.com/night99/filebackup/raw/master/quickpay/rule/rules-alipay.json",
      collection?.url
    )
  }

  @Test
  fun collectionFallsBackToRuleTitle() {
    val rule = parse(
      """
      {
        "title": "A 微信合集",
        "extras": "{\"extras\":[{\"key\":\"url\",\"type\":\"--es\",\"value\":\"https://example.com/rules-wx.json\"}]}",
        "type": 0
      }
      """
    )

    assertEquals("A 微信合集", QuickShortRuleConverter.collectionOf(rule)?.title)
  }

  @Test
  fun ignoresRuleWithoutCollectionUrl() {
    val rule = parse(
      """
      {
        "title": "电池信息",
        "extras": "{\"action\":\"android.intent.action.POWER_USAGE_SUMMARY\",\"extras\":[]}",
        "type": 0
      }
      """
    )

    assertTrue(QuickShortRuleConverter.collectionOf(rule) == null)
  }

  @Test
  fun ignoresNonJsonCollectionUrl() {
    val rule = parse(
      """
      {
        "title": "Not a collection",
        "extras": "{\"extras\":[{\"key\":\"url\",\"type\":\"--es\",\"value\":\"https://example.com/page.html\"}]}",
        "type": 0
      }
      """
    )

    assertTrue(QuickShortRuleConverter.collectionOf(rule) == null)
  }

  @Test
  fun ignoresCollectionUrlOnNonActivityRule() {
    val rule = parse(
      """
      {
        "title": "URL rule",
        "path": "https://example.com/rules.json",
        "extras": "{\"extras\":[{\"key\":\"url\",\"type\":\"--es\",\"value\":\"https://example.com/rules.json\"}]}",
        "type": 2
      }
      """
    )

    assertTrue(QuickShortRuleConverter.collectionOf(rule) == null)
  }

  @Test
  fun rejectsUnknownRuleType() {
    val rule = parse("""{"title":"Unknown","path":"something","type":99}""")

    assertTrue(QuickShortRuleConverter.convert(rule) is QuickShortRuleConverter.Result.Unsupported)
  }
}
