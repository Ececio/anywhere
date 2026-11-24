package com.absinthe.anywhere_.model

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import com.absinthe.anywhere_.utils.ShortcutDumpParser

/**
 * A static or dynamic shortcut published by another app, reconstructed from [ShortcutDumpParser].
 *
 * The dump only prints extra values as text, so types are inferred: booleans and integers keep
 * their type, everything else is passed as a string.
 */
data class ThirdAppShortcut(
  val id: String,
  val packageName: String,
  val appLabel: String,
  val label: String,
  val kind: ShortcutDumpParser.Kind,
  val rank: Int,
  val action: String?,
  val data: String?,
  val mimeType: String?,
  val flags: Int,
  val targetPackage: String?,
  val component: String?,
  val categories: List<String>,
  val extras: Map<String, String>,
  val icon: Drawable?
) {

  fun toIntent(): Intent {
    val intent = Intent()

    action?.takeIf { it.isNotBlank() }?.let { intent.action = it }
    data?.takeIf { it.isNotBlank() }?.let { intent.data = Uri.parse(it) }
    mimeType?.takeIf { it.isNotBlank() }?.let { intent.type = it }
    component?.let { ComponentName.unflattenFromString(it) }?.let { intent.component = it }
    targetPackage?.takeIf { it.isNotBlank() }?.let { intent.setPackage(it) }
    categories.forEach { intent.addCategory(it) }
    intent.addFlags(flags or Intent.FLAG_ACTIVITY_NEW_TASK)
    extras.forEach { (key, value) -> intent.putInferredExtra(key, value) }

    return intent
  }

  private fun Intent.putInferredExtra(key: String, value: String) {
    when {
      value == "true" || value == "false" -> putExtra(key, value.toBoolean())
      value.toIntOrNull() != null -> putExtra(key, value.toInt())
      else -> putExtra(key, value)
    }
  }
}
