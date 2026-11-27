package com.absinthe.anywhere_.ui.preview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.absinthe.anywhere_.BaseActivity
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.databinding.ActivityActivityPreviewImageBinding
import com.absinthe.anywhere_.ui.editor.ACTION_EDITOR
import com.absinthe.anywhere_.ui.editor.EditorActivity
import com.absinthe.anywhere_.ui.editor.EXTRA_CLASS_NAME
import com.absinthe.anywhere_.ui.editor.EXTRA_PACKAGE_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Full screen viewer for captured screenshots.
 *
 * Receives parallel arrays built from the currently visible list items so that swiping only walks
 * through entries that actually have a screenshot.
 */
class ActivityPreviewImageActivity : BaseActivity<ActivityActivityPreviewImageBinding>() {

  private var paths: ArrayList<String> = arrayListOf()
  private var classNames: ArrayList<String> = arrayListOf()
  private var indexes: IntArray = IntArray(0)
  private var targetPackage: String = ""

  override fun setViewBinding() = ActivityActivityPreviewImageBinding.inflate(layoutInflater)

  override fun initView() {
    paths = intent.getStringArrayListExtra(EXTRA_SHOT_PATHS) ?: arrayListOf()
    classNames = intent.getStringArrayListExtra(EXTRA_CLASS_NAMES) ?: arrayListOf()
    indexes = intent.getIntArrayExtra(EXTRA_INDEXES) ?: IntArray(0)
    targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()

    if (paths.isEmpty()) {
      finish()
      return
    }

    window.setBackgroundDrawableResource(android.R.color.black)
    enterImmersiveMode()

    val imageAdapter = ImageAdapter(paths, lifecycleScope)
    binding.viewPager.adapter = imageAdapter
    binding.viewPager.offscreenPageLimit = 1

    binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        updateChrome(position)
      }
    })

    binding.btnExit.setOnClickListener { finish() }
    binding.btnPrev.setOnClickListener {
      val target = binding.viewPager.currentItem - 1
      if (target >= 0) binding.viewPager.setCurrentItem(target, true)
    }
    binding.btnNext.setOnClickListener {
      val target = binding.viewPager.currentItem + 1
      if (target < paths.size) binding.viewPager.setCurrentItem(target, true)
    }
    binding.btnEdit.setOnClickListener { openEditor(binding.viewPager.currentItem) }

    val start = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, paths.size - 1)
    if (start > 0) binding.viewPager.setCurrentItem(start, false)
    updateChrome(start)
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) enterImmersiveMode()
  }

  private fun enterImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.BLACK
    window.navigationBarColor = Color.BLACK
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  private fun updateChrome(position: Int) {
    if (position !in paths.indices) return
    val index = indexes.getOrElse(position) { position + 1 }
    val name = classNames.getOrNull(position).orEmpty().substringAfterLast('.')
    binding.tvTitle.text = getString(R.string.preview_image_title, index, name)
    binding.tvCounter.text = getString(R.string.preview_image_counter, position + 1, paths.size)
    binding.btnPrev.isEnabled = position > 0
    binding.btnNext.isEnabled = position < paths.size - 1
    binding.btnEdit.isEnabled =
      targetPackage.isNotEmpty() && !classNames.getOrNull(position).isNullOrEmpty()
  }

  private fun openEditor(position: Int) {
    val className = classNames.getOrNull(position)?.takeIf { it.isNotBlank() } ?: return
    if (targetPackage.isBlank()) return
    startActivity(
      Intent(this, EditorActivity::class.java).apply {
        action = ACTION_EDITOR
        putExtra(EXTRA_PACKAGE_NAME, targetPackage)
        putExtra(EXTRA_CLASS_NAME, className)
      }
    )
  }

  private class ImageAdapter(
    private val paths: List<String>,
    private val scope: CoroutineScope
  ) : RecyclerView.Adapter<ImageAdapter.Holder>() {

    class Holder(val image: ZoomImageView) : RecyclerView.ViewHolder(image)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val image = ZoomImageView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.BLACK)
      }
      return Holder(image)
    }

    override fun getItemCount(): Int = paths.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
      val path = paths[position]
      holder.image.tag = path
      holder.image.setImageDrawable(null)
      scope.launch {
        val bitmap = withContext(Dispatchers.IO) { decodeScreenshot(path) } ?: return@launch
        if (holder.image.tag == path) {
          holder.image.setImageBitmap(bitmap)
        }
      }
    }

    override fun onViewRecycled(holder: Holder) {
      holder.image.tag = null
      holder.image.setImageDrawable(null)
      super.onViewRecycled(holder)
    }
  }

  companion object {
    const val EXTRA_SHOT_PATHS = "EXTRA_SHOT_PATHS"
    const val EXTRA_CLASS_NAMES = "EXTRA_CLASS_NAMES"
    const val EXTRA_INDEXES = "EXTRA_INDEXES"
    const val EXTRA_START = "EXTRA_START"
    const val EXTRA_TARGET_PACKAGE = "EXTRA_TARGET_PACKAGE"

    private const val MAX_DECODE_SIZE = 2048

    private fun decodeScreenshot(path: String): Bitmap? {
      if (!File(path).exists()) return null
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(path, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

      var sample = 1
      val largest = max(bounds.outWidth, bounds.outHeight)
      while (largest / sample > MAX_DECODE_SIZE) sample *= 2

      val options = BitmapFactory.Options().apply { inSampleSize = sample }
      return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }
  }
}
