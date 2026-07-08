package com.example.focusgate

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object DeepLinkLauncher {
    fun openSearch(context: Context, platform: TargetPlatform, keyword: String): DeepLinkResult =
        when (platform) {
            TargetPlatform.XHS -> openXhsSearch(context, keyword)
            TargetPlatform.BILI -> openBiliSearch(context, keyword)
            TargetPlatform.DOUYIN -> {
                Log.w(SearchGateAppTypeLog.TAG, "blocked Douyin study lookup deep link because Douyin is ENTERTAINMENT_ONLY")
                DeepLinkResult.FAILED_TO_START
            }
        }

    fun openPackage(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            Log.w(FocusGateLog.TAG, "open package failed: $packageName")
            false
        }
    }

    private fun openXhsSearch(context: Context, keyword: String): DeepLinkResult {
        val encoded = Uri.encode(keyword)
        val uri = Uri.parse("xhsdiscover://search/result?keyword=$encoded&target_search=notes")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(TargetPlatform.XHS.packageName)
        }
        return start(context, intent)
    }

    private fun openBiliSearch(context: Context, keyword: String): DeepLinkResult {
        val encoded = Uri.encode(keyword)
        val uri = Uri.parse("bilibili://search?keyword=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(TargetPlatform.BILI.packageName)
        }
        return start(context, intent)
    }

    private fun openDouyinSearch(context: Context, keyword: String): DeepLinkResult {
        val encoded = Uri.encode(keyword)
        val uri = Uri.parse("snssdk1128://search?keyword=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(TargetPlatform.DOUYIN.packageName)
        }
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): DeepLinkResult =
        try {
            context.startActivity(intent)
            Log.i(FocusGateLog.TAG, "deep link result = STARTED")
            DeepLinkResult.STARTED
        } catch (e: ActivityNotFoundException) {
            Log.w(FocusGateLog.TAG, "deep link result = FAILED_TO_START")
            DeepLinkResult.FAILED_TO_START
        } catch (e: SecurityException) {
            Log.w(FocusGateLog.TAG, "deep link result = FAILED_TO_START")
            DeepLinkResult.FAILED_TO_START
        }
}
