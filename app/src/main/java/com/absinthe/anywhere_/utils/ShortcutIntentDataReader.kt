package com.absinthe.anywhere_.utils

import android.os.Process
import timber.log.Timber

/**
 * Recovers the original intent data of a shortcut that `dumpsys shortcut` prints incompletely.
 *
 * The platform prints shortcut intents through [android.content.Intent.toString], which uses
 * [android.net.Uri.toSafeString]. Since Android 13 that only keeps `scheme://host/...` of the data;
 * before that it still decoded percent escapes, which corrupts URIs that carry an encoded URL as a
 * query parameter. The shortcut service stores every intent as `Intent.toUri(0)` in
 * `/data/system_ce/<user>/shortcut_service/packages/<package>.xml`, so the original data can be
 * read from there. The files are Android binary XML; the `abx2xml` system tool converts them to
 * text.
 *
 * Reading those files needs root (or a Shizuku server started as root); when that is not
 * available [load] simply returns an empty map and the caller keeps the dump value.
 */
object ShortcutIntentDataReader {

  private const val MARKER = "@@@"
  private const val PACKAGES_DIR = "shortcut_service/packages"

  private val SHORTCUT_ID_REGEX = Regex("""\bid="([^"]*)"""")
  private val INTENT_BASE_REGEX = Regex("""\bintent-base="([^"]*)"""")
  private val INTENT_LEGACY_REGEX = Regex("""\bintent="([^"]*)"""")
  private val INTENT_TAG_REGEX = Regex("""<intent\b[^>]*>""")

  /**
   * Whether [data] printed by the dump has to be replaced with the value stored by the shortcut
   * service.
   *
   * The dump decodes percent escapes, which corrupts data URIs that carry an encoded URL as a
   * query parameter, and since Android 13 it only prints `scheme://host/...`.
   */
  fun needsFullData(data: String?): Boolean = !data.isNullOrEmpty()

  /**
   * Builds a single shell command that prints the shortcut XML of [packages] for [userId],
   * each one preceded by a [MARKER] line holding its path.
   */
  fun buildCommand(packages: Collection<String>, userId: Int): String {
    val directory = "/data/system_ce/$userId/$PACKAGES_DIR"
    val files = packages
      .asSequence()
      .filter { it.isNotBlank() }
      .map { "$directory/$it.xml" }
      .sorted()
      .joinToString(" ")

    return "for f in $files; do if [ -f \"\$f\" ]; then echo \"$MARKER\$f\"; " +
      "abx2xml \"\$f\" - 2>/dev/null || cat \"\$f\"; echo; fi; done"
  }

  /**
   * Parses the output of [buildCommand] into `package name -> (shortcut id -> intent data)`.
   * Shortcuts without a data URI are left out so the caller keeps whatever the dump provided.
   */
  fun parse(output: String): Map<String, Map<String, String>> {
    val result = mutableMapOf<String, Map<String, String>>()
    var packageName: String? = null
    val xml = StringBuilder()

    fun flush() {
      val name = packageName ?: return
      result[name] = parsePackage(xml.toString())
      xml.setLength(0)
    }

    for (line in output.lineSequence()) {
      if (line.startsWith(MARKER)) {
        flush()
        packageName = line.removePrefix(MARKER).trim()
          .substringAfterLast('/')
          .removeSuffix(".xml")
          .takeIf { it.isNotEmpty() }
        continue
      }
      if (packageName != null) {
        xml.append(line).append('\n')
      }
    }
    flush()

    return result
  }

  /** Runs [buildCommand] through the configured root/Shizuku shell and parses the result. */
  fun load(
    packages: Collection<String>,
    userId: Int = Process.myUid() / 100000
  ): Map<String, Map<String, String>> {
    if (packages.isEmpty()) {
      return emptyMap()
    }

    return try {
      val output = CommandUtils.execAdbCmd(buildCommand(packages, userId))
      val parsed = parse(output)
      Timber.d("Recovered full intent data for %d package(s)", parsed.size)
      parsed
    } catch (t: Throwable) {
      Timber.w(t, "Failed to read shortcut XML")
      emptyMap()
    }
  }

  /**
   * Extracts the data URI from the `Intent.toUri(0)` representation stored in the shortcut XML.
   * The data is the base URI; the optional `#Intent;...;end` fragment carries action, component
   * and flags and is not needed here.
   */
  fun dataOfIntentBase(intentBase: String): String? {
    val marker = intentBase.lastIndexOf("#Intent;")
    return when {
      marker > 0 -> intentBase.substring(0, marker)
      marker == 0 -> null
      else -> intentBase
    }
  }

  private fun parsePackage(xml: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    if (!xml.contains("<shortcut")) {
      return result
    }

    var index = 0
    while (true) {
      val start = xml.indexOf("<shortcut", index)
      if (start < 0) {
        break
      }
      val openEnd = xml.indexOf('>', start)
      if (openEnd < 0) {
        break
      }
      val openTag = xml.substring(start, openEnd + 1)
      val id = SHORTCUT_ID_REGEX.find(openTag)?.groupValues?.getOrNull(1)
      val close = xml.indexOf("</shortcut>", openEnd)
      val blockEnd = if (close < 0) xml.length else close
      val intentTag = INTENT_TAG_REGEX.find(xml.substring(openEnd + 1, blockEnd))?.value
      val rawBase = intentTag?.let {
        (INTENT_BASE_REGEX.find(it) ?: INTENT_LEGACY_REGEX.find(it))?.groupValues?.getOrNull(1)
      }
      val data = rawBase?.let(::dataOfIntentBase)?.let(::unescapeXml)

      if (!id.isNullOrEmpty() && !data.isNullOrEmpty()) {
        result[unescapeXml(id)] = data
      }

      index = if (close < 0) xml.length else close + "</shortcut>".length
    }

    return result
  }

  private fun unescapeXml(value: String): String {
    if (!value.contains('&')) {
      return value
    }

    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
      val char = value[index]
      if (char == '&') {
        val end = value.indexOf(';', index + 1)
        if (end > index) {
          val entity = value.substring(index + 1, end)
          val decoded = when {
            entity == "amp" -> "&"
            entity == "lt" -> "<"
            entity == "gt" -> ">"
            entity == "quot" -> "\""
            entity == "apos" -> "'"
            entity.startsWith("#x") || entity.startsWith("#X") ->
              entity.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            entity.startsWith("#") ->
              entity.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> null
          }
          if (decoded != null) {
            out.append(decoded)
            index = end + 1
            continue
          }
        }
      }
      out.append(char)
      index++
    }

    return out.toString()
  }
}
