package com.absinthe.anywhere_.ui.main

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.constants.AnywhereType
import com.absinthe.anywhere_.constants.EventTag
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.databinding.DialogCustomizeBinding
import com.absinthe.anywhere_.model.database.AnywhereEntity
import com.absinthe.anywhere_.ui.cloud.CloudRulesActivity
import com.absinthe.anywhere_.ui.editor.EXTRA_EDIT_MODE
import com.absinthe.anywhere_.ui.editor.EXTRA_ENTITY
import com.absinthe.anywhere_.ui.editor.EditorActivity
import com.absinthe.anywhere_.ui.list.AppListActivity
import com.absinthe.anywhere_.ui.qrcode.QRCodeCollectionActivity
import com.absinthe.anywhere_.ui.shortcuts.ThirdAppsShortcutActivity
import com.absinthe.anywhere_.utils.manager.CardTypeIconGenerator
import com.absinthe.libraries.utils.extensions.dp
import com.absinthe.libraries.utils.extensions.getColorByAttr
import com.microsoft.appcenter.analytics.Analytics

/**
 * The "Customize" panel opened from the home screen button.
 *
 * It is shared by the full screen home ([MainActivity]) and the lightweight popup home
 * ([HomePopupActivity]) so both entry points expose exactly the same actions.
 */
class CustomizePanel(
  private val activity: Activity,
  private val anchor: View,
  private val onStartCollector: () -> Unit
) {

  private var popup: PopupWindow? = null

  val isShowing: Boolean
    get() = popup?.isShowing == true

  fun toggle() {
    if (isShowing) {
      dismiss()
    } else {
      show()
    }
  }

  fun dismiss() {
    popup?.dismiss()
    popup = null
  }

  private fun show() {
    val sheet = DialogCustomizeBinding.inflate(activity.layoutInflater)

    val cardTypes = listOf(
      R.string.btn_url_scheme to AnywhereType.Card.URL_SCHEME,
      R.string.btn_activity to AnywhereType.Card.ACTIVITY,
      R.string.btn_add_image to AnywhereType.Card.IMAGE,
      R.string.btn_add_shell to AnywhereType.Card.SHELL,
      R.string.btn_add_switch_shell to AnywhereType.Card.SWITCH_SHELL,
      R.string.btn_add_file to AnywhereType.Card.FILE,
      R.string.btn_add_broadcast to AnywhereType.Card.BROADCAST,
      R.string.btn_add_accessibility to AnywhereType.Card.ACCESSIBILITY,
      R.string.btn_add_workflow to AnywhereType.Card.WORKFLOW
    )
    cardTypes.forEach { (titleRes, type) ->
      addCustomizeEntry(
        sheet.containerCards,
        CardTypeIconGenerator.getAdvancedIcon(activity, type, 26.dp),
        activity.getString(titleRes)
      ) {
        dismiss()
        openCardEditor(type)
      }
    }

    addCustomizeEntry(
      sheet.containerTools,
      getTintedIcon(R.drawable.ic_activity_list),
      activity.getString(R.string.btn_activity_list)
    ) {
      dismiss()
      activity.startActivity(Intent(activity, AppListActivity::class.java))
      Analytics.trackEvent(EventTag.FAB_ACTIVITY_LIST_CLICK)
    }

    addCustomizeEntry(
      sheet.containerTools,
      getTintedIcon(R.drawable.ic_logo),
      GlobalValues.collectorMode
    ) {
      dismiss()
      onStartCollector()
    }

    addCustomizeEntry(
      sheet.containerTools,
      getTintedIcon(R.drawable.ic_qr_code),
      activity.getString(R.string.btn_qr_code_collection)
    ) {
      dismiss()
      activity.startActivity(Intent(activity, QRCodeCollectionActivity::class.java))
      Analytics.trackEvent(EventTag.FAB_QR_CODE_COLLECTION_CLICK)
    }

    addCustomizeEntry(
      sheet.containerTools,
      getTintedIcon(R.drawable.ic_cloud_backup),
      activity.getString(R.string.btn_cloud_rules)
    ) {
      dismiss()
      activity.startActivity(Intent(activity, CloudRulesActivity::class.java))
      Analytics.trackEvent(EventTag.FAB_CLOUD_RULES_CLICK)
    }

    addCustomizeEntry(
      sheet.containerTools,
      getTintedIcon(R.drawable.ic_add_shortcut),
      activity.getString(R.string.btn_third_apps_shortcut)
    ) {
      dismiss()
      activity.startActivity(Intent(activity, ThirdAppsShortcutActivity::class.java))
      Analytics.trackEvent(EventTag.FAB_THIRD_APPS_SHORTCUT_CLICK)
    }

    val root = anchor.rootView
    val rootWidth = root.width.takeIf { it > 0 }
      ?: activity.resources.displayMetrics.widthPixels
    val rootHeight = root.height.takeIf { it > 0 }
      ?: activity.resources.displayMetrics.heightPixels
    val popupWidth = rootWidth - 24.dp
    val maxHeight = activity.resources.displayMetrics.heightPixels - 160.dp

    sheet.root.measure(
      View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
    )
    val popupHeight = sheet.root.measuredHeight

    val window = PopupWindow(sheet.root, popupWidth, popupHeight, true).apply {
      setBackgroundDrawable(
        ContextCompat.getDrawable(activity, R.drawable.bg_customize_popup)
      )
      isOutsideTouchable = true
      elevation = 8.dp.toFloat()
      setOnDismissListener { popup = null }
    }

    // Show the panel in the middle of the screen instead of above the button.
    val rootLocation = IntArray(2)
    root.getLocationOnScreen(rootLocation)
    val x = rootLocation[0] + (rootWidth - popupWidth) / 2
    val y = rootLocation[1] + (rootHeight - popupHeight) / 2
    window.showAtLocation(root, Gravity.NO_GRAVITY, x, y)

    popup = window
  }

  private fun addCustomizeEntry(
    container: LinearLayout,
    icon: Drawable?,
    title: String,
    onClick: () -> Unit
  ) {
    val item = activity.layoutInflater.inflate(R.layout.item_customize_entry, container, false)
    item.findViewById<ImageView>(R.id.iv_icon).setImageDrawable(icon)
    item.findViewById<TextView>(R.id.tv_title).text = title
    item.setOnClickListener { view ->
      view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
      onClick()
    }
    container.addView(item)
  }

  private fun getTintedIcon(resId: Int): Drawable? {
    return ContextCompat.getDrawable(activity, resId)?.mutate()?.apply {
      setTint(activity.getColorByAttr(com.google.android.material.R.attr.colorPrimary))
    }
  }

  private fun openCardEditor(type: Int) {
    val ae = AnywhereEntity().apply {
      this.type = type
      appName = AnywhereType.Card.NEW_TITLE_MAP[type] ?: "New Card"
    }
    activity.startActivity(Intent(activity, EditorActivity::class.java).apply {
      putExtra(EXTRA_ENTITY, ae)
      putExtra(EXTRA_EDIT_MODE, false)
    })
  }
}
