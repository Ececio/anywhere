package com.absinthe.anywhere_.adapter.manager

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import timber.log.Timber

class WrapContentStaggeredGridLayoutManager(spanCount: Int, orientation: Int) :
  StaggeredGridLayoutManager(spanCount, orientation) {

  init {
    // Relocating items between spans to close a gap makes cards that are outside the viewport
    // jump to a new position and then move back once the next layout pass runs.
    gapStrategy = GAP_HANDLING_NONE
  }

  /**
   * Predictive animations run a pre-layout with the previous item count. With a staggered grid the
   * spans cached during that pass are then reused for the shifted items, so after inserting cards
   * a row can be filled from the wrong column and leave holes (e.g. one new card on the far right
   * above its own group header). Disabling them makes every change lay out in its final state.
   */
  override fun supportsPredictiveItemAnimations(): Boolean = false

  override fun onLayoutChildren(recycler: Recycler, state: RecyclerView.State) {
    try {
      super.onLayoutChildren(recycler, state)
    } catch (e: IndexOutOfBoundsException) {
      Timber.e("encounter an IOOBE in RecyclerView")
    }
  }

  override fun onItemsChanged(recyclerView: RecyclerView) {
    try {
      super.onItemsChanged(recyclerView)
    } catch (e: IndexOutOfBoundsException) {
      Timber.e("encounter an IOOBE in RecyclerView")
    }
  }
}
