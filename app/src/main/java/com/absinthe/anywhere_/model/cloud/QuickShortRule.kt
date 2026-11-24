package com.absinthe.anywhere_.model.cloud

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * A single rule from the QuickShort rules repository.
 *
 * Every string field is nullable on purpose: Gson allocates the object without calling the
 * Kotlin constructor, so a missing JSON field would otherwise leave a non-null property as null.
 */
@Parcelize
@Keep
data class QuickShortRule(
  @SerializedName("title") val title: String? = null,
  @SerializedName("name") val name: String? = null,
  @SerializedName("subname") val subname: String? = null,
  @SerializedName("author") val author: String? = null,
  @SerializedName("pkg") val pkg: String? = null,
  @SerializedName("path") val path: String? = null,
  @SerializedName("extras") val extras: String? = null,
  @SerializedName("icon") val icon: String? = null,
  @SerializedName("type") val type: Int = 0
) : Parcelable {

  val displayName: String
    get() = title.orEmpty().trim().ifBlank { name.orEmpty().trim() }

  val displaySubtitle: String
    get() = subname.orEmpty().trim().ifBlank { author.orEmpty().trim() }
}

@Keep
data class QuickShortRulesResponse(
  @SerializedName("data") val data: List<QuickShortRule>? = null
)
