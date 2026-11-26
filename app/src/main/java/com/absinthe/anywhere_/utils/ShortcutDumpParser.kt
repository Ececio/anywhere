package com.absinthe.anywhere_.utils

/**
 * Parses the output of `dumpsys shortcut`.
 *
 * ShortcutManager and LauncherApps only expose the calling package's shortcuts unless the app owns
 * the launcher role, so a shell dump is the only way to read other apps' shortcuts. The dump uses
 * the platform `ShortcutInfo`/`Intent` toString() format; anything this parser cannot understand is
 * dropped instead of producing a broken intent.
 */
object ShortcutDumpParser {

  enum class Kind { STATIC, DYNAMIC }

  data class Shortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val kind: Kind,
    val rank: Int,
    val action: String?,
    val data: String?,
    val mimeType: String?,
    val flags: Int,
    val targetPackage: String?,
    val component: String?,
    val categories: List<String>,
    val extras: Map<String, String>,
    val iconResId: Int,
    val userId: Int
  )

  private const val HEADER_PREFIX = "ShortcutInfo {"
  private const val USER_PREFIX = "User: "
  private const val ENABLED_REASON = "[Not disabled]"

  private val FLAG_NAMES_REGEX = Regex("""\[([^\]]*)]""")
  private val ACTION_REGEX = Regex("""\bact=(\S+)""")
  private val DATA_REGEX = Regex("""\bdat=(\S+)""")
  private val TYPE_REGEX = Regex("""\btyp=(\S+)""")
  private val FLAGS_REGEX = Regex("""\bflg=0x([0-9a-fA-F]+)""")
  private val TARGET_PACKAGE_REGEX = Regex("""\bpkg=(\S+)""")
  private val COMPONENT_REGEX = Regex("""\bcmp=(\S+)""")
  private val CATEGORIES_REGEX = Regex("""\bcat=\[([^\]]*)]""")

  fun parse(dump: String): List<Shortcut> {
    val shortcuts = mutableListOf<Shortcut>()
    var builder: Builder? = null
    var userId = 0

    for (rawLine in dump.lineSequence()) {
      val line = rawLine.trim()

      if (line.startsWith(USER_PREFIX)) {
        builder?.let { build(it) }?.let(shortcuts::add)
        builder = null
        userId = line.removePrefix(USER_PREFIX).substringBefore(' ').trim().toIntOrNull() ?: userId
        continue
      }

      if (line.startsWith(HEADER_PREFIX)) {
        builder?.let { build(it) }?.let(shortcuts::add)
        builder = parseHeader(line, userId)
        continue
      }

      val current = builder ?: continue

      when {
        line.startsWith("packageName=") -> current.packageName = line.substringAfter("packageName=")
        line.startsWith("shortLabel=") -> current.shortLabel = labelOf(line)
        line.startsWith("longLabel=") -> current.longLabel = labelOf(line)
        line.startsWith("rank=") -> current.rank = numberBeforeComma(line)
        line.startsWith("disabledReason=") -> current.enabled = line.substringAfter("disabledReason=") == ENABLED_REASON
        line.startsWith("intents=[") -> current.intent = parseIntent(line)
        line.startsWith("iconRes=") -> current.iconResId = line.substringAfter("iconRes=").substringBefore("[").trim().toIntOrNull() ?: 0
      }
    }

    builder?.let { build(it) }?.let(shortcuts::add)
    return shortcuts
  }

  private fun parseHeader(line: String, userId: Int): Builder? {
    val body = line.removePrefix(HEADER_PREFIX)
    val id = body.substringAfter("id=", "").substringBefore(", flags=").trim()

    if (id.isEmpty()) {
      return null
    }

    val flagNames = FLAG_NAMES_REGEX.find(body)?.groupValues?.getOrNull(1).orEmpty()
    val kind = when {
      flagNames.contains("Dyn") -> Kind.DYNAMIC
      flagNames.contains("Man") -> Kind.STATIC
      else -> return null
    }

    return Builder(id, kind, userId)
  }

  private fun build(builder: Builder): Shortcut? {
    if (!builder.enabled || builder.packageName.isBlank()) {
      return null
    }

    val intent = builder.intent ?: return null
    if (intent.action.isNullOrBlank() && intent.data.isNullOrBlank() && intent.component.isNullOrBlank()) {
      return null
    }

    val label = listOfNotNull(builder.shortLabel, builder.longLabel)
      .firstOrNull { it.isNotBlank() }
      ?: builder.id

    return Shortcut(
      id = builder.id,
      packageName = builder.packageName,
      label = label,
      kind = builder.kind,
      rank = builder.rank,
      action = intent.action,
      data = intent.data,
      mimeType = intent.mimeType,
      flags = intent.flags,
      targetPackage = intent.targetPackage,
      component = intent.component,
      categories = intent.categories,
      extras = intent.extras,
      iconResId = builder.iconResId,
      userId = builder.userId
    )
  }

  private fun labelOf(line: String): String {
    val value = line.substringAfter("=").substringBefore(", resId=").trim()
    return if (value == "null") "" else value
  }

  private fun numberBeforeComma(line: String): Int {
    val value = line.substringAfter("=").substringBefore(",").trim()
    return value.toIntOrNull() ?: 0
  }

  private fun parseIntent(line: String): ParsedIntent? {
    val start = line.indexOf("Intent {")
    if (start < 0) {
      return null
    }
    val bodyStart = start + "Intent {".length
    val bodyEnd = line.indexOf(" }", bodyStart)
    if (bodyEnd < 0) {
      return null
    }
    val body = line.substring(bodyStart, bodyEnd)
    val extras = line.indexOf("}/", bodyEnd)
      .takeIf { it >= 0 }
      ?.let { parseExtras(line.substring(it + 2)) }
      .orEmpty()

    return ParsedIntent(
      action = ACTION_REGEX.find(body)?.groupValues?.getOrNull(1),
      data = DATA_REGEX.find(body)?.groupValues?.getOrNull(1),
      mimeType = TYPE_REGEX.find(body)?.groupValues?.getOrNull(1),
      flags = FLAGS_REGEX.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull(16)?.toInt() ?: 0,
      targetPackage = TARGET_PACKAGE_REGEX.find(body)?.groupValues?.getOrNull(1),
      component = COMPONENT_REGEX.find(body)?.groupValues?.getOrNull(1),
      categories = CATEGORIES_REGEX.find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty(),
      extras = extras
    )
  }

  /** Reads `PersistableBundle[{key=value, ...}]` printed after the intent. */
  private fun parseExtras(raw: String): Map<String, String> {
    val start = raw.indexOf("[{")
    val end = raw.lastIndexOf("}]")
    if (start < 0 || end <= start) {
      return emptyMap()
    }

    return splitTopLevel(raw.substring(start + 2, end))
      .mapNotNull { entry ->
        val separator = entry.indexOf('=')
        if (separator <= 0) {
          null
        } else {
          entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
        }
      }
      .toMap()
  }

  private fun splitTopLevel(value: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0

    for (char in value) {
      when (char) {
        '[', '{' -> {
          depth++
          current.append(char)
        }
        ']', '}' -> {
          depth--
          current.append(char)
        }
        ',' -> if (depth == 0) {
          parts += current.toString()
          current.clear()
        } else {
          current.append(char)
        }
        else -> current.append(char)
      }
    }

    if (current.isNotEmpty()) {
      parts += current.toString()
    }
    return parts.map { it.trim() }.filter { it.isNotEmpty() }
  }

  private class Builder(
    val id: String,
    val kind: Kind,
    val userId: Int
  ) {
    var packageName: String = ""
    var shortLabel: String = ""
    var longLabel: String = ""
    var rank: Int = 0
    var enabled: Boolean = true
    var intent: ParsedIntent? = null
    var iconResId: Int = 0
  }

  private class ParsedIntent(
    val action: String?,
    val data: String?,
    val mimeType: String?,
    val flags: Int,
    val targetPackage: String?,
    val component: String?,
    val categories: List<String>,
    val extras: Map<String, String>
  )
}
