package com.absinthe.anywhere_.model

import com.google.gson.annotations.SerializedName

const val TYPE_STRING = "--es"
const val TYPE_BOOLEAN = "--ez"
const val TYPE_INT = "--ei"
const val TYPE_LONG = "--el"
const val TYPE_FLOAT = "--ef"
const val TYPE_DOUBLE = "--ed"
const val TYPE_URI = "--eu"

const val TYPE_STRING_LABEL = "String"
const val TYPE_BOOLEAN_LABEL = "Bool"
const val TYPE_INT_LABEL = "Int"
const val TYPE_LONG_LABEL = "Long"
const val TYPE_FLOAT_LABEL = "Float"
const val TYPE_DOUBLE_LABEL = "Double"
const val TYPE_URI_LABEL = "Uri"

data class ExtraBean(
  @SerializedName("data") val data: String = "",
  @SerializedName("action") val action: String = "",
  @SerializedName("category") val category: String = "",
  @SerializedName("extras") val extras: List<ExtraItem> = emptyList()
) {
  override fun toString(): String {
    val sb = StringBuilder()
    if (action.isNotBlank()) {
      sb.append("-a ").append(quoteForShell(action))
    }
    if (data.isNotBlank()) {
      sb.append(" -d ").append(quoteForShell(data))
    }

    for (extra in extras) {
      if (sb.isNotEmpty()) {
        sb.append(' ')
      }
      sb.append(extra.toString())
    }
    return sb.toString()
  }

  data class ExtraItem(
    @SerializedName("type") var type: String,
    @SerializedName("key") var key: String,
    @SerializedName("value") var value: String
  ) {
    override fun toString(): String {
      return "$type ${quoteForShell(key)} ${quoteForShell(value)}"
    }
  }

  companion object {
    /**
     * Quotes a command argument for the shell used by [com.absinthe.anywhere_.utils.CommandUtils].
     *
     * Double quotes are not enough: extra values can contain quotes, `$`, backticks and `//`
     * (JSON payloads for example), and the shell would strip or expand them before `am` sees them.
     * Single quotes keep the value byte for byte; an embedded single quote is written as `'\''`.
     */
    fun quoteForShell(value: String): String {
      return "'" + value.replace("'", "'\\''") + "'"
    }
  }
}
