package com.absinthe.anywhere_.ui.preview

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.absinthe.anywhere_.utils.manager.ShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Enumerates every activity of a package and probes it with root:
 * launch -> screenshot -> uiautomator dump -> screen text -> perceptual hash.
 */
class ActivityPreviewRunner(private val context: Context) {

  private val packageManager: PackageManager = context.packageManager
  private val workDir: File = File(context.getExternalFilesDir(null), DIR_NAME)
  private val shotDir: File = File(workDir, "shots")
  private val thumbDir: File = File(workDir, "thumbs")

  init {
    shotDir.mkdirs()
    thumbDir.mkdirs()
  }

  fun enumerate(packageName: String): List<ActivityPreviewItem> {
    val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
    val info = try {
      packageManager.getPackageInfo(packageName, flags)
    } catch (e: PackageManager.NameNotFoundException) {
      Timber.w(e, "package not found: %s", packageName)
      return emptyList()
    }

    val appEnabled = info.applicationInfo?.enabled ?: true
    val result = LinkedHashMap<String, ActivityPreviewItem>()

    info.activities.orEmpty().forEach { activity ->
      // getPackageInfo may return duplicated aliases, keep the first declaration.
      if (result.containsKey(activity.name)) return@forEach

      val label = try {
        activity.loadLabel(packageManager)?.toString().orEmpty()
      } catch (e: Exception) {
        ""
      }

      result[activity.name] = ActivityPreviewItem(
        index = result.size,
        packageName = packageName,
        className = activity.name,
        label = label.ifEmpty { activity.name.substringAfterLast('.') },
        exported = activity.exported,
        enabled = appEnabled && activity.enabled
      )
    }

    return result.values
      .sortedBy { it.className.lowercase() }
      .mapIndexed { index, item -> item.copy(index = index + 1) }
  }

