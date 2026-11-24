package com.absinthe.anywhere_.utils

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import com.absinthe.anywhere_.constants.AnywhereType
import com.absinthe.anywhere_.model.database.AnywhereEntity
import com.blankj.utilcode.util.Utils

/**
 * Resolves the label shown on ACTIVITY cards.
 *
 * An activity without its own `android:label` has no name inside the APK: PackageManager's
 * [ActivityInfo.loadLabel] falls back to the application label, so every such card used to show
 * the same app name. This resolver keeps real labels and user renames, and falls back to a name
 * for well-known SDK / integration activities or to the simplified class name for the rest.
 *
 * It only reads PackageManager and never writes to the database; the result is computed when the
 * card is bound, so existing cards are updated as well.
 */
object ActivityNameResolver {

  private data class ResolvedName(
    val label: String,
    val appLabel: String,
    val hasOwnLabel: Boolean,
    val fallback: String
  )

  /**
   * Names for common SDK / integration activities, matched against the end of the full class
   * name. Longer entries must come first when one name is a suffix of another.
   */
  private val KNOWN_NAMES = listOf(
    "wxapi.WXPayEntryActivity" to "微信支付回调",
    "wxapi.WXEntryActivity" to "微信登录回调",
    "WXPayEntryActivity" to "微信支付回调",
    "WXEntryActivity" to "微信登录回调",
    "sina.weibo.sdk.share.ShareResultActivity" to "微博分享回调",
    "sina.weibo.sdk.share.ShareTransActivity" to "微博分享中转页",
    "xhssharesdk.core.XhsShareActivity" to "小红书分享回调",
    "XhsShareActivity" to "小红书分享回调",
    "jpush.android.service.JNotifyActivity" to "极光推送通知页",
    "JNotifyActivity" to "极光推送通知页",
    "flutter_web_auth.CallbackActivity" to "网页登录回调",
    "urllauncher.WebViewActivity" to "内置浏览器",
    "SignInHubActivity" to "Google 登录",
    "tencent.connect.common.AssistActivity" to "QQ 登录中转页",
    "alipay.sdk.app.H5PayActivity" to "支付宝支付页",
    "alipay.sdk.app.H5AuthActivity" to "支付宝授权页",
    "umeng.socialize.weixin.view.WXCallbackActivity" to "友盟微信回调",
    "umeng.socialize.media.WBShareCallBackActivity" to "友盟微博回调",
    "DouYinEntryActivity" to "抖音分享回调",
    "OpenPlatformShareRealActivity" to "开放平台分享",
    "LarkSSOAuthActivity" to "飞书授权回调",
    "DyPayRooterActivity" to "抖音支付页",
    "UserAgreementActivity" to "用户协议",
    "PrivacyPolicyActivity" to "隐私政策",
    "CrashPreviewActivity" to "崩溃预览页",
    "BootstrapActivity" to "SDK 启动中转页",
    "hpplay.sdk.sink.business.BusinessActivity" to "投屏业务页",
    "hpplay.sdk.sink.business.TipActivity" to "投屏提示页",
    "TransparentAdsAppActivity" to "透明广告页",
    "AdsAppActivity" to "广告落地页",
    "ErrorActivity" to "错误页",
    "StubActivity" to "占位页",
    "DetailActivity" to "详情页",
    "WebViewActivity" to "内置浏览器"
  )

  /** Simple names that normally belong to the app entry point, not to a specific screen. */
  private val ENTRY_CORES = setOf(
    "main", "home", "launch", "launcher", "splash", "entry", "startup", "welcome", "start"
  )

  private val nameCache = object : LruCache<String, ResolvedName>(128) {}
  private val launcherCache = object : LruCache<String, Set<String>>(64) {}

  /**
   * Returns the name to show for [entity]. User renames, non-activity cards and unresolvable
   * components keep their stored name.
   */
  fun getDisplayName(entity: AnywhereEntity): String {
    if (entity.type != AnywhereType.Card.ACTIVITY) return entity.appName

    val packageName = entity.param1.orEmpty()
    val className = entity.param2.orEmpty()
    if (packageName.isBlank() || className.isBlank()) return entity.appName

    val resolved = resolve(packageName, className) ?: return entity.appName
    val storedName = entity.appName

    // The stored name is neither the activity label nor the app label: it was renamed by the user.
    if (storedName != resolved.label && storedName != resolved.appLabel) return storedName

    return if (resolved.hasOwnLabel) resolved.label else resolved.fallback
  }

