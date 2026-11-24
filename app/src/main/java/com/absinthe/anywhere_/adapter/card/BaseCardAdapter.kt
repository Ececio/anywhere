package com.absinthe.anywhere_.adapter.card

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.absinthe.anywhere_.AnywhereApplication
import com.absinthe.anywhere_.BaseActivity
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.adapter.ItemTouchCallBack
import com.absinthe.anywhere_.constants.AnywhereType
import com.absinthe.anywhere_.constants.Const
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.model.database.AnywhereEntity
import com.absinthe.anywhere_.ui.dialog.EXTRA_FROM_WORKFLOW
import com.absinthe.anywhere_.ui.editor.EXTRA_EDIT_MODE
import com.absinthe.anywhere_.ui.editor.EXTRA_ENTITY
import com.absinthe.anywhere_.ui.editor.EditorActivity
import com.absinthe.anywhere_.ui.editor.impl.SWITCH_OFF
import com.absinthe.anywhere_.ui.editor.impl.SWITCH_ON
import com.absinthe.anywhere_.ui.qrcode.QRCodeCollectionActivity
import com.absinthe.anywhere_.utils.AppUtils.atLeastO
import com.absinthe.anywhere_.utils.AppUtils.isAppFrozen
import com.absinthe.anywhere_.utils.ShortcutsUtils
import com.absinthe.anywhere_.utils.UxUtils
import com.absinthe.anywhere_.utils.handler.Opener
import com.absinthe.anywhere_.view.card.*
import com.absinthe.libraries.utils.extensions.dp
import com.absinthe.libraries.utils.extensions.getColorByAttr
import com.blankj.utilcode.util.Utils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.catchingnow.icebox.sdk_client.IceBox
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ADAPTER_MODE_NORMAL = 0
const val ADAPTER_MODE_SORT = 1
const val ADAPTER_MODE_SELECT = 2

const val LAYOUT_MODE_LARGE = 0
const val LAYOUT_MODE_MEDIUM = 1
const val LAYOUT_MODE_SMALL = 2
const val LAYOUT_MODE_MINIMUM = 3

const val SNOW_FLAKE_EMOJI = "\u2744"

private const val ITEM_TYPE_GROUP_HEADER = -100

