package com.absinthe.anywhere_.utils

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.SearchView

/**
 * Drops the focus of the search field and hides the keyboard when [event] starts outside it.
 *
 * The search view lives in the toolbar, so tapping the list (or any other part of the screen)
 * would otherwise leave it focused and keep the keyboard on screen.
 */
fun SearchView.clearFocusOnOutsideTouch(context: Context, event: MotionEvent) {
  if (event.actionMasked != MotionEvent.ACTION_DOWN || !isShown || !hasFocus()) {
    return
  }

  val bounds = Rect()
  if (!getGlobalVisibleRect(bounds) || bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
    return
  }

  clearFocus()
  val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  inputManager.hideSoftInputFromWindow(windowToken, 0)
}
