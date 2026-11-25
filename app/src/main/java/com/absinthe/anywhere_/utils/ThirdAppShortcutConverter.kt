package com.absinthe.anywhere_.utils

import com.absinthe.anywhere_.constants.AnywhereType
import com.absinthe.anywhere_.model.ExtraBean
import com.absinthe.anywhere_.model.TYPE_BOOLEAN
import com.absinthe.anywhere_.model.TYPE_INT
import com.absinthe.anywhere_.model.TYPE_STRING
import com.absinthe.anywhere_.model.ThirdAppShortcut
import com.absinthe.anywhere_.model.database.AnywhereEntity
import com.google.gson.Gson

/**
 * Builds an editable [AnywhereEntity] from a shortcut published by another app.
 *
 * The card uses the activity editor fields: [AnywhereEntity.param1] is the target package,
 * [AnywhereEntity.param2] the target class and [AnywhereEntity.param3] the serialized [ExtraBean].
 * The shortcut dump only prints extra values as text, so types are inferred the same way
 * [ThirdAppShortcut.toIntent] does it: booleans and integers keep their type, everything else is a
 * string.
 *
 * [resolvedPackage]/[resolvedClass] are the component found through PackageManager when the
 * shortcut intent is implicit, so the saved card can start the exact activity instead of falling
 * back to the app's launcher activity.
 */
object ThirdAppShortcutConverter {

  fun toEntity(
    shortcut: ThirdAppShortcut,
    resolvedPackage: String? = null,
    resolvedClass: String? = null
  ): AnywhereEntity {
    val componentPackage = shortcut.component
      ?.substringBefore('/')
      ?.takeIf { it.isNotBlank() }
    val componentClass = shortcut.component
      ?.substringAfter('/', "")
      ?.takeIf { it.isNotBlank() }

    return toEntity(
      name = shortcut.label.ifBlank { shortcut.appLabel },
      appLabel = shortcut.appLabel,
      packageName = componentPackage
        ?: resolvedPackage?.takeIf { it.isNotBlank() }
        ?: shortcut.targetPackage?.takeIf { it.isNotBlank() }
        ?: shortcut.packageName,
      className = componentClass ?: resolvedClass,
      action = shortcut.action.orEmpty(),
      data = shortcut.data.orEmpty(),
      extras = shortcut.extras.map { (key, value) -> inferredExtra(key, value) }
    )
  }

  fun toEntity(
    name: String,
    appLabel: String,
    packageName: String,
    className: String?,
    action: String,
    data: String,
    extras: List<ExtraBean.ExtraItem>
  ): AnywhereEntity {
    // The opener ignores the action when a package is set without a class and launches the app's
    // main activity instead, so action-only intents have to stay implicit.
    val keepImplicit = className.isNullOrBlank() && action.isNotBlank()

    return AnywhereEntity(
      appName = name.ifBlank { appLabel.ifBlank { packageName } },
      param1 = if (keepImplicit) "" else packageName,
      param2 = className.orEmpty(),
      param3 = Gson().toJson(ExtraBean(action = action, data = data, extras = extras)),
      type = AnywhereType.Card.ACTIVITY,
      category = ""
    )
  }

  private fun inferredExtra(key: String, value: String): ExtraBean.ExtraItem = when {
    value == "true" || value == "false" -> ExtraBean.ExtraItem(TYPE_BOOLEAN, key, value)
    value.toIntOrNull() != null -> ExtraBean.ExtraItem(TYPE_INT, key, value)
    else -> ExtraBean.ExtraItem(TYPE_STRING, key, value)
  }
}
