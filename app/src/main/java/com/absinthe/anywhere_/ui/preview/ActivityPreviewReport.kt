package com.absinthe.anywhere_.ui.preview

import android.content.Context
import android.util.Base64
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a self-contained HTML report. Thumbnails are embedded as base64 so the
 * file can be opened from a content:// uri without breaking relative paths.
 */
object ActivityPreviewReport {

  fun write(
    context: Context,
    appName: String,
    packageName: String,
    items: List<ActivityPreviewItem>
  ): File {
    val total = items.size
    val ok = items.count { it.status == PreviewStatus.OK }
    val failed = items.count { it.status == PreviewStatus.FAILED }
    val skipped = items.count { it.status == PreviewStatus.SKIPPED }
    val duplicates = items.count { it.duplicateOf != null }

    val cards = items.joinToString("\n") { item ->
      val thumb = thumbBase64(item)
      val statusClass = when (item.status) {
        PreviewStatus.OK -> "ok"
        PreviewStatus.FAILED -> "failed"
        PreviewStatus.SKIPPED -> "skipped"
        else -> "pending"
      }
      val image = if (thumb != null) {
        """<img src="data:image/jpeg;base64,$thumb" loading="lazy" alt="">"""
      } else {
        """<div class="noimg">no screenshot</div>"""
      }
      val search = buildString {
        append(item.className).append(' ')
        append(item.label).append(' ')
        append(item.screenText).append(' ')
        append(item.note)
      }.lowercase(Locale.ROOT)

      """
      <article class="card $statusClass${if (thumb == null) " noimg" else ""}" data-search="${escape(search)}" data-dup="${item.duplicateOf != null}" data-failed="${item.status == PreviewStatus.FAILED}">
        <div class="shot">$image</div>
        <div class="body">
          <div class="head">
            <span class="idx">#${item.index}</span>
            <span class="badge $statusClass">${item.status}</span>
            ${if (item.duplicateOf != null) """<span class="badge dup">dup of #${item.duplicateOf}</span>""" else ""}
            ${if (!item.exported) """<span class="badge plain">not exported</span>""" else ""}
          </div>
          <h3 title="${escape(item.className)}">${escape(item.shortName)}</h3>
          <p class="label">${escape(item.label)}</p>
          <p class="component">${escape(item.component)}</p>
          ${if (item.note.isNotEmpty()) """<p class="note">${escape(item.note)}</p>""" else ""}
          ${if (item.actual.isNotEmpty() && item.actual != item.component) """<p class="note">top: ${escape(item.actual)}</p>""" else ""}
          <details>
            <summary>screen text (${item.screenText.length})</summary>
            <pre>${escape(item.screenText)}</pre>
          </details>
        </div>
      </article>
      """
    }

    val html = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Activity preview - ${escape(appName)}</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 16px; background: #121212; color: #e6e6e6;
         font: 14px/1.45 -apple-system, "Segoe UI", Roboto, sans-serif; }
  header { position: sticky; top: 0; z-index: 10; margin: -16px -16px 16px; padding: 14px 16px;
           background: rgba(18,18,18,.94); backdrop-filter: blur(8px); border-bottom: 1px solid #2a2a2a; }
  h1 { margin: 0 0 4px; font-size: 18px; }
  .meta { color: #9a9a9a; font-size: 12px; word-break: break-all; }
  .stats { margin: 8px 0 12px; display: flex; gap: 14px; flex-wrap: wrap; font-size: 12px; }
  .stats b { color: #fff; }
  .controls { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
  input[type=search] { flex: 1 1 220px; min-width: 160px; padding: 8px 10px; border-radius: 8px;
                       border: 1px solid #3a3a3a; background: #1d1d1d; color: #eee; outline: none; }
  label.toggle { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #cfcfcf; }
  .grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); }
  .card { background: #1c1c1c; border: 1px solid #2e2e2e; border-radius: 12px; overflow: hidden;
          display: flex; flex-direction: column; }
  .card.failed { border-color: #7a2b2b; }
  .card.skipped { opacity: .62; }
  .shot { background: #0b0b0b; aspect-ratio: 9 / 16; display: flex; align-items: center; justify-content: center; }
  .card.noimg .shot { aspect-ratio: auto; height: 84px; }
  .shot img { width: 100%; height: 100%; object-fit: contain; }
  .noimg { color: #666; font-size: 12px; }
  .body { padding: 10px 12px 12px; display: flex; flex-direction: column; gap: 3px; }
  .head { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; margin-bottom: 4px; }
  .idx { color: #8a8a8a; font-size: 12px; }
  .badge { font-size: 10px; padding: 2px 7px; border-radius: 999px; background: #333; color: #ddd; }
  .badge.ok { background: #1f4d2c; color: #8ee6a4; }
  .badge.failed { background: #5a1f1f; color: #ff9d9d; }
  .badge.skipped { background: #3a3a3a; color: #bbb; }
  .badge.dup { background: #3d3320; color: #e8c97a; }
  .badge.plain { background: #26313d; color: #9cc3e6; }
  h3 { margin: 0; font-size: 14px; word-break: break-all; }
  .label { margin: 0; color: #b9b9b9; font-size: 12px; }
  .component { margin: 0; color: #7f7f7f; font-size: 11px; word-break: break-all; }
  .note { margin: 2px 0 0; color: #d0a85a; font-size: 11px; word-break: break-word; }
  details { margin-top: 6px; }
  summary { cursor: pointer; color: #9cc3e6; font-size: 12px; }
  pre { margin: 6px 0 0; padding: 8px; background: #141414; border-radius: 8px; max-height: 220px;
        overflow: auto; white-space: pre-wrap; word-break: break-word; font-size: 11px; color: #cfcfcf; }
  .empty { color: #777; padding: 40px 0; text-align: center; }
  .hidden { display: none !important; }
</style>
</head>
<body>
<header>
  <h1>${escape(appName)} · Activity preview</h1>
  <div class="meta">${escape(packageName)} · ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())}</div>
  <div class="stats">
    <span>total <b>$total</b></span>
    <span>ok <b>$ok</b></span>
    <span>failed <b>$failed</b></span>
    <span>skipped <b>$skipped</b></span>
    <span>duplicates <b>$duplicates</b></span>
  </div>
  <div class="controls">
    <input id="q" type="search" placeholder="search class / label / screen text" autocomplete="off">
    <label class="toggle"><input id="hideDup" type="checkbox"> hide duplicates</label>
    <label class="toggle"><input id="onlyFailed" type="checkbox"> only failed</label>
  </div>
</header>
<main id="grid" class="grid">
$cards
</main>
<div id="empty" class="empty hidden">no activity matches the filter</div>
<script>
  const cards = Array.from(document.querySelectorAll('.card'));
  const q = document.getElementById('q');
  const hideDup = document.getElementById('hideDup');
  const onlyFailed = document.getElementById('onlyFailed');
  const empty = document.getElementById('empty');
  function apply() {
    const term = q.value.trim().toLowerCase();
    let shown = 0;
    for (const card of cards) {
      let visible = true;
      if (term && !card.dataset.search.includes(term)) visible = false;
      if (hideDup.checked && card.dataset.dup === 'true') visible = false;
      if (onlyFailed.checked && card.dataset.failed !== 'true') visible = false;
      card.classList.toggle('hidden', !visible);
      if (visible) shown++;
    }
    empty.classList.toggle('hidden', shown !== 0);
  }
  q.addEventListener('input', apply);
  hideDup.addEventListener('change', apply);
  onlyFailed.addEventListener('change', apply);
</script>
</body>
</html>
    """.trimIndent()

    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
    val safeName = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val file = File(File(context.getExternalFilesDir(null), ActivityPreviewRunner.DIR_NAME), "report-$safeName-$stamp.html")
    file.writeText(html, Charsets.UTF_8)
    return file
  }

  private fun thumbBase64(item: ActivityPreviewItem): String? {
    val path = item.thumbPath ?: item.shotPath ?: return null
    val file = File(path)
    if (!file.exists() || file.length() <= 0L) return null
    return runCatching {
      Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }.getOrNull()
  }

  private fun escape(value: String): String = buildString(value.length + 16) {
    value.forEach { ch ->
      when (ch) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(ch)
      }
    }
  }
}