  /** Wake the device and make sure no system dialog blocks the next launch. */
  suspend fun prepare() = withContext(Dispatchers.IO) {
    ShellManager.exec("input keyevent KEYCODE_WAKEUP")
    ShellManager.exec("wm dismiss-keyguard")
    ShellManager.exec("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
    delay(300)
  }

  /** Press BACK only when the foreground window is not ours, so crash dialogs go away. */
  suspend fun dismissForeignScreen(actual: String? = null) = withContext(Dispatchers.IO) {
    val top = actual ?: topActivity() ?: return@withContext
    if (!top.startsWith(context.packageName + "/")) {
      ShellManager.exec("input keyevent KEYCODE_BACK")
      delay(250)
      ShellManager.exec("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
    }
  }

  suspend fun probe(item: ActivityPreviewItem): ActivityPreviewItem = withContext(Dispatchers.IO) {
    if (!item.enabled) {
      return@withContext item.copy(status = PreviewStatus.SKIPPED, note = "disabled component")
    }

    val shot = shotFile(item)
    val xml = xmlFile(item)
    shot.delete()
    xml.delete()

    val component = item.component
    var note = ""

    val startOut = runCatching {
      ShellManager.exec("am start -W -n $component")
    }.getOrElse {
      Timber.w(it, "am start failed: %s", component)
      it.message.orEmpty()
    }

    val launchError = startOut.lineSequence()
      .map { it.trim() }
      .firstOrNull {
        it.startsWith("Error") || it.contains("does not exist") ||
          it.contains("Permission Denial") || it.contains("unable to resolve")
      }
      .orEmpty()

    if (launchError.isNotEmpty()) {
      return@withContext item.copy(
        status = PreviewStatus.FAILED,
        note = launchError.take(200)
      )
    }

    // Wait until the target app is actually in front, otherwise a slow launch would
    // capture the previous screen (often our own list).
    var actual = awaitForeground(item.packageName, LAUNCH_TIMEOUT_MS)
    if (isForegroundOf(actual, item.packageName)) {
      delay(RENDER_DELAY_MS)
      actual = topActivity().orEmpty()
      if (!isForegroundOf(actual, item.packageName)) {
        actual = awaitForeground(item.packageName, LAUNCH_TIMEOUT_MS)
      }
    }
    var shotForeground = actual

    // Always capture: a crash dialog is drawn on top of the target and must be kept.
    captureScreen(shot, xml)

    actual = topActivity().orEmpty()
    if (isForegroundOf(actual, item.packageName) && !isForegroundOf(shotForeground, item.packageName)) {
      // The target only reached the foreground after the shot: retake so the card
      // does not show our own list.
      delay(RENDER_DELAY_MS)
      captureScreen(shot, xml)
      shotForeground = actual
      actual = topActivity().orEmpty()
    }

    var rawXml = runCatching { xml.readText() }.getOrElse { "" }
    var screenText = extractText(rawXml)

    // A MediaProjection / permission dialog can cover the target. Dismiss and retry once.
    if (isSystemDialog(actual)) {
      dismissForeignScreen()
      delay(700)
      captureScreen(shot, xml)
      actual = topActivity().orEmpty()
      shotForeground = actual
      rawXml = runCatching { xml.readText() }.getOrElse { "" }
      screenText = extractText(rawXml)
    }

    val crashed = isCrash(rawXml, screenText)
    val opened = isForegroundOf(shotForeground, item.packageName)

    val shotBitmap = decodeScaled(shot)
    val thumb = shotBitmap?.let { saveThumbnail(it, thumbFile(item)) }
    val hash = shotBitmap?.let { aHash(it) }
    shotBitmap?.recycle()

    if (shot.exists() && shot.length() <= 0L) shot.delete()

    val status = when {
      crashed -> {
        note = "crashed on launch"
        PreviewStatus.FAILED
      }
      !opened -> {
        val top = actual.substringAfter('/').substringAfterLast('.').ifEmpty { "unknown" }
        note = "did not open (foreground: $top)"
        shot.delete()
        thumb?.delete()
        screenText = ""
        PreviewStatus.FAILED
      }
      shot.exists() -> PreviewStatus.OK
      else -> {
        note = "no screenshot"
        PreviewStatus.FAILED
      }
    }

    item.copy(
      status = status,
      actual = actual,
      screenText = screenText,
      note = note,
      shotPath = shot.absolutePath.takeIf { shot.exists() },
      thumbPath = thumb?.absolutePath?.takeIf { thumb.exists() },
      hash = hash
    )
  }

  private fun isSystemDialog(actual: String): Boolean {
    val pkg = actual.substringBefore('/')
    return pkg == "com.android.systemui" ||
      pkg == "com.android.permissioncontroller" ||
      pkg == "com.google.android.permissioncontroller"
  }

  /** Polls the resumed activity until [pkg] is in front or the budget runs out. */
  private suspend fun awaitForeground(pkg: String, budgetMs: Long): String {
    var actual = topActivity().orEmpty()
    var waited = 0L
    while (!isForegroundOf(actual, pkg) && waited < budgetMs) {
      delay(POLL_INTERVAL_MS)
      waited += POLL_INTERVAL_MS
      actual = topActivity().orEmpty()
    }
    return actual
  }

  private fun isForegroundOf(actual: String, pkg: String): Boolean = actual.startsWith("$pkg/")

  private fun captureScreen(shot: File, xml: File) {
    runCatching {
      ShellManager.exec("screencap -p ${quote(shot.absolutePath)}; chmod 644 ${quote(shot.absolutePath)}")
    }.onFailure { Timber.w(it, "screencap failed: %s", shot.name) }

    runCatching {
      ShellManager.exec("uiautomator dump ${quote(xml.absolutePath)} >/dev/null 2>&1; chmod 644 ${quote(xml.absolutePath)}")
    }.onFailure { Timber.w(it, "uiautomator dump failed: %s", xml.name) }
  }

  fun cleanup() {
    runCatching { shotDir.listFiles()?.forEach { it.delete() } }
    runCatching { thumbDir.listFiles()?.forEach { it.delete() } }
    runCatching { workDir.listFiles { file -> file.extension == "xml" }?.forEach { it.delete() } }
  }

  fun shotFile(item: ActivityPreviewItem): File = File(shotDir, "%04d.png".format(item.index))

  fun thumbFile(item: ActivityPreviewItem): File = File(thumbDir, "%04d.jpg".format(item.index))

  fun xmlFile(item: ActivityPreviewItem): File = File(workDir, "%04d.xml".format(item.index))

  fun workDir(): File = workDir

  fun topActivity(): String? {
    val out = runCatching {
      ShellManager.exec("dumpsys activity activities | grep -m1 topResumedActivity")
    }.getOrElse { return null }
    return COMPONENT_REGEX.find(out)?.value
  }

  private fun extractText(xml: String): String {
    if (xml.isEmpty()) return ""
    val texts = LinkedHashSet<String>()
    TEXT_REGEX.findAll(xml).forEach { match ->
      match.groupValues[1]
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#10;", " ")
        .trim()
        .takeIf { it.isNotEmpty() && it.length < 200 }
        ?.let { texts.add(it) }
      if (texts.size >= 120) return@forEach
    }
    return texts.joinToString(" | ").take(4000)
  }

  private fun isCrash(rawXml: String, screenText: String): Boolean {
    val source = "$rawXml\n$screenText"
    return CRASH_MARKERS.any { source.contains(it, ignoreCase = true) }
  }

  private fun decodeScaled(file: File): Bitmap? {
    if (!file.exists() || file.length() <= 0L) return null
    return runCatching {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

      var sample = 1
      while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH && bounds.outHeight / (sample * 2) >= THUMB_WIDTH) {
        sample *= 2
      }
      val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      BitmapFactory.decodeFile(file.absolutePath, options)
    }.getOrNull()
  }

  private fun saveThumbnail(bitmap: Bitmap, target: File): File? {
    return runCatching {
      val width = bitmap.width.coerceAtLeast(1)
      val height = bitmap.height.coerceAtLeast(1)
      val scaledHeight = (height.toFloat() * THUMB_WIDTH / width).toInt().coerceAtLeast(1)
      val scaled = if (width > THUMB_WIDTH) {
        Bitmap.createScaledBitmap(bitmap, THUMB_WIDTH, scaledHeight, true)
      } else {
        bitmap
      }
      target.parentFile?.mkdirs()
      FileOutputStream(target).use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
      }
      if (scaled !== bitmap) scaled.recycle()
      target.takeIf { it.length() > 0L }
    }.getOrNull()
  }

