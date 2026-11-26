package com.absinthe.anywhere_

import com.absinthe.anywhere_.model.ExtraBean
import com.absinthe.anywhere_.model.TYPE_INT
import com.absinthe.anywhere_.model.TYPE_STRING
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtraBeanTest {

  @Test
  fun `string extra keeps quotes and slashes for the shell`() {
    val value = "openApp.jdMobile://virtual?params={\"category\":\"jump\",\"url\":\"https://example.com\"}"
    val bean = ExtraBean(
      action = "android.intent.action.VIEW",
      extras = listOf(ExtraBean.ExtraItem(TYPE_STRING, "openApp", value))
    )

    assertEquals(
      "-a 'android.intent.action.VIEW' --es 'openApp' '$value'",
      bean.toString()
    )
  }

  @Test
  fun `single quote inside a value is escaped for single quoted shell strings`() {
    val bean = ExtraBean(extras = listOf(ExtraBean.ExtraItem(TYPE_STRING, "title", "it's fine")))

    assertEquals("--es 'title' 'it'\\''s fine'", bean.toString())
  }

  @Test
  fun `int extra keeps its value quoted`() {
    val bean = ExtraBean(extras = listOf(ExtraBean.ExtraItem(TYPE_INT, "sort", "3")))

    assertEquals("--ei 'sort' '3'", bean.toString())
  }
}
