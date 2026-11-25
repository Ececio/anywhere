package com.absinthe.anywhere_.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Stores card icons chosen by the user or taken from a shortcut inside the app's private files.
 *
 * Cards reference the stored PNG through [com.absinthe.anywhere_.model.database.AnywhereEntity.iconUri].
 * Keeping a copy instead of the source URI means the icon still shows after a reboot, after the
 * document permission expires, or after the shortcut disappears.
 */
object CardIconStore {

  private const val DIR = "card_icons"
  private const val ICON_SIZE = 192

  /** Draws [drawable] into a square PNG and returns its `file://` URI. */
  fun saveDrawable(context: Context, key: String, drawable: Drawable?): String? {
    if (drawable == null) {
      return null
    }

    return runCatching {
      val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
      drawable.draw(canvas)
      saveBitmap(context, key, bitmap)
    }.onFailure {
      Timber.w(it, "Unable to save the icon %s", key)
    }.getOrNull()
  }

  /**
   * Copies the image behind [uri] into a square PNG and returns its `file://` URI.
   *
   * The image is decoded with a sample size first so a large photo cannot exhaust the heap.
   */
  fun copyFrom(context: Context, uri: Uri, key: String): String? {
    return runCatching {
      val bitmap = decodeSquare(context, uri, ICON_SIZE) ?: return null
      saveBitmap(context, key, bitmap)
    }.onFailure {
      Timber.w(it, "Unable to copy the icon %s", key)
    }.getOrNull()
  }

  private fun saveBitmap(context: Context, key: String, bitmap: Bitmap): String {
    val dir = File(context.filesDir, DIR).apply { mkdirs() }
    val file = File(dir, fileName(key))
    FileOutputStream(file).use { output ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    bitmap.recycle()
    return Uri.fromFile(file).toString()
  }

  private fun fileName(key: String): String {
    val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    return "${safe}_${Integer.toHexString(key.hashCode())}.png"
  }

  private fun decodeSquare(context: Context, uri: Uri, size: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
      BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      return null
    }

    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= size &&
      bounds.outHeight / (sampleSize * 2) >= size
    ) {
      sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val source = context.contentResolver.openInputStream(uri)?.use {
      BitmapFactory.decodeStream(it, null, options)
    } ?: return null

    val side = min(source.width, source.height)
    val x = (source.width - side) / 2
    val y = (source.height - side) / 2
    val cropped = Bitmap.createBitmap(source, x, y, side, side)
    if (cropped !== source) {
      source.recycle()
    }

    if (cropped.width == size) {
      return cropped
    }

    val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
    if (scaled !== cropped) {
      cropped.recycle()
    }
    return scaled
  }
}
