package com.absinthe.anywhere_.ui.preview

import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.absinthe.anywhere_.R
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import java.io.File

class ActivityPreviewAdapter :
  BaseQuickAdapter<ActivityPreviewItem, BaseViewHolder>(R.layout.item_activity_preview) {

  override fun convert(holder: BaseViewHolder, item: ActivityPreviewItem) {
    holder.setText(R.id.tv_title, "#${item.index}  ${item.shortName}")
    holder.setText(R.id.tv_label, item.label)
    holder.setText(R.id.tv_component, item.component)
    holder.setText(R.id.tv_status, statusText(item))
    holder.setTextColor(R.id.tv_status, statusColor(holder, item))
    holder.setText(R.id.tv_text, item.screenText)

    val note = buildString {
      if (item.duplicateOf != null) append("duplicate of #${item.duplicateOf}")
      if (item.note.isNotEmpty()) {
        if (isNotEmpty()) append(" · ")
        append(item.note)
      }
    }
    holder.setGone(R.id.tv_note, note.isEmpty())
    holder.setText(R.id.tv_note, note)

    val image = holder.getView<ImageView>(R.id.iv_thumb)
    val path = item.thumbPath ?: item.shotPath
    if (path.isNullOrEmpty()) {
      Glide.with(image).clear(image)
      image.setImageDrawable(null)
    } else {
      Glide.with(image)
        .load(File(path))
        .centerCrop()
        .into(image)
    }
  }

  private fun statusText(item: ActivityPreviewItem): String = when (item.status) {
    PreviewStatus.OK -> "OK"
    PreviewStatus.FAILED -> "FAILED"
    PreviewStatus.SKIPPED -> "SKIPPED"
    PreviewStatus.RUNNING -> "RUNNING"
    PreviewStatus.PENDING -> "PENDING"
  }

  private fun statusColor(holder: BaseViewHolder, item: ActivityPreviewItem): Int {
    val color = when (item.status) {
      PreviewStatus.OK -> R.color.preview_ok
      PreviewStatus.FAILED -> R.color.preview_failed
      PreviewStatus.SKIPPED -> R.color.preview_skipped
      else -> R.color.preview_skipped
    }
    return ContextCompat.getColor(holder.itemView.context, color)
  }
}
