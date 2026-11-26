package com.absinthe.anywhere_.ui.preview

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Keeps the latest probe result on disk so a long run survives the activity
 * being recreated or the process being killed in the background.
 */
object ActivityPreviewStore {

  private val gson = Gson()

  private data class State(
    val packageName: String = "",
    val appName: String = "",
    val items: List<ActivityPreviewItem> = emptyList()
  )

  fun save(context: Context, packageName: String, appName: String, items: List<ActivityPreviewItem>) {
    runCatching {
      val state = State(packageName, appName, items)
      file(context).writeText(gson.toJson(state), Charsets.UTF_8)
    }
  }

  fun load(context: Context): Pair<String, List<ActivityPreviewItem>>? {
    return runCatching {
      val file = file(context)
      if (!file.exists()) return null
      val type = object : TypeToken<State>() {}.type
      val state: State = gson.fromJson(file.readText(Charsets.UTF_8), type) ?: return null
      if (state.packageName.isEmpty() || state.items.isEmpty()) return null
      state.packageName to state.items
    }.getOrNull()
  }

  fun clear(context: Context) {
    runCatching { file(context).delete() }
  }

  private fun file(context: Context): File {
    return File(File(context.getExternalFilesDir(null), ActivityPreviewRunner.DIR_NAME), "state.json")
  }
}
