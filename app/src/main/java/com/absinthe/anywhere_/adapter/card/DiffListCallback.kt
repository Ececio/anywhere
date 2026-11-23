package com.absinthe.anywhere_.adapter.card

import androidx.recyclerview.widget.DiffUtil

class DiffListCallback : DiffUtil.ItemCallback<CardListItem>() {

  override fun areItemsTheSame(oldItem: CardListItem, newItem: CardListItem): Boolean {
    return when {
      oldItem is CardListItem.Header && newItem is CardListItem.Header -> {
        oldItem.key == newItem.key
      }
      oldItem is CardListItem.Card && newItem is CardListItem.Card -> {
        oldItem.entity.id == newItem.entity.id
      }
      else -> false
    }
  }

  override fun areContentsTheSame(oldItem: CardListItem, newItem: CardListItem): Boolean {
    return when {
      oldItem is CardListItem.Header && newItem is CardListItem.Header -> {
        oldItem.title == newItem.title && oldItem.collapsed == newItem.collapsed
      }
      oldItem is CardListItem.Card && newItem is CardListItem.Card -> {
        val oldEntity = oldItem.entity
        val newEntity = newItem.entity
        oldEntity.appName == newEntity.appName &&
          oldEntity.param1 == newEntity.param1 &&
          oldEntity.param2 == newEntity.param2 &&
          oldEntity.param3 == newEntity.param3 &&
          oldEntity.description == newEntity.description &&
          oldEntity.type == newEntity.type &&
          oldEntity.color == newEntity.color &&
          oldEntity.iconUri == newEntity.iconUri &&
          oldEntity.tag == newEntity.tag
      }
      else -> false
    }
  }
}