class BaseCardAdapter(
  private val layoutMode: Int,
  private val lifecycleCoroutineScope: LifecycleCoroutineScope
) : BaseQuickAdapter<CardListItem, BaseViewHolder>(0), ItemTouchCallBack.OnItemTouchListener {

  var mode = ADAPTER_MODE_NORMAL
  var onHeaderClick: ((String) -> Unit)? = null
  private val selectedIndex = mutableListOf<Int>()
  private val groupIconCache = mutableMapOf<String, Drawable?>()

  override fun getDefItemViewType(position: Int): Int {
    return if (data.getOrNull(position) is CardListItem.Header) {
      ITEM_TYPE_GROUP_HEADER
    } else {
      0
    }
  }

  override fun isFixedViewType(type: Int): Boolean {
    return super.isFixedViewType(type) || type == ITEM_TYPE_GROUP_HEADER
  }

  /** Group headers span the whole row; the grid layout manager asks this for every position. */
  fun isGroupHeader(position: Int): Boolean {
    return getItemViewType(position) == ITEM_TYPE_GROUP_HEADER
  }

  override fun onCreateDefViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
    if (viewType == ITEM_TYPE_GROUP_HEADER) {
      val header = LayoutInflater.from(context)
        .inflate(R.layout.item_card_group_header, parent, false)
      // The width is set by the layout manager so the header fills the whole row.
      header.layoutParams = RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      return createBaseViewHolder(header)
    }

    return when (layoutMode) {
      LAYOUT_MODE_LARGE -> createBaseViewHolder(CardItemView(context, NormalItemView(context)))
      LAYOUT_MODE_MEDIUM -> createBaseViewHolder(CardItemView(context, StreamItemView(context)))
      LAYOUT_MODE_SMALL -> createBaseViewHolder(
        CardItemView(
          context,
          CompactItemView(context)
        )
      )
      LAYOUT_MODE_MINIMUM -> createBaseViewHolder(CardItemView(context, MinimumItemView(context)))
      else -> createBaseViewHolder(CardItemView(context, NormalItemView(context)))
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun convert(holder: BaseViewHolder, cardItem: CardListItem) {
    if (cardItem is CardListItem.Header) {
      holder.setText(R.id.tv_group_title, cardItem.title)
      holder.getView<ImageView>(R.id.iv_group_arrow).rotation =
        if (cardItem.collapsed) 0f else 90f
      bindGroupIcon(holder.getView(R.id.iv_group_icon), cardItem)
      holder.itemView.isClickable = true
      holder.itemView.isLongClickable = false
      return
    }

    val item = (cardItem as CardListItem.Card).entity
    val itemView = holder.itemView as CardItemView<*>

    val displayName = item.appName
      .removeSuffix(" (Launcher)")
      .removeSuffix(" (Exported)")
    itemView.appName.text = try {
      if (IceBox.getAppEnabledSetting(context, item.packageName) != 0) {
        SNOW_FLAKE_EMOJI + displayName
      } else {
        displayName
      }
    } catch (e: PackageManager.NameNotFoundException) {
      displayName
    }

    when (layoutMode) {
      LAYOUT_MODE_LARGE -> {
        val normalView = itemView as CardItemView<NormalItemView>

        normalView.content.description.isGone = item.description.isNullOrEmpty()
        normalView.content.param1.isGone = item.type == AnywhereType.Card.QR_CODE
          || item.type == AnywhereType.Card.IMAGE
          || item.type == AnywhereType.Card.BROADCAST
          || item.type == AnywhereType.Card.WORKFLOW
          || item.type == AnywhereType.Card.ACCESSIBILITY
        normalView.content.param2.isGone = item.type == AnywhereType.Card.URL_SCHEME
          || item.type == AnywhereType.Card.QR_CODE
          || item.type == AnywhereType.Card.IMAGE
          || item.type == AnywhereType.Card.BROADCAST
          || item.type == AnywhereType.Card.WORKFLOW

        normalView.content.description.text = item.description
        normalView.content.param1.text = item.param1
        normalView.content.param2.text = item.param2
      }
      LAYOUT_MODE_MEDIUM, LAYOUT_MODE_SMALL -> {
        val normalView: CardItemView<StreamItemView>? = if (layoutMode == LAYOUT_MODE_MEDIUM) {
          itemView as CardItemView<StreamItemView>
        } else {
          null
        }

        if (layoutMode == LAYOUT_MODE_MEDIUM) {
          normalView!!.content.description.text = item.description
        }

        if (GlobalValues.sCardBackgroundMode == Const.CARD_BG_MODE_PURE) {
          if (item.color == 0) {
            if (item.packageName.isNotEmpty()) {
              lifecycleCoroutineScope.launch {
                UxUtils.setCardUseIconColor(UxUtils.getAppIcon(context, item, 45.dp)) { color ->
                  item.color = color

                  if (shouldUpdateColorInfo(context, item)) {
                    AnywhereApplication.sRepository.update(item)
                  } else {
                    itemView.cardBackground.post {
                      itemView.cardBackground.background = ColorDrawable(color)
                      itemView.appName.setTextColor(if (UxUtils.isLightColor(color)) Color.BLACK else Color.WHITE)
                      normalView?.content?.description?.setTextColor(if (UxUtils.isLightColor(color)) Color.BLACK else Color.WHITE)
                    }
                  }
                }
              }
            }
            itemView.cardBackground.setImageDrawable(null)
          } else {
            itemView.cardBackground.post {
              itemView.cardBackground.background = ColorDrawable(item.color)
              itemView.appName.setTextColor(if (UxUtils.isLightColor(item.color)) Color.BLACK else Color.WHITE)
              normalView?.content?.description?.setTextColor(if (UxUtils.isLightColor(item.color)) Color.BLACK else Color.WHITE)
            }
          }
        } else if (GlobalValues.sCardBackgroundMode == Const.CARD_BG_MODE_GRADIENT) {
          if (item.color == 0) {
            if (item.packageName.isNotEmpty()) {
              lifecycleCoroutineScope.launch {
                UxUtils.setCardUseIconColor(UxUtils.getAppIcon(context, item, 45.dp)) { color ->
                  item.color = color
                  if (shouldUpdateColorInfo(context, item)) {
                    AnywhereApplication.sRepository.update(item)
                  } else {
                    itemView.cardBackground.post {
                      UxUtils.createLinearGradientBitmap(context, itemView.cardBackground, color)
                      itemView.appName.setTextColor(if (UxUtils.isLightColor(color)) Color.BLACK else Color.WHITE)
                      normalView?.content?.description?.setTextColor(if (UxUtils.isLightColor(color)) Color.BLACK else Color.WHITE)
                    }
                  }
                }
              }
            }
            itemView.cardBackground.setImageDrawable(null)
          } else {
            itemView.cardBackground.post {
              UxUtils.createLinearGradientBitmap(context, itemView.cardBackground, item.color)
              itemView.appName.setTextColor(if (UxUtils.isLightColor(item.color)) Color.BLACK else Color.WHITE)
              normalView?.content?.description?.setTextColor(if (UxUtils.isLightColor(item.color)) Color.BLACK else Color.WHITE)
            }
          }
        } else {
          itemView.cardBackground.setImageDrawable(null)
        }
      }
      LAYOUT_MODE_MINIMUM -> {
        if (GlobalValues.sCardBackgroundMode == Const.CARD_BG_MODE_PURE) {
          if (item.color == 0) {
            if (item.packageName.isNotEmpty()) {
              lifecycleCoroutineScope.launch {
                UxUtils.setCardUseIconColor(UxUtils.getAppIcon(context, item, 32.dp)) { color ->
                  if (color != 0) {
                    itemView.rootView.backgroundTintList = ColorStateList.valueOf(color)
                    itemView.appName.setTextColor(if (UxUtils.isLightColor(color)) Color.BLACK else Color.WHITE)
                    item.color = color

                    if (shouldUpdateColorInfo(context, item)) {
                      AnywhereApplication.sRepository.update(item)
                    }
                  } else {
                    itemView.appName.setTextColor(
                      ContextCompat.getColor(
                        context,
                        R.color.textColorNormal
                      )
                    )
                  }
                }
              }
            }
          } else {
            itemView.rootView.backgroundTintList = ColorStateList.valueOf(item.color)
            itemView.appName.setTextColor(if (UxUtils.isLightColor(item.color)) Color.BLACK else Color.WHITE)
          }
        } else if (GlobalValues.sCardBackgroundMode == Const.CARD_BG_MODE_GRADIENT) {
          if (item.color == 0) {
            if (item.packageName.isNotEmpty()) {
              lifecycleCoroutineScope.launch {
                UxUtils.setCardUseIconColor(UxUtils.getAppIcon(context, item, 32.dp)) { color ->
                  item.color = color
                  if (shouldUpdateColorInfo(context, item)) {
                    AnywhereApplication.sRepository.update(item)
                  }

                  if (color == 0) {
                    itemView.cardBackground.setImageDrawable(null)
                  } else {
                    itemView.cardBackground.post {
                      UxUtils.createLinearGradientBitmap(
                        context,
                        itemView.cardBackground,
                        item.color
                      )
                      itemView.appName.setTextColor(if (UxUtils.isLightColor(item.color)) Color.BLACK else Color.WHITE)
                    }
                  }
                }
              }
            }
          } else {
            itemView.cardBackground.post {
              UxUtils.createLinearGradientBitmap(context, itemView.cardBackground, item.color)
              itemView.appName.setTextColor(if (UxUtils.isLightColor(item.color)) Color.BLACK else Color.WHITE)
            }
          }
        } else {
          itemView.cardBackground.setImageDrawable(null)
        }
      }
    }

    if (item.iconUri.isNullOrEmpty()) {
      Glide.with(context.applicationContext)
        .load(UxUtils.getAppIcon(context, item, 45.dp))
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .into(itemView.icon)
    } else {
      Glide.with(context.applicationContext)
        .load(item.iconUri)
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .into(itemView.icon)
    }

    itemView.badge.apply {
      if (GlobalValues.shortcutsList.contains(item.id)) {
        isVisible = true
        setImageResource(R.drawable.ic_add_shortcut)
        setColorFilter(ContextCompat.getColor(context, R.color.colorAccent), PorterDuff.Mode.SRC_IN)
      } else {
        isGone = true
      }
    }
    itemView.indicator.apply {
      if (item.type == AnywhereType.Card.SWITCH_SHELL) {
        isVisible = true
        if (item.param3 == SWITCH_OFF) {
          setImageResource(R.drawable.ic_red_dot)
        } else if (item.param3 == SWITCH_ON) {
          setImageResource(R.drawable.ic_green_dot)
        }
      } else {
        isGone = true
      }
    }

    val dataPosition = holder.layoutPosition - headerLayoutCount
    if (holder.itemView is MaterialCardView && !selectedIndex.contains(dataPosition)) {
      (holder.itemView as MaterialCardView).apply {
        scaleX = 1.0f
        scaleY = 1.0f
        isChecked = false
      }
    }
  }

  /** Shows the app icon for package groups and the generic group icon for tag / fallback groups. */
  private fun bindGroupIcon(view: ImageView, header: CardListItem.Header) {
    val packageName = header.packageName
    if (!packageName.isNullOrEmpty()) {
      val icon = if (groupIconCache.containsKey(packageName)) {
        groupIconCache[packageName]
      } else {
        UxUtils.getAppIcon(context, packageName).also { groupIconCache[packageName] = it }
      }
      if (icon != null) {
        view.imageTintList = null
        view.setImageDrawable(icon)
        return
      }
    }

    view.imageTintList = ColorStateList.valueOf(
      context.getColorByAttr(com.google.android.material.R.attr.colorPrimary)
    )
    view.setImageResource(R.drawable.ic_group)
  }

  override fun onMove(fromPosition: Int, toPosition: Int): Boolean {
    val from = fromPosition - headerLayoutCount
    val to = toPosition - headerLayoutCount

    if (from == to || from !in data.indices || to !in data.indices) {
      return false
    }
    if (data[from] !is CardListItem.Card || data[to] !is CardListItem.Card) {
      return false
    }
    // When grouped, cards may only be reordered inside their own group.
    if (GlobalValues.groupMode != Const.GROUP_MODE_NONE &&
      groupKeyAt(from) != groupKeyAt(to)
    ) {
      return false
    }

    data.add(to, data.removeAt(from))
    notifyItemMoved(fromPosition, toPosition)
    return true
  }

  /** Returns the key of the group header above [index], or null when not grouped. */
  private fun groupKeyAt(index: Int): String? {
    for (i in index downTo 0) {
      val item = data.getOrNull(i)
      if (item is CardListItem.Header) {
        return item.key
      }
    }
    return null
  }

  override fun onSwiped(position: Int) { /* Do nothing */
  }

  override fun getItemId(position: Int): Long {
    return when (val item = data.getOrNull(position - headerLayoutCount)) {
      is CardListItem.Card -> item.entity.id.toLongOrNull()
        ?: (item.entity.id.hashCode().toLong() and 0xFFFFFFFFL)
      is CardListItem.Header -> {
        // Card ids are millisecond timestamps (positive), so negative ids are safe for headers.
        -1_000_000_000_000L - (item.key.hashCode().toLong() and 0xFFFFFFFFL)
      }
      null -> RecyclerView.NO_ID
    }
  }

  private fun getCardEntity(position: Int): AnywhereEntity? {
    return (data.getOrNull(position) as? CardListItem.Card)?.entity
  }

  fun clickItem(v: View, position: Int) {
    try {
      val listItem = data.getOrNull(position)

      if (listItem is CardListItem.Header) {
        if (mode == ADAPTER_MODE_NORMAL) {
          onHeaderClick?.invoke(listItem.key)
        }
        return
      }

      val item = (listItem as? CardListItem.Card)?.entity ?: return

      if (mode == ADAPTER_MODE_NORMAL) {
        if (isAppFrozen(context, item)) {
          v.postDelayed({ notifyItemChanged(position) }, 500)
        }
        Opener.with(context)
          .load(item)
          .setOpenedListener(object : Opener.OnOpenListener {
            override fun onOpened() {
              if (GlobalValues.closeAfterLaunch) {
                if (context is BaseActivity<*>) {
                  (context as BaseActivity<*>).shouldFinishOnResume = true
                }
              }
            }
          })
          .open()
      } else if (mode == ADAPTER_MODE_SELECT) {
        if (selectedIndex.isNotEmpty() && selectedIndex.contains(position)) {
          (v as MaterialCardView).apply {
            scaleX = 1.0f
            scaleY = 1.0f
            isChecked = false
          }
          selectedIndex.remove(position)
        } else {
          (v as MaterialCardView).apply {
            scaleX = 0.9f
            scaleY = 0.9f
            isChecked = true
          }
          selectedIndex.add(position)
        }
      }
    } catch (e: IndexOutOfBoundsException) {
      e.printStackTrace()
    }
  }

  fun longClickItem(v: View, position: Int, isEditMode: Boolean = true): Boolean {
    try {
      val item = getCardEntity(position) ?: return false

      if (mode == ADAPTER_MODE_NORMAL) {

        val intent = Intent(context, EditorActivity::class.java).apply {
          putExtra(EXTRA_ENTITY, item)
          putExtra(EXTRA_EDIT_MODE, isEditMode)
          putExtra(EXTRA_FROM_WORKFLOW, item.type == AnywhereType.Card.WORKFLOW)
        }

        if (GlobalValues.editorEntryAnim) {
          val options = ActivityOptions.makeSceneTransitionAnimation(
            context as BaseActivity<*>,
            v,
            context.getString(R.string.trans_item_container)
          )

          context.startActivity(intent, options.toBundle())
        } else {
          context.startActivity(intent)
        }
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
      }

      return true
    } catch (e: IndexOutOfBoundsException) {
      e.printStackTrace()
      return false
    }
  }

  fun deleteSelect() {
    if (selectedIndex.size == 0) {
      return
    }
    val deleteList = mutableListOf<AnywhereEntity>()

    for (index in selectedIndex) {
      getCardEntity(index)?.let { deleteList.add(it) }
    }

    AnywhereApplication.sRepository.delete(deleteList)
    clearSelect()
  }

  fun moveSelect(pageTitle: String) {
    if (selectedIndex.size == 0) {
      return
    }
    val moveList = mutableListOf<AnywhereEntity>()

    for (index in selectedIndex) {
      getCardEntity(index)?.let { moveList.add(it) }
    }

    for (item in moveList) {
      item.category = pageTitle
      AnywhereApplication.sRepository.update(item)
    }
    clearSelect()
  }

  suspend fun createShortcutSelect() {
    if (selectedIndex.size == 0) {
      return
    }

    for (index in selectedIndex) {
      val entity = getCardEntity(index) ?: continue

      withContext(Dispatchers.Main) {
        if (atLeastO()) {
          ShortcutsUtils.addPinnedShortcut(
            entity,
            UxUtils.getAppIcon(Utils.getApp(), entity, 45.dp),
            entity.appName
          )
        } else {
          ShortcutsUtils.addHomeShortcutPreO(
            entity,
            UxUtils.getAppIcon(Utils.getApp(), entity, 45.dp),
            entity.appName
          )
        }
      }

      delay(200)
    }

    clearSelect()
  }

  fun clearSelect() {
    selectedIndex.clear()
  }

  fun updateSortedList() {
    val list = mutableListOf<AnywhereEntity>()
    val startTime = System.currentTimeMillis()
    var cardIndex = 0

    for (pos in data.indices) {
      val item = (data[pos] as? CardListItem.Card)?.entity ?: continue
      item.timeStamp = (startTime - cardIndex * 100).toString()
      list.add(item)
      cardIndex++
    }
    AnywhereApplication.sRepository.update(list)
  }

  private fun shouldUpdateColorInfo(context: Context, item: AnywhereEntity): Boolean {
    return context !is QRCodeCollectionActivity && (item.type == AnywhereType.Card.ACTIVITY || item.type == AnywhereType.Card.URL_SCHEME || item.type == AnywhereType.Card.QR_CODE)
  }
}
