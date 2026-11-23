package com.absinthe.anywhere_.ui.main

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.absinthe.anywhere_.AnywhereApplication
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.adapter.ItemTouchCallBack
import com.absinthe.anywhere_.adapter.SpacesItemDecoration
import com.absinthe.anywhere_.adapter.card.ADAPTER_MODE_NORMAL
import com.absinthe.anywhere_.adapter.card.BaseCardAdapter
import com.absinthe.anywhere_.adapter.card.CardListItem
import com.absinthe.anywhere_.adapter.card.DiffListCallback
import com.absinthe.anywhere_.adapter.card.LAYOUT_MODE_LARGE
import com.absinthe.anywhere_.adapter.card.LAYOUT_MODE_MEDIUM
import com.absinthe.anywhere_.adapter.card.LAYOUT_MODE_MINIMUM
import com.absinthe.anywhere_.adapter.card.LAYOUT_MODE_SMALL
import com.absinthe.anywhere_.adapter.manager.WrapContentStaggeredGridLayoutManager
import com.absinthe.anywhere_.constants.AnywhereType
import com.absinthe.anywhere_.constants.Const
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.extension.addSystemBarPaddingAsync
import com.absinthe.anywhere_.model.database.AnywhereEntity
import com.absinthe.anywhere_.utils.AppUtils.updateWidget
import com.absinthe.libraries.utils.extensions.dp
import com.absinthe.libraries.utils.extensions.paddingEndCompat
import com.absinthe.libraries.utils.extensions.paddingStartCompat
import com.blankj.utilcode.util.Utils

/**
 * Owns the card grid shared by the full screen home and the lightweight popup home: adapter,
 * staggered layout manager, group headers, collapse state and the settings observers.
 *
 * [category] is only used when pages are enabled; pass null to show every card.
 */
