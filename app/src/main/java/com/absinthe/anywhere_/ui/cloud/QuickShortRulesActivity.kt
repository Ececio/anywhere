package com.absinthe.anywhere_.ui.cloud

import android.animation.LayoutTransition
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import com.absinthe.anywhere_.AppBarActivity
import com.absinthe.anywhere_.R
import com.absinthe.anywhere_.adapter.cloud.QuickShortRulesAdapter
import com.absinthe.anywhere_.adapter.manager.WrapContentLinearLayoutManager
import com.absinthe.anywhere_.api.ApiManager
import com.absinthe.anywhere_.api.RuleApi
import com.absinthe.anywhere_.constants.GlobalValues
import com.absinthe.anywhere_.databinding.ActivityCloudRulesBinding
import com.absinthe.anywhere_.model.cloud.QuickShortRule
import com.absinthe.anywhere_.model.cloud.QuickShortRulesResponse
import com.absinthe.anywhere_.ui.editor.EXTRA_EDIT_MODE
import com.absinthe.anywhere_.ui.editor.EXTRA_ENTITY
import com.absinthe.anywhere_.ui.editor.EditorActivity
import com.absinthe.anywhere_.utils.QuickShortRuleConverter
import com.absinthe.anywhere_.utils.ToastUtil
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import rikka.widget.borderview.BorderView
import timber.log.Timber

/**
 * Rules imported from the QuickShort community repository.
 *
 * Tapping a rule opens the card editor so it can be reviewed before being added to the home page.
 */
class QuickShortRulesActivity : AppBarActivity<ActivityCloudRulesBinding>(),
  SearchView.OnQueryTextListener {

  private val mAdapter = QuickShortRulesAdapter()
  private var mList = listOf<QuickShortRule>()
  private var isListReady = false

  override fun setViewBinding() = ActivityCloudRulesBinding.inflate(layoutInflater)

  override fun getToolBar() = binding.toolbar.toolBar

  override fun getAppBarLayout() = binding.toolbar.appBar

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestRules()
  }

  override fun initView() {
    binding.list.apply {
      layoutManager = WrapContentLinearLayoutManager(this@QuickShortRulesActivity)
      adapter = mAdapter
      borderVisibilityChangedListener =
        BorderView.OnBorderVisibilityChangedListener { top: Boolean, _: Boolean, _: Boolean, _: Boolean ->
          getAppBarLayout().isLifted = !top
        }
      addItemDecoration(
        DividerItemDecoration(
          this@QuickShortRulesActivity,
          DividerItemDecoration.VERTICAL
        )
      )
      FastScrollerBuilder(this).useMd2Style().build()
    }
    mAdapter.setOnItemClickListener { _, _, position ->
      openRule(mAdapter.data[position])
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.cloud_rules_menu, menu)

    val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
    val searchView = menu.findItem(R.id.search).actionView as SearchView
    searchView.findViewById<LinearLayout>(androidx.appcompat.R.id.search_bar)?.layoutTransition =
      LayoutTransition()

    searchView.apply {
      isQueryRefinementEnabled = true
      setIconifiedByDefault(false)
      setSearchableInfo(searchManager.getSearchableInfo(componentName))
      setOnQueryTextListener(this@QuickShortRulesActivity)
    }

    if (!isListReady) {
      menu.findItem(R.id.search).isVisible = false
    }

    return true
  }

  override fun onQueryTextSubmit(query: String): Boolean {
    return false
  }

  override fun onQueryTextChange(newText: String): Boolean {
    val filter = mList.filter {
      it.displayName.contains(newText, ignoreCase = true) ||
        it.displaySubtitle.contains(newText, ignoreCase = true)
    }
    mAdapter.setNewData(filter.toMutableList())
    return false
  }

  private fun openRule(rule: QuickShortRule) {
    when (val result = QuickShortRuleConverter.convert(rule)) {
      is QuickShortRuleConverter.Result.Supported -> {
        val entity = result.entity.apply {
          category = GlobalValues.category
        }
        startActivity(Intent(this, EditorActivity::class.java).apply {
          putExtra(EXTRA_ENTITY, entity)
          putExtra(EXTRA_EDIT_MODE, false)
        })
      }

      is QuickShortRuleConverter.Result.Unsupported -> {
        Timber.w("Unsupported QuickShort rule: %s", result.reason)
        ToastUtil.makeText(this, R.string.cloud_rules_2_unsupported)
      }
    }
  }

  private fun requestRules() {
    binding.progressHorizontal.show()
    val retrofit = Retrofit.Builder()
      .baseUrl(ApiManager.QUICK_SHORT_RULES_RAW_URL)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
    val request = retrofit.create(RuleApi::class.java).requestQuickShortRules()

    request.enqueue(object : Callback<QuickShortRulesResponse> {
      override fun onResponse(
        call: Call<QuickShortRulesResponse>,
        response: Response<QuickShortRulesResponse>
      ) {
        val list = response.body()?.data.orEmpty()
        mAdapter.setList(list)
        mList = list
        getToolBar().menu?.findItem(R.id.search)?.isVisible = true
        binding.progressHorizontal.hide()
        isListReady = true
      }

      override fun onFailure(call: Call<QuickShortRulesResponse>, t: Throwable) {
        Timber.e(t)
        binding.progressHorizontal.hide()
        ToastUtil.makeText(this@QuickShortRulesActivity, R.string.cloud_rules_2_load_failed)
      }
    })
  }
}
