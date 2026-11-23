package com.absinthe.anywhere_.view.card

import android.content.Context
import android.graphics.PorterDuff
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.view.app.SpeedMarqueeTextView
import com.absinthe.libraries.utils.extensions.dp

/**
 * Compact home page card: the icon on top and a centered, single-line, auto-scrolling name below.
 * Icon size, text size, padding and spacing follow the compact layout settings.
 */
class CompactItemView(context: Context) : ConstraintLayout(context) {

  val icon: AppCompatImageView = AppCompatImageView(context).apply {
    id = View.generateViewId()
    backgroundTintList = ContextCompat.getColorStateList(
      context,
      com.google.android.material.R.color.material_on_surface_emphasis_medium
    )
    backgroundTintMode = PorterDuff.Mode.ADD
  }

  val badge: ImageView = ImageView(context).apply {
    id = View.generateViewId()
    contentDescription = context.getString(R.string.icon_badge_todo)
    visibility = View.GONE
  }

  val indicator: ImageView = ImageView(context).apply {
    id = View.generateViewId()
    visibility = View.GONE
  }

  val appName: SpeedMarqueeTextView = SpeedMarqueeTextView(context).apply {
    id = View.generateViewId()
    setTextSize(TypedValue.COMPLEX_UNIT_SP, GlobalValues.compactTextSizeSp)
    includeFontPadding = false
    centerWhenFits = true
  }

  init {
    id = R.id.card_container

    val padding = GlobalValues.compactPadding
    setPadding(padding[0].dp, padding[1].dp, padding[2].dp, padding[3].dp)

    val iconSize = GlobalValues.compactIconSizeDp.dp
    addView(icon, LayoutParams(iconSize, iconSize).apply {
      startToStart = LayoutParams.PARENT_ID
      endToEnd = LayoutParams.PARENT_ID
      topToTop = LayoutParams.PARENT_ID
    })

    addView(badge, LayoutParams(9.dp, 9.dp).apply {
      endToEnd = icon.id
      topToTop = icon.id
    })

    addView(indicator, LayoutParams(9.dp, 9.dp).apply {
      endToEnd = icon.id
      bottomToBottom = icon.id
    })

    addView(appName, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
      startToStart = LayoutParams.PARENT_ID
      endToEnd = LayoutParams.PARENT_ID
      topToBottom = icon.id
      topMargin = GlobalValues.compactIconTextGapDp.dp
      bottomToBottom = LayoutParams.PARENT_ID
    })
  }
}
