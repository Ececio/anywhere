package com.absinthe.anywhere_.ui.preview

enum class PreviewStatus {
  PENDING,
  RUNNING,
  OK,
  FAILED,
  SKIPPED
}

data class ActivityPreviewItem(
  val index: Int,
  val packageName: String,
  val className: String,
  val label: String,
  val exported: Boolean,
  val enabled: Boolean,
  val status: PreviewStatus = PreviewStatus.PENDING,
  val actual: String = "",
  val screenText: String = "",
  val note: String = "",
  val duplicateOf: Int? = null,
  val shotPath: String? = null,
  val thumbPath: String? = null,
  val hash: LongArray? = null
) {
  val shortName: String get() = className.substringAfterLast('.')

  val component: String get() = "$packageName/$className"

  val hasImage: Boolean get() = !shotPath.isNullOrEmpty()
}
