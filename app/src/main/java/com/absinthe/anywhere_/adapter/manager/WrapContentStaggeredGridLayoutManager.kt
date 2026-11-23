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
