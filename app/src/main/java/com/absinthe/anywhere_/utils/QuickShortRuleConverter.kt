package com.absinthe.anywhere_.utils

import androidx.annotation.Keep
import com.absinthe.anywhere_.constants.AnywhereType
import com.absinthe.anywhere_.model.ExtraBean
import com.absinthe.anywhere_.model.cloud.QuickShortRule
import com.absinthe.anywhere_.model.database.AnywhereEntity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Converts QuickShort rules into Anywhere cards.
 *
 * Only the conversions that keep the original behaviour are accepted. Anything else is reported
 * as [Result.Unsupported] so the caller can tell the user instead of adding a broken card.
 */
object QuickShortRuleConverter {

  private const val TYPE_ACTIVITY = 0
  private const val TYPE_URL_SCHEME = 2
  private const val TYPE_SHELL = 3
  private const val TYPE_SWITCH = 7

  private const val SWITCH_ITEM_TYPE_SHELL = 3
  private const val SWITCH_OFF = "off"

  sealed class Result {
    data class Supported(val entity: AnywhereEntity) : Result()
    data class Unsupported(val reason: String) : Result()
  }

  fun convert(rule: QuickShortRule): Result {
    val name = rule.displayName.ifBlank { "QuickShort" }

    return when (rule.type) {
      TYPE_ACTIVITY -> convertActivity(rule, name)
      TYPE_URL_SCHEME -> convertUrlScheme(rule, name)
      TYPE_SHELL -> convertShell(rule, name)
      TYPE_SWITCH -> convertSwitch(rule, name)
      else -> Result.Unsupported("Unsupported rule type: ${rule.type}")
    }
  }

  private fun convertActivity(rule: QuickShortRule, name: String): Result {
    val packageName = rule.pkg.orEmpty().trim()
    val className = rule.path.orEmpty().trim()
    val extras = rule.extras.orEmpty()
    val extraBean = parseExtraBean(extras)

    if (packageName.isBlank() && extraBean?.action.isNullOrBlank()) {
      return Result.Unsupported("Activity rule without package name or intent action")
    }

    return Result.Supported(
      createEntity(name, AnywhereType.Card.ACTIVITY).apply {
        param1 = packageName
        param2 = className
        param3 = extras
      }
    )
  }

  private fun convertUrlScheme(rule: QuickShortRule, name: String): Result {
    val url = rule.path.orEmpty().trim()

    if (url.isBlank()) {
      return Result.Unsupported("URL scheme rule without path")
    }

    return Result.Supported(
      createEntity(name, AnywhereType.Card.URL_SCHEME).apply {
        param1 = url
      }
    )
  }

  private fun convertShell(rule: QuickShortRule, name: String): Result {
    val command = rule.path.orEmpty().trim()

    if (command.isBlank()) {
      return Result.Unsupported("Shell rule without command")
    }

    return Result.Supported(
      createEntity(name, AnywhereType.Card.SHELL).apply {
        param1 = command
      }
    )
  }

  private fun convertSwitch(rule: QuickShortRule, name: String): Result {
    // QuickShort stores the "on" action in pkg and the "off" action in path.
    val onItem = parseSwitchItem(rule.pkg)
    val offItem = parseSwitchItem(rule.path)

    if (onItem == null || offItem == null) {
      return Result.Unsupported("Switch rule without both states")
    }
    if (onItem.type != SWITCH_ITEM_TYPE_SHELL || offItem.type != SWITCH_ITEM_TYPE_SHELL) {
      return Result.Unsupported("Switch rule is not made of shell commands")
    }

    val onCommand = onItem.path.orEmpty().trim()
    val offCommand = offItem.path.orEmpty().trim()

    if (onCommand.isBlank() || offCommand.isBlank()) {
      return Result.Unsupported("Switch rule with empty shell command")
    }

    return Result.Supported(
      createEntity(name, AnywhereType.Card.SWITCH_SHELL).apply {
        param1 = onCommand
        param2 = offCommand
        param3 = SWITCH_OFF
      }
    )
  }

  private fun parseExtraBean(extras: String): ExtraBean? {
    if (extras.isBlank()) {
      return null
    }

    return try {
      Gson().fromJson(extras, ExtraBean::class.java)
    } catch (e: Exception) {
      null
    }
  }

  private fun parseSwitchItem(raw: String?): QuickShortSwitchItem? {
    if (raw.isNullOrBlank()) {
      return null
    }

    return try {
      Gson().fromJson(raw, QuickShortSwitchWrapper::class.java)?.item
    } catch (e: Exception) {
      null
    }
  }

  private fun createEntity(name: String, type: Int) = AnywhereEntity(
    appName = name,
    type = type,
    category = ""
  )
}

@Keep
private data class QuickShortSwitchWrapper(
  @SerializedName("item") val item: QuickShortSwitchItem? = null
)

@Keep
private data class QuickShortSwitchItem(
  @SerializedName("type") val type: Int = 0,
  @SerializedName("path") val path: String? = null
)
