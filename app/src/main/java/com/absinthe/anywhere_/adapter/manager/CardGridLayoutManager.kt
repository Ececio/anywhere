package com.absinthe.anywhere_.adapter.manager

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager

/**
 * Grid used by the home cards. Group headers take a full row, every card takes one cell.
 *
 * A staggered grid caches the span (column) of each adapter position, so inserting or removing a
 * card leaves stale spans behind and the following cards can be laid out in the wrong column after
 * scrolling. GridLayoutManager derives the column from the position and the span sizes on every
 * layout pass instead, which keeps every row aligned regardless of how the list changed.
 */
class CardGridLayoutManager(
  context: Context,
  spanCount: Int,
  private val isFullSpanItem: (Int) -> Boolean
) : GridLayoutManager(context, spanCount) {

  init {
    spanSizeLookup = object : SpanSizeLookup() {
      override fun getSpanSize(position: Int): Int {
        return if (isFullSpanItem(position)) spanCount else 1
      }
    }
  }

  /**
   * Predictive animations run a pre-layout with the previous item list. Disabling them makes every
   * insert, remove and move lay out in its final state, so the grid can never reuse a column from
   * the old list (the same reason the previous staggered manager turned them off).
   */
  override fun supportsPredictiveItemAnimations(): Boolean = false
}
