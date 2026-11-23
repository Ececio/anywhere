package com.absinthe.anywhere_.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.databinding.DialogGroupSortBinding
import com.absinthe.libraries.utils.extensions.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.min

/**
 * Lets the user reorder home page groups by dragging the handle next to each group name.
 * The new order is saved immediately and applied to the home page behind the dialog.
 */
class GroupSortDialogFragment : DialogFragment() {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val binding = DialogGroupSortBinding.inflate(layoutInflater)
    val keys = arguments?.getStringArrayList(ARG_KEYS)?.toMutableList() ?: mutableListOf()
    val titles = arguments?.getStringArrayList(ARG_TITLES)?.toMutableList() ?: mutableListOf()

    val adapter = GroupSortAdapter(keys, titles)
    binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    binding.recyclerView.adapter = adapter

    val rowHeight = 52.dp
    val maxHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
    binding.recyclerView.layoutParams = LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      min(keys.size * rowHeight + 4.dp, maxHeight)
    )

    ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
      ItemTouchHelper.UP or ItemTouchHelper.DOWN,
      0
    ) {
      override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
      ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || from == to) {
          return false
        }
        adapter.move(from, to)
        return true
      }

      override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    }).attachToRecyclerView(binding.recyclerView)

    return MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.group_sort_title)
      .setView(binding.root)
      .setPositiveButton(R.string.group_sort_done, null)
      .create()
  }

  private class GroupSortAdapter(
    private val keys: MutableList<String>,
    private val titles: MutableList<String>
  ) : RecyclerView.Adapter<GroupSortAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
      val title: TextView = view.findViewById(R.id.tv_group_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      return Holder(
        LayoutInflater.from(parent.context)
          .inflate(R.layout.item_group_sort, parent, false)
      )
    }

    override fun getItemCount(): Int = keys.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
      holder.title.text = titles[position]
    }

    fun move(from: Int, to: Int) {
      keys.add(to, keys.removeAt(from))
      titles.add(to, titles.removeAt(from))
      notifyItemMoved(from, to)
      GlobalValues.setGroupOrder(keys)
    }
  }

  companion object {
    const val TAG = "GroupSortDialog"

    private const val ARG_KEYS = "keys"
    private const val ARG_TITLES = "titles"

    fun newInstance(keys: ArrayList<String>, titles: ArrayList<String>): GroupSortDialogFragment {
      return GroupSortDialogFragment().apply {
        arguments = Bundle().apply {
          putStringArrayList(ARG_KEYS, keys)
          putStringArrayList(ARG_TITLES, titles)
        }
      }
    }
  }
}
