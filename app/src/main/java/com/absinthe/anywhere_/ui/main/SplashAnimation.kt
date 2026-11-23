package com.absinthe.anywhere_.ui.main

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.absinthe.anywhere_.constants.GlobalValues

/**
 * Plays the logo animation on top of a home screen when the "Launch animation" setting is enabled.
 *
 * The system splash screen is kept static (animation duration is 0 in the theme) so the animation
 * can be toggled at runtime on every supported Android version. The caller is responsible for
 * removing the returned runnable from the overlay in onDestroy.
 */
object SplashAnimation {

  private const val HOLD_DURATION = 500L
  private const val FADE_DURATION = 150L

  fun play(
    activity: Activity,
    savedInstanceState: Bundle?,
    overlay: View,
    icon: ImageView
  ): Runnable? {
    if (savedInstanceState != null || activity.isFinishing || !GlobalValues.splashAnimation) {
      return null
    }
    val action = activity.intent?.action
    if (action != null && action != Intent.ACTION_MAIN) {
      return null
    }

    overlay.alpha = 1f
    overlay.visibility = View.VISIBLE

    val dismiss = Runnable {
      overlay.animate()
        .alpha(0f)
        .setDuration(FADE_DURATION)
        .withEndAction {
          overlay.visibility = View.GONE
          overlay.alpha = 1f
        }
        .start()
    }

    overlay.post {
      if (activity.isFinishing || activity.isDestroyed) {
        return@post
      }
      (icon.drawable as? Animatable)?.start()
      overlay.postDelayed(dismiss, HOLD_DURATION)
    }

    return dismiss
  }
}
