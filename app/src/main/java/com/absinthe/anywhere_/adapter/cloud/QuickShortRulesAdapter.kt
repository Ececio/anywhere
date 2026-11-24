package com.absinthe.anywhere_.adapter.cloud

import android.view.View
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.model.cloud.QuickShortRule
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import me.zhanghai.android.fastscroll.PopupTextProvider

class QuickShortRulesAdapter :
  BaseQuickAdapter<QuickShortRule, BaseViewHolder>(R.layout.item_cloud_rules),
  PopupTextProvider {

  override fun convert(holder: BaseViewHolder, item: QuickShortRule) {
    holder.setText(R.id.tv_app_name, item.displayName)
    holder.setText(R.id.tv_contributor, item.displaySubtitle)
  }

  override fun getPopupText(view: View, position: Int): CharSequence {
    return data[position].displayName.ifEmpty { " " }.first().toString()
  }

  override fun getItemId(position: Int): Long {
    return try {
      data[position].hashCode().toLong()
    } catch (e: Exception) {
      super.getItemId(position)
    }
  }
}
