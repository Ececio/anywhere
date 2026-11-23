package com.absinthe.anywhere_.ui.main

import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.absinthe.anywhere_.AnywhereApplication
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.adapter.card.ADAPTER_MODE_NORMAL
import com.absinthe.anywhere_.adapter.card.ADAPTER_MODE_SELECT
import com.absinthe.anywhere_.adapter.card.ADAPTER_MODE_SORT
import com.absinthe.anywhere_.databinding.FragmentCategoryCardBinding
import com.absinthe.anywhere_.constants.Const
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.utils.manager.DialogManager
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

const val BUNDLE_CATEGORY = "CATEGORY"

class CategoryCardFragment : Fragment() {

  private val category by lazy { arguments?.getString(BUNDLE_CATEGORY) ?: GlobalValues.category }

  private lateinit var binding: FragmentCategoryCardBinding
  private lateinit var controller: CardGridController

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentCategoryCardBinding.inflate(inflater, container, false)
    setHasOptionsMenu(true)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    controller = CardGridController(
      context = requireContext(),
      lifecycleOwner = viewLifecycleOwner,
      lifecycleScope = viewLifecycleOwner.lifecycleScope,
      recyclerView = binding.recyclerView,
      category = category
    )
    controller.setup()
  }

  override fun onResume() {
    super.onResume()
    currentReference = WeakReference(this)

    if (GlobalValues.shortcutListChanged && ::controller.isInitialized) {
      controller.adapter.notifyDataSetChanged()
      GlobalValues.shortcutListChanged = false
    }
  }

  override fun onDestroyView() {
    if (::controller.isInitialized) {
      controller.unregisterObservers()
    }
    super.onDestroyView()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (::controller.isInitialized) {
      controller.updateLayoutManager(newConfig)
    }
  }

  override fun onPrepareOptionsMenu(menu: Menu) {
    val mode = if (::controller.isInitialized) {
      controller.adapter.mode
    } else {
      ADAPTER_MODE_NORMAL
    }

    menu.findItem(R.id.toolbar_settings).isVisible = mode == ADAPTER_MODE_NORMAL
    menu.findItem(R.id.toolbar_sort).isVisible = mode == ADAPTER_MODE_NORMAL
    menu.findItem(R.id.toolbar_done).isVisible = mode != ADAPTER_MODE_NORMAL
    menu.findItem(R.id.toolbar_delete).isVisible = mode == ADAPTER_MODE_SELECT
    menu.findItem(R.id.toolbar_move).isVisible = mode == ADAPTER_MODE_SELECT
    menu.findItem(R.id.toolbar_create_sc).isVisible =
      mode == ADAPTER_MODE_SELECT && com.absinthe.libraries.utils.utils.XiaomiUtilities.isMIUI()

    super.onPrepareOptionsMenu(menu)
  }

  fun sort() {
    if (!::controller.isInitialized) {
      return
    }
    controller.adapter.mode = ADAPTER_MODE_SORT
    controller.itemTouchHelper.attachToRecyclerView(controller.recyclerView)
    requireActivity().invalidateOptionsMenu()
    controller.recyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
  }

  fun multiSelect() {
    if (!::controller.isInitialized) {
      return
    }
    controller.adapter.mode = ADAPTER_MODE_SELECT
    requireActivity().invalidateOptionsMenu()
    controller.recyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
  }

  fun refreshSortMode() {
    AnywhereApplication.sRepository.refresh()
    if (::controller.isInitialized) {
      controller.observeEntitiesList()
    }
  }

  fun refreshGroupMode() {
    if (::controller.isInitialized) {
      controller.refreshGroupMode()
    }
  }

  fun editDone() {
    if (!::controller.isInitialized) {
      return
    }

    if (controller.adapter.mode == ADAPTER_MODE_SORT) {
      controller.adapter.mode = ADAPTER_MODE_NORMAL

      controller.itemTouchHelper.attachToRecyclerView(null)
      requireActivity().invalidateOptionsMenu()

      controller.adapter.updateSortedList()
      GlobalValues.sortMode = Const.SORT_MODE_TIME_DESC
      // Re-observe the time-ordered source so the new order is applied immediately.
      refreshSortMode()
    } else if (controller.adapter.mode == ADAPTER_MODE_SELECT) {
      resetSelectState()
      controller.adapter.clearSelect()
      controller.adapter.mode = ADAPTER_MODE_NORMAL
      requireActivity().invalidateOptionsMenu()
    }
    controller.recyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
  }

  fun deleteSelected() {
    if (!::controller.isInitialized) {
      return
    }
    DialogManager.showDeleteSelectCardDialog(requireContext()) {
      controller.adapter.deleteSelect()
      resetSelectState()
    }
  }

  fun moveSelected() {
    if (!::controller.isInitialized) {
      return
    }
    DialogManager.showPageListDialog(requireContext()) {
      controller.adapter.moveSelect(it)
    }
  }

  fun createShortcutSelected() {
    if (!::controller.isInitialized) {
      return
    }
    DialogManager.showMultiSelectCreatingShortcutDialog(requireContext()) {
      GlobalScope.launch(Dispatchers.IO) {
        controller.adapter.createShortcutSelect()

        withContext(Dispatchers.Main) {
          resetSelectState()
        }
      }
    }
  }

  /** Opens the dialog that allows reordering groups by dragging their handles. */
  fun showGroupSortDialog() {
    if (GlobalValues.groupMode == Const.GROUP_MODE_NONE || !::controller.isInitialized) {
      return
    }
    val entities = AnywhereApplication.sRepository.allAnywhereEntities.value ?: return
    val groups = controller.buildGroups(controller.filterEntities(entities))
    if (groups.size < 2) {
      return
    }

    GroupSortDialogFragment.newInstance(
      ArrayList(groups.map { it.key }),
      ArrayList(groups.map { it.title })
    ).show(parentFragmentManager, GroupSortDialogFragment.TAG)
  }

  private fun resetSelectState() {
    for (pos in 0 until controller.adapter.itemCount) {
      val view: View = controller.recyclerView.layoutManager?.findViewByPosition(pos) ?: continue
      if (view is MaterialCardView) {
        view.scaleX = 1.0f
        view.scaleY = 1.0f
        view.isChecked = false
      }
    }
  }

  companion object {
    fun newInstance(category: String): CategoryCardFragment {
      return CategoryCardFragment().apply {
        arguments = Bundle().apply {
          putString(BUNDLE_CATEGORY, category)
        }
      }
    }

    var currentReference: WeakReference<CategoryCardFragment>? = null
  }
}