  private fun resolve(packageName: String, className: String): ResolvedName? {
    val fullClassName = if (className.startsWith(".")) packageName + className else className
    val cacheKey = "$packageName/$fullClassName"
    nameCache.get(cacheKey)?.let { return it }

    val packageManager = Utils.getApp().packageManager
    val activityInfo = findActivity(packageManager, packageName, fullClassName) ?: return null

    val label = runCatching { activityInfo.loadLabel(packageManager).toString() }
      .getOrElse { return null }
    val appLabel = runCatching {
      packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    }.getOrDefault(label)
    val hasOwnLabel = activityInfo.labelRes != 0 || activityInfo.nonLocalizedLabel != null

    val fallback = when {
      hasOwnLabel -> label
      isEntryPoint(packageManager, packageName, fullClassName) -> label
      else -> knownName(fullClassName) ?: simplifyClassName(fullClassName) ?: label
    }

    return ResolvedName(label, appLabel, hasOwnLabel, fallback).also {
      nameCache.put(cacheKey, it)
    }
  }

  @Suppress("DEPRECATION")
  private fun findActivity(
    packageManager: PackageManager,
    packageName: String,
    className: String
  ): ActivityInfo? {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      PackageManager.MATCH_DISABLED_COMPONENTS
    } else {
      PackageManager.GET_DISABLED_COMPONENTS
    }

    return try {
      packageManager.getActivityInfo(ComponentName(packageName, className), flags)
    } catch (e: Exception) {
      // Frozen apps reject per-component queries, but the package info still lists activities.
      try {
        packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES or flags)
          .activities
          ?.firstOrNull { it.name == className }
      } catch (e: Exception) {
        null
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun isEntryPoint(
    packageManager: PackageManager,
    packageName: String,
    className: String
  ): Boolean {
    if (entryCore(className.substringAfterLast('.')) in ENTRY_CORES) return true

    val launcherComponents = launcherCache.get(packageName) ?: try {
      val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage(packageName)
      packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { it.activityInfo?.name }
        .toSet()
        .also { launcherCache.put(packageName, it) }
    } catch (e: Exception) {
      emptySet()
    }

    return className in launcherComponents
  }

  /** Removes Activity / Alias suffixes so "MainActivityAlias" becomes "Main". */
  private fun entryCore(simpleName: String): String {
    val suffixes = listOf("ActivityAlias", "AliasActivity", "Alias", "Activity")
    var name = simpleName
    var changed = true
    while (changed) {
      changed = false
      for (suffix in suffixes) {
        if (name.length > suffix.length && name.endsWith(suffix, ignoreCase = true)) {
          name = name.dropLast(suffix.length)
          changed = true
          break
        }
      }
    }
    return name.lowercase()
  }

  private fun knownName(className: String): String? {
    KNOWN_NAMES.firstOrNull { className.endsWith(it.first) }?.let { return it.second }
    return null
  }

  /**
   * Produces a short, readable class name, or null when the name is obfuscated
   * (e.g. "OoO0O0OO0OOo00O" or "l.ۖܺۨ") and would be meaningless on a card.
   */
  private fun simplifyClassName(className: String): String? {
    val simpleName = className.substringAfterLast('.')
    if (simpleName.any { it.code > 127 }) return null
    if (simpleName.count { it.isLetter() } < 2) return null

    val name = if (simpleName.length > 10 && simpleName.endsWith("Activity", true)) {
      simpleName.dropLast("Activity".length)
    } else {
      simpleName
    }

    if (name.length < 2) return null
    // Obfuscated names are long runs of single characters without normal lowercase words.
    if (longestLowercaseRun(name) < 3) return null
    val distinctLetters = name.lowercase().filter { it in 'a'..'z' }.toSet().size
    if (distinctLetters < 3) return null
    if (name.count { it.isDigit() } >= 2) return null
    return name
  }

  private fun longestLowercaseRun(value: String): Int {
    var longest = 0
    var current = 0
    for (char in value) {
      if (char in 'a'..'z') {
        current++
        if (current > longest) longest = current
      } else {
        current = 0
      }
    }
    return longest
  }

  fun clearCache() {
    nameCache.evictAll()
    launcherCache.evictAll()
  }
}
