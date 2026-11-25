package com.absinthe.anywhere_.adapter.card

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Spinner
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.model.*
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder

class ExtrasAdapter : BaseQuickAdapter<ExtraBean.ExtraItem, BaseViewHolder>(R.layout.item_extra) {

  init {
    addChildClickViewIds(R.id.ib_delete)
  }

  override fun convert(holder: BaseViewHolder, item: ExtraBean.ExtraItem) {
    holder.getView<Spinner>(R.id.spinner).apply {
      setSelection(TYPE_LIST.indexOf(item.type).coerceAtLeast(0))
      onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {

        }

        override fun onItemSelected(
          parent: AdapterView<*>?,
          view: View?,
          position: Int,
          id: Long
        ) {
          item.type = TYPE_LIST[position]
        }
      }
    }

    holder.getView<EditText>(R.id.et_key).bindText(item.key) { item.key = it }
    holder.getView<EditText>(R.id.et_value).bindText(item.value) { item.value = it }
  }

  /**
   * Replaces the text and its watcher, so a recycled row does not keep listening for the
   * previous item nor show its text.
   */
  private fun EditText.bindText(value: String, onChanged: (String) -> Unit) {
    (tag as? TextWatcher)?.let(::removeTextChangedListener)
    setText(value)
    setSelection(text.length)
    val watcher = object : TextWatcher {
      override fun afterTextChanged(text: Editable) {
        onChanged(text.toString())
      }

      override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
      override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
    }
    addTextChangedListener(watcher)
    tag = watcher
  }

  companion object {
    val TYPE_LIST = listOf(
      TYPE_STRING, TYPE_INT, TYPE_LONG, TYPE_BOOLEAN, TYPE_FLOAT, TYPE_DOUBLE, TYPE_URI
    )
  }
}
