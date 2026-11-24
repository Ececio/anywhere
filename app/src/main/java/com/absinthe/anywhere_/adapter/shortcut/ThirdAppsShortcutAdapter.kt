package com.absinthe.anywhere_.adapter.shortcut

import com.absinthe.anywhere_.R
import com.blankj.utilcode.util.AppUtils
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder

class ThirdAppsShortcutAdapter :
  BaseMultiItemQuickAdapter<ThirdAppsShortcutItem, BaseViewHolder>() {

  init {
    addItemType(TYPE_HEADER, R.layout.item_shortcut_header)
    addItemType(TYPE_APP, R.layout.item_app_list)
    addItemType(TYPE_SHORTCUT, R.layout.item_app_list)
  }

  override fun convert(holder: BaseViewHolder, item: ThirdAppsShortcutItem) {
    when (item) {
      is ThirdAppsShortcutItem.Header -> {
        holder.setText(R.id.tv_shortcut_header, item.title)
      }

      is ThirdAppsShortcutItem.App -> {
        holder.setText(R.id.tv_app_name, AppUtils.getAppName(item.info.activityInfo.packageName))
        holder.setImageDrawable(R.id.iv_app_icon, item.info.loadIcon(context.packageManager))
        holder.setText(
          R.id.tv_pkg_name,
          (item.info.loadLabel(context.packageManager) ?: "App").toString()
        )
      }

      is ThirdAppsShortcutItem.Shortcut -> {
        holder.setText(R.id.tv_app_name, item.shortcut.label)
        holder.setText(R.id.tv_pkg_name, item.shortcut.appLabel)
        holder.setImageDrawable(R.id.iv_app_icon, item.shortcut.icon)
      }
    }
  }
}
