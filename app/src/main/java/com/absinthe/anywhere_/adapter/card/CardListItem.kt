package com.absinthe.anywhere_.adapter.card

import com.absinthe.anywhere_.model.database.AnywhereEntity

/**
 * An item in the home page card list.
 *
 * It is either a [Header] used to divide cards into groups, or a [Card] wrapping an entity.
 */
sealed class CardListItem {

  data class Card(val entity: AnywhereEntity) : CardListItem()

  data class Header(
    val key: String,
    val title: String,
    val collapsed: Boolean = false,
    /** Package whose app icon is shown before the title; null uses the generic group icon. */
    val packageName: String? = null
  ) : CardListItem()
}
