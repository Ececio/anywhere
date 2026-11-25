package com.absinthe.anywhere_.view.app

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * A RecyclerView that always measures its full content height.
 *
 * The editor's extras list lives inside a ScrollView. When the keyboard shrinks the available
 * height, a normal RecyclerView gets a bounded height, starts scrolling on its own and draws rows
 * outside its bounds over the fields above. Measuring with an unspecified height keeps every row
 * laid out so the outer ScrollView is the only scrolling container.
 */
class NonScrollRecyclerView : RecyclerView {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
    context,
    attrs,
    defStyle
  )

  override fun onMeasure(widthSpec: Int, heightSpec: Int) {
    val unspecifiedHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    super.onMeasure(widthSpec, unspecifiedHeight)
  }
}
