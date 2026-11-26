package com.absinthe.anywhere_.ui.preview

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.absinthe.anywhere_.AppBarActivity
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.constants.Const
import com.absinthe.anywhere_.databinding.ActivityActivityPreviewBinding
import com.absinthe.anywhere_.databinding.DialogActivityPreviewBinding
import com.absinthe.anywhere_.ui.list.AppListActivity
import com.absinthe.anywhere_.ui.list.EXTRA_APP_LIST_ENTRY_MODE
import com.absinthe.anywhere_.ui.list.EXTRA_PACKAGE_NAME
import com.absinthe.anywhere_.ui.list.MODE_SELECT
import com.absinthe.anywhere_.utils.ToastUtil
import com.absinthe.anywhere_.utils.manager.ShellManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ActivityPreviewActivity : AppBarActivity<ActivityActivityPreviewBinding>() {

  private val adapter = ActivityPreviewAdapter()
  private val allItems = mutableListOf<ActivityPreviewItem>()
  private val visibleItems = mutableListOf<ActivityPreviewItem>()
  private var job: Job? = null
  private var packageName: String = ""
  private lateinit var runner: ActivityPreviewRunner

  override fun setViewBinding() = ActivityActivityPreviewBinding.inflate(layoutInflater)

  override fun getToolBar() = binding.toolbar.toolBar

  override fun getAppBarLayout() = binding.toolbar.appBar

  override fun onApplyUserThemeResource(theme: Resources.Theme, isDecorView: Boolean) {
    super.onApplyUserThemeResource(theme, isDecorView)
  }

  override fun initView() {
    super.initView()
    runner = ActivityPreviewRunner(this)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    binding.rvPreview.layoutManager = LinearLayoutManager(this)
    binding.rvPreview.adapter = adapter
    adapter.setNewInstance(visibleItems)
    adapter.setOnItemClickListener { _, _, position ->
      adapter.getItemOrNull(position)?.let { showDetail(it) }
    }

    binding.btnSelect.setOnClickListener { selectApp() }
    binding.btnRun.setOnClickListener { if (job?.isActive == true) stopPreview() else startPreview() }
    binding.btnExport.setOnClickListener { exportReport() }

    binding.etSearch.doAfterTextChanged { rebuildVisible() }
    binding.cbHideDuplicates.setOnCheckedChangeListener { _, _ -> rebuildVisible() }
    binding.cbOnlyFailed.setOnCheckedChangeListener { _, _ -> rebuildVisible() }

    // Allow `am start ... --es EXTRA_PACKAGE_NAME <pkg>` to prefill the target.
    intent?.getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf { it.isNotBlank() }?.let { pkg ->
      packageName = pkg
      binding.tvPackage.text = pkg
    }

    ActivityPreviewStore.load(this)?.let { (pkg, saved) ->
      if (packageName.isEmpty()) packageName = pkg
      if (packageName == pkg) {
        binding.tvPackage.text = pkg
        allItems.clear()
        allItems.addAll(saved.map {
          var item = if (it.status == PreviewStatus.RUNNING) it.copy(status = PreviewStatus.PENDING) else it
          if (item.shotPath == null && item.status != PreviewStatus.SKIPPED) {
            val file = runner.shotFile(item)
            if (file.exists()) item = item.copy(shotPath = file.absolutePath)
          }
          if (item.thumbPath == null) {
            val file = runner.thumbFile(item)
            if (file.exists()) item = item.copy(thumbPath = file.absolutePath)
          }
          item
        })
        recomputeDuplicates()
        rebuildVisible()
        binding.btnExport.isEnabled = allItems.any { it.status == PreviewStatus.OK }
        binding.tvProgress.text = summary()
        ActivityPreviewStore.save(this, pkg, appName(pkg), allItems.toList())
      }
    }
  }

  private fun recomputeDuplicates() {
    for (index in allItems.indices) {
      allItems[index] = allItems[index].copy(duplicateOf = null)
    }
    markDuplicates()
  }

  private fun summary(): String {
    val ok = allItems.count { it.status == PreviewStatus.OK }
    val failed = allItems.count { it.status == PreviewStatus.FAILED }
    val skipped = allItems.count { it.status == PreviewStatus.SKIPPED }
    val duplicates = allItems.count { it.duplicateOf != null }
    return getString(R.string.preview_summary, allItems.size, ok, failed, skipped, duplicates)
  }

  private fun selectApp() {
    if (job?.isActive == true) {
      ToastUtil.makeText(this, R.string.preview_stop_first)
      return
    }
    startActivityForResult(
      Intent(this, AppListActivity::class.java).apply {
        putExtra(EXTRA_APP_LIST_ENTRY_MODE, MODE_SELECT)
      },
      Const.REQUEST_CODE_APP_LIST_SELECT
    )
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == Const.REQUEST_CODE_APP_LIST_SELECT) {
      data?.getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf { it.isNotBlank() }?.let { applyPackage(it) }
    }
    @Suppress("DEPRECATION")
    super.onActivityResult(requestCode, resultCode, data)
  }

  /** Supports `am start ... --es EXTRA_PACKAGE_NAME <pkg>` while the screen is already open. */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf { it.isNotBlank() } ?: return
    if (pkg == packageName) return
    if (job?.isActive == true) {
      ToastUtil.makeText(this, R.string.preview_stop_first)
      return
    }
    applyPackage(pkg)
  }

  private fun applyPackage(pkg: String) {
    packageName = pkg
    binding.tvPackage.text = pkg
    binding.btnExport.isEnabled = false
    allItems.clear()
    rebuildVisible()
    binding.tvProgress.text = ""
    binding.progress.visibility = View.GONE
    ActivityPreviewStore.clear(this)
  }

  private fun startPreview() {
    val pkg = packageName
    if (pkg.isBlank()) {
      ToastUtil.makeText(this, R.string.preview_select_app_first)
      return
    }
    if (job?.isActive == true) return

    binding.btnRun.setText(R.string.preview_stop)
    binding.btnExport.isEnabled = false
    binding.progress.visibility = View.VISIBLE
    binding.progress.isIndeterminate = false
    binding.progress.progress = 0
    binding.tvProgress.text = getString(R.string.preview_probing, 0, 0)

    job = lifecycleScope.launch {
      val hasRoot = withContext(Dispatchers.IO) { ShellManager.acquireRoot() }
      if (!hasRoot) {
        ToastUtil.makeText(this@ActivityPreviewActivity, R.string.preview_need_root)
        finishRun()
        return@launch
      }

      val list = withContext(Dispatchers.IO) {
        runner.cleanup()
        runner.enumerate(pkg)
      }
      if (list.isEmpty()) {
        ToastUtil.makeText(this@ActivityPreviewActivity, R.string.preview_no_activity)
        finishRun()
        return@launch
      }

      allItems.clear()
      allItems.addAll(list)
      rebuildVisible()
      binding.progress.max = list.size
      binding.tvProgress.text = getString(R.string.preview_probing, 0, list.size)
      ActivityPreviewStore.clear(this@ActivityPreviewActivity)

      runner.prepare()
      delay(1200)

      list.forEach { original ->
        if (!isActive) return@forEach
        val running = original.copy(status = PreviewStatus.RUNNING)
        updateItem(running)
        binding.tvProgress.text = getString(R.string.preview_probing, original.index, list.size)

        val result = runner.probe(original)
        if (isActive) {
          updateItem(result)
          binding.progress.progress = original.index
          withContext(Dispatchers.IO) {
            ActivityPreviewStore.save(
              this@ActivityPreviewActivity,
              pkg,
              appName(pkg),
              allItems.toList()
            )
          }
        }
        if (isActive) runner.dismissForeignScreen(result.actual)
      }

      if (isActive) {
        markDuplicates()
        rebuildVisible()
        binding.btnExport.isEnabled = allItems.any { it.status == PreviewStatus.OK }
        binding.tvProgress.text = summary()
        withContext(Dispatchers.IO) {
          ActivityPreviewStore.save(
            this@ActivityPreviewActivity,
            pkg,
            appName(pkg),
            allItems.toList()
          )
        }
      }
      finishRun()
    }
  }

  private fun stopPreview() {
    job?.cancel()
    job = null
    for (index in allItems.indices) {
      if (allItems[index].status == PreviewStatus.RUNNING) {
        allItems[index] = allItems[index].copy(status = PreviewStatus.PENDING)
      }
    }
    rebuildVisible()
    binding.btnExport.isEnabled = allItems.any { it.status == PreviewStatus.OK }
    if (allItems.isNotEmpty()) binding.tvProgress.text = summary()
    finishRun()
    ToastUtil.makeText(this, R.string.preview_stopped)
  }

  private fun finishRun() {
    binding.btnRun.setText(R.string.preview_start)
    binding.progress.visibility = View.GONE
  }

  private fun updateItem(item: ActivityPreviewItem) {
    val index = allItems.indexOfFirst { it.index == item.index }
    if (index < 0) return
    allItems[index] = item
    val visibleIndex = visibleItems.indexOfFirst { it.index == item.index }
    if (visibleIndex >= 0) {
      visibleItems[visibleIndex] = item
      adapter.notifyItemChanged(visibleIndex)
    }
  }

  private fun markDuplicates() {
    allItems.forEachIndexed { index, item ->
      if (item.status != PreviewStatus.OK || item.duplicateOf != null) return@forEachIndexed
      for (previous in 0 until index) {
        val candidate = allItems[previous]
        if (ActivityPreviewRunner.isDuplicate(item, candidate)) {
          allItems[index] = item.copy(duplicateOf = candidate.index)
          break
        }
      }
    }
  }

  private fun rebuildVisible() {
    val query = binding.etSearch.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val hideDuplicates = binding.cbHideDuplicates.isChecked
    val onlyFailed = binding.cbOnlyFailed.isChecked

    val filtered = allItems.filter { item ->
      (query.isEmpty() || matches(item, query)) &&
        (!hideDuplicates || item.duplicateOf == null) &&
        (!onlyFailed || item.status == PreviewStatus.FAILED)
    }
    visibleItems.clear()
    visibleItems.addAll(filtered)
    adapter.notifyDataSetChanged()
  }

  private fun matches(item: ActivityPreviewItem, query: String): Boolean {
    return item.className.lowercase(Locale.ROOT).contains(query) ||
      item.label.lowercase(Locale.ROOT).contains(query) ||
      item.screenText.lowercase(Locale.ROOT).contains(query) ||
      item.note.lowercase(Locale.ROOT).contains(query)
  }

  private fun showDetail(item: ActivityPreviewItem) {
    val dialogBinding = DialogActivityPreviewBinding.inflate(layoutInflater)
    dialogBinding.tvDialogTitle.text = "#${item.index}  ${item.shortName}"
    dialogBinding.tvDialogSub.text = item.component
    dialogBinding.tvDialogNote.text = buildString {
      append(item.status.name)
      if (item.duplicateOf != null) append(" · duplicate of #${item.duplicateOf}")
      if (item.note.isNotEmpty()) append(" · ${item.note}")
      if (item.actual.isNotEmpty() && item.actual != item.component) {
        append("\ntop: ${item.actual}")
      }
    }
    dialogBinding.tvDialogText.text = item.screenText.ifEmpty {
      getString(R.string.preview_no_screen_text)
    }

    val shot = item.shotPath?.let { File(it) }
    if (shot != null && shot.exists()) {
      Glide.with(this).load(shot).into(dialogBinding.ivDialogShot)
    } else {
      dialogBinding.ivDialogShot.visibility = View.GONE
    }

    MaterialAlertDialogBuilder(this)
      .setView(dialogBinding.root)
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  private fun exportReport() {
    if (allItems.isEmpty()) {
      ToastUtil.makeText(this, R.string.preview_no_activity)
      return
    }

    binding.btnExport.isEnabled = false
    lifecycleScope.launch {
      val file = withContext(Dispatchers.IO) {
        ActivityPreviewReport.write(
          this@ActivityPreviewActivity,
          appName(packageName),
          packageName,
          allItems.toList()
        )
      }
      binding.btnExport.isEnabled = true

      val uri = androidx.core.content.FileProvider.getUriForFile(
        this@ActivityPreviewActivity,
        applicationContext.packageName + ".fileprovider",
        file
      )
      val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "text/html")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      try {
        startActivity(
          Intent.createChooser(view, getString(R.string.preview_export))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
      } catch (e: ActivityNotFoundException) {
        try {
          startActivity(Intent.createChooser(share, getString(R.string.preview_export)))
        } catch (e: ActivityNotFoundException) {
          ToastUtil.makeText(this@ActivityPreviewActivity, file.absolutePath)
        }
      }
    }
  }

  private fun appName(pkg: String): String {
    return runCatching {
      val info = packageManager.getApplicationInfo(pkg, 0)
      packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)
  }

  override fun onDestroy() {
    job?.cancel()
    job = null
    super.onDestroy()
  }
}