class CardGridController(
  private val context: Context,
  private val lifecycleOwner: LifecycleOwner,
  private val lifecycleScope: LifecycleCoroutineScope,
  val recyclerView: RecyclerView,
  private val category: String? = null,
  private val applySystemBarPadding: Boolean = true
) {

  lateinit var adapter: BaseCardAdapter
    private set
  lateinit var itemTouchHelper: ItemTouchHelper
    private set

  private lateinit var decoration: SpacesItemDecoration
  private val collapsedGroups = mutableSetOf<String>()
  private var isFirstLoadItems = true

  private val listObserver = Observer<List<AnywhereEntity>> { list ->
    if (isFirstLoadItems) {
      isFirstLoadItems = false
      updateItems(list)
    } else {
      // Apply the new list on the next frame instead of delaying it: a delayed diff made the
      // grid re-layout twice, which moved off-screen cards and then snapped them back.
      recyclerView.post { updateItems(list) }
    }
  }
  private val cardObserver = Observer<Any> { refreshRecyclerView() }
  private val compactLayoutObserver = Observer<Any> { refreshRecyclerView() }
  private val groupOrderObserver = Observer<Any> {
    AnywhereApplication.sRepository.allAnywhereEntities.value?.let { updateItems(it) }
  }
  private val groupModeObserver = Observer<Any> { refreshGroupMode() }
  private val animationSpeedObserver = Observer<Any> {
    applyItemAnimatorDuration()
    adapter.notifyDataSetChanged()
  }

  fun setup() {
    setupRecyclerView()
    registerObservers()
  }

  private fun setupRecyclerView() {
    decoration = SpacesItemDecoration(
      context.resources.getDimension(R.dimen.cardview_item_margin).toInt()
    )

    when (GlobalValues.cardMode) {
      Const.PREF_CARD_MODE_LARGE -> {
        decoration = SpacesItemDecoration(
          context.resources.getDimension(R.dimen.cardview_margin_parent_horizontal).toInt() / 2
        )
        adapter = BaseCardAdapter(LAYOUT_MODE_LARGE, lifecycleScope)
      }
      Const.PREF_CARD_MODE_MEDIUM -> {
        adapter = BaseCardAdapter(LAYOUT_MODE_MEDIUM, lifecycleScope)
      }
      Const.PREF_CARD_MODE_SMALL -> {
        decoration = SpacesItemDecoration(GlobalValues.compactItemGapDp.dp)
        adapter = BaseCardAdapter(LAYOUT_MODE_SMALL, lifecycleScope)
      }
      Const.PREF_CARD_MODE_MINIMUM -> {
        decoration = SpacesItemDecoration(
          context.resources.getDimension(R.dimen.cardview_margin_parent_horizontal).toInt() / 2
        )
        adapter = BaseCardAdapter(LAYOUT_MODE_MINIMUM, lifecycleScope)
      }
      else -> {
        adapter = BaseCardAdapter(LAYOUT_MODE_MEDIUM, lifecycleScope)
      }
    }

    adapter.apply {
      setDiffCallback(DiffListCallback())
      setOnItemClickListener { _, view, i -> clickItem(view, i) }
      setOnItemLongClickListener { _, view, i -> longClickItem(view, i) }
      setHasStableIds(true)
      onHeaderClick = { key -> toggleGroup(key) }
      mode = ADAPTER_MODE_NORMAL
    }

    with(recyclerView) {
      adapter = this@CardGridController.adapter
      setRecyclerViewLayoutManager(context.resources.configuration)
      addItemDecoration(decoration)
      paddingStartCompat = decoration.space
      paddingEndCompat = decoration.space
      if (applySystemBarPadding) {
        addSystemBarPaddingAsync(addStatusBarPadding = false)
      }
      itemAnimator = DefaultItemAnimator()
    }
    applyItemAnimatorDuration()

    itemTouchHelper = ItemTouchHelper(ItemTouchCallBack().apply {
      setOnItemTouchListener(adapter)
    }).apply {
      attachToRecyclerView(null)
    }
  }

  private fun registerObservers() {
    GlobalValues.cardModeLiveData.observe(lifecycleOwner, cardObserver)
    GlobalValues.compactLayoutLiveData.observe(lifecycleOwner, compactLayoutObserver)
    GlobalValues.groupOrderLiveData.observe(lifecycleOwner, groupOrderObserver)
    GlobalValues.groupModeLiveData.observe(lifecycleOwner, groupModeObserver)
    GlobalValues.animationSpeedLiveData.observe(lifecycleOwner, animationSpeedObserver)
    observeEntitiesList()
  }

  fun unregisterObservers() {
    GlobalValues.cardModeLiveData.removeObserver(cardObserver)
    GlobalValues.compactLayoutLiveData.removeObserver(compactLayoutObserver)
    GlobalValues.groupOrderLiveData.removeObserver(groupOrderObserver)
    GlobalValues.groupModeLiveData.removeObserver(groupModeObserver)
    GlobalValues.animationSpeedLiveData.removeObserver(animationSpeedObserver)
    AnywhereApplication.sRepository.allAnywhereEntities.removeObserver(listObserver)
  }

  fun observeEntitiesList() {
    AnywhereApplication.sRepository.allAnywhereEntities.removeObserver(listObserver)
    AnywhereApplication.sRepository.allAnywhereEntities.observe(lifecycleOwner, listObserver)
  }

  fun refreshGroupMode() {
    // Group keys are mode-specific, so old collapsed states must not leak into the new mode.
    collapsedGroups.clear()
    AnywhereApplication.sRepository.allAnywhereEntities.value?.let { updateItems(it) }
  }

  private fun toggleGroup(key: String) {
    if (!collapsedGroups.add(key)) {
      collapsedGroups.remove(key)
    }
    AnywhereApplication.sRepository.allAnywhereEntities.value?.let { updateItems(it) }
  }

  fun updateLayoutManager(configuration: Configuration) {
    setRecyclerViewLayoutManager(configuration)
  }

  fun updateItems(list: List<AnywhereEntity>) {
    applyItems(list)
  }

  fun filterEntities(list: List<AnywhereEntity>): List<AnywhereEntity> {
    val currentCategory = category ?: return list
    if (!GlobalValues.isPages) {
      return list
    }
    return if (currentCategory == AnywhereType.Category.DEFAULT_CATEGORY) {
      list.filter { it.category.isNullOrEmpty() || it.category == currentCategory }
    } else {
      list.filter { it.category == currentCategory }
    }
  }

  private fun applyItems(list: List<AnywhereEntity>) {
    adapter.setDiffNewData(buildListItems(filterEntities(list)))
    // StaggeredGridLayoutManager caches the span (column) of every adapter position. DiffUtil
    // dispatches insert/move notifications, so the cached spans can point at the wrong items and
    // leave holes until the grid is rebuilt (e.g. a group's first row only filled on the right
    // after adding cards). Clearing the cache makes the next layout assign spans from scratch.
    (recyclerView.layoutManager as? StaggeredGridLayoutManager)?.invalidateSpanAssignments()
    updateWidget(Utils.getApp())
  }

  data class CardGroup(
    val key: String,
    val title: String,
    val isFallback: Boolean,
    val packageName: String? = null,
    val items: MutableList<AnywhereEntity> = mutableListOf()
  )

  /**
   * Converts entities into adapter items, inserting group headers when grouping is enabled.
   * The order of entities inside every group is preserved, so sorting still works within groups.
   */
  fun buildListItems(entities: List<AnywhereEntity>): MutableList<CardListItem> {
    val result = mutableListOf<CardListItem>()

    if (GlobalValues.groupMode == Const.GROUP_MODE_NONE) {
      entities.forEach { result.add(CardListItem.Card(it)) }
      return result
    }

    for (group in buildGroups(entities)) {
      val collapsed = collapsedGroups.contains(group.key)
      result.add(CardListItem.Header(group.key, group.title, collapsed, group.packageName))
      if (!collapsed) {
        group.items.forEach { result.add(CardListItem.Card(it)) }
      }
    }

    return result
  }

  /**
   * Groups entities and applies the user-defined group order. Groups that are not in the saved
   * order yet are appended alphabetically; the fallback group stays at the end.
   */
  fun buildGroups(entities: List<AnywhereEntity>): List<CardGroup> {
    val groups = LinkedHashMap<String, CardGroup>()
    val packageTitleCache = mutableMapOf<String, String>()

    for (entity in entities) {
      val key: String
      val title: String
      var groupPackage: String? = null

      when (GlobalValues.groupMode) {
        Const.GROUP_MODE_PACKAGE -> {
          val packageName = entity.packageName
          if (packageName.isEmpty()) {
            key = ""
            title = context.getString(R.string.group_others)
          } else {
            key = packageName
            groupPackage = packageName
            title = packageTitleCache.getOrPut(packageName) {
              com.blankj.utilcode.util.AppUtils.getAppName(packageName)
                .ifBlank { packageName }
            }
          }
        }
        Const.GROUP_MODE_TAG -> {
          val tag = entity.tag.orEmpty().trim()
          if (tag.isEmpty()) {
            key = ""
            title = context.getString(R.string.group_untagged)
          } else {
            key = tag
            title = tag
          }
        }
        else -> continue
      }

      groups.getOrPut(key) {
        CardGroup(key, title, key.isEmpty(), groupPackage)
      }.items.add(entity)
    }

    val order = GlobalValues.groupOrder
      .withIndex()
      .associate { it.value to it.index }

    return groups.values.sortedWith(
      compareBy<CardGroup> { order[it.key] ?: Int.MAX_VALUE }
        .thenBy { it.isFallback }
        .thenBy { it.title.lowercase() }
    )
  }

  fun applyItemAnimatorDuration() {
    val duration = GlobalValues.itemAnimDuration
    (recyclerView.itemAnimator as? SimpleItemAnimator)?.apply {
      addDuration = duration
      removeDuration = duration
      moveDuration = duration
      changeDuration = duration
    }
  }

  fun refreshRecyclerView() {
    recyclerView.removeItemDecoration(decoration)
    setupRecyclerView()
    AnywhereApplication.sRepository.allAnywhereEntities.value?.let { list ->
      applyItems(list)
    }
  }

  private fun setRecyclerViewLayoutManager(configuration: Configuration) {
    recyclerView.layoutManager =
      if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        val spanCount = when (GlobalValues.cardMode) {
          Const.PREF_CARD_MODE_LARGE -> 2
          Const.PREF_CARD_MODE_MEDIUM -> 4
          Const.PREF_CARD_MODE_SMALL -> GlobalValues.compactColumns
          Const.PREF_CARD_MODE_MINIMUM -> 8
          else -> 4
        }
        WrapContentStaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
      } else {
        val spanCount = when (GlobalValues.cardMode) {
          Const.PREF_CARD_MODE_LARGE -> 1
          Const.PREF_CARD_MODE_MEDIUM -> 2
          Const.PREF_CARD_MODE_SMALL -> GlobalValues.compactColumns
          Const.PREF_CARD_MODE_MINIMUM -> 4
          else -> 2
        }
        WrapContentStaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
      }
  }
}
