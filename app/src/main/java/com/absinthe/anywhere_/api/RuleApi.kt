package com.absinthe.anywhere_.api

import com.absinthe.anywhere_.model.cloud.QuickShortRulesResponse
import com.absinthe.anywhere_.model.cloud.RuleEntity
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

interface RuleApi {

  @GET("rules/rules.json")
  fun requestAllRules(): Call<List<RuleEntity>>

  @GET("rules.json")
  fun requestQuickShortRules(): Call<QuickShortRulesResponse>

  @GET
  fun requestQuickShortRulesFrom(@Url url: String): Call<QuickShortRulesResponse>
}