  /**
   * 16x16 average hash over the top 85% of the frame, so status bars / gesture bars
   * do not make two identical screens look different.
   */
  private fun aHash(bitmap: Bitmap): LongArray {
    val cropHeight = (bitmap.height * 0.85f).toInt().coerceAtLeast(1)
    val cropped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, cropHeight)
    val scaled = Bitmap.createScaledBitmap(cropped, HASH_SIZE, HASH_SIZE, true)
    if (cropped !== scaled) cropped.recycle()

    val pixels = IntArray(HASH_SIZE * HASH_SIZE)
    scaled.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)
    scaled.recycle()

    var sum = 0L
    val gray = IntArray(pixels.size)
    pixels.forEachIndexed { index, color ->
      val value = ((color shr 16 and 0xFF) * 299 + (color shr 8 and 0xFF) * 587 + (color and 0xFF) * 114) / 1000
      gray[index] = value
      sum += value
    }
    val average = sum / pixels.size

    val hash = LongArray(HASH_LONGS)
    gray.forEachIndexed { index, value ->
      if (value >= average) {
        hash[index / 64] = hash[index / 64] or (1L shl (index % 64))
      }
    }
    return hash
  }

  private fun quote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

  companion object {
    const val DIR_NAME = "activity-preview"
    private const val THUMB_WIDTH = 360
    private const val HASH_SIZE = 16
    private const val HASH_LONGS = HASH_SIZE * HASH_SIZE / 64
    private const val MAX_HAMMING = 12
    private const val TEXT_PREFIX = 120
    private const val LAUNCH_TIMEOUT_MS = 5000L
    private const val RENDER_DELAY_MS = 600L
    private const val POLL_INTERVAL_MS = 250L

    private val COMPONENT_REGEX = Regex("[A-Za-z0-9_.]+/[^\\s}]+")
    private val TEXT_REGEX = Regex("text=\"([^\"]*)\"")
    private val CRASH_MARKERS = listOf(
      "--StackTrace--",
      "FATAL EXCEPTION",
      "Caused by:",
      "java.lang.",
      "android.content.ActivityNotFoundException",
      "keeps stopping",
      "has stopped"
    )

    fun isDuplicate(a: ActivityPreviewItem, b: ActivityPreviewItem): Boolean {
      if (a.status != PreviewStatus.OK || b.status != PreviewStatus.OK) return false
      if (a.duplicateOf != null || b.duplicateOf != null) return false

      val textA = a.screenText.take(TEXT_PREFIX)
      val textB = b.screenText.take(TEXT_PREFIX)
      val hashA = a.hash
      val hashB = b.hash
      val hashClose = hashA != null && hashB != null && hamming(hashA, hashB) <= MAX_HAMMING

      return when {
        textA.isNotEmpty() && textB.isNotEmpty() -> textA == textB && (hashA == null || hashB == null || hashClose)
        hashA != null && hashB != null -> hashClose
        else -> a.actual == b.actual && textA == textB
      }
    }

    private fun hamming(a: LongArray, b: LongArray): Int {
      var count = 0
      for (i in a.indices) {
        count += java.lang.Long.bitCount(a[i] xor b[i])
      }
      return count
    }
  }
}
