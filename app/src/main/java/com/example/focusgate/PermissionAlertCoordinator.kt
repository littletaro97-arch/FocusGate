package com.example.focusgate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

enum class CriticalPermissionIssue(val displayName: String) {
    ACCESSIBILITY("无障碍服务"),
    OVERLAY("悬浮窗权限"),
    NOTIFICATIONS("通知权限"),
    FOREGROUND_SERVICE("前台服务")
}

data class PermissionHealth(
    val missingCritical: Set<CriticalPermissionIssue>,
    val batteryOptimizationIgnored: Boolean
)

object PermissionAlertCoordinator {
    @Synchronized
    fun evaluate(
        context: Context,
        trigger: String,
        includeForegroundService: Boolean = true
    ): PermissionHealth {
        val appContext = context.applicationContext
        val accessibilityEnabled = PermissionUtils.isAccessibilityEnabled(appContext)
        val overlayEnabled = PermissionUtils.canDrawOverlays(appContext)
        val notificationsEnabled = PermissionUtils.canPostNotifications(appContext)
        val missing = linkedSetOf<CriticalPermissionIssue>()
        if (!accessibilityEnabled) missing += CriticalPermissionIssue.ACCESSIBILITY
        if (!overlayEnabled) missing += CriticalPermissionIssue.OVERLAY
        if (!notificationsEnabled) missing += CriticalPermissionIssue.NOTIFICATIONS
        if (
            includeForegroundService &&
            accessibilityEnabled &&
            notificationsEnabled &&
            !StabilityForegroundService.isRunning()
        ) {
            missing += CriticalPermissionIssue.FOREGROUND_SERVICE
        }

        val health = PermissionHealth(
            missingCritical = missing,
            batteryOptimizationIgnored = PermissionUtils.isIgnoringBatteryOptimizations(appContext)
        )
        val signature = missing.joinToString(",") { it.name }
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousSignature = prefs.getString(KEY_LAST_SIGNATURE, null)

        if (signature == previousSignature) {
            Log.d(
                SearchGatePermissionAlertLog.TAG,
                "permission state unchanged trigger=$trigger missing=$signature duplicateSkipped=true notificationsEnabled=$notificationsEnabled"
            )
            return health
        }

        prefs.edit().putString(KEY_LAST_SIGNATURE, signature).apply()
        Log.w(
            SearchGatePermissionAlertLog.TAG,
            "permission state changed trigger=$trigger previous=${previousSignature ?: "UNINITIALIZED"} current=${signature.ifEmpty { "OK" }} batteryWhitelist=${health.batteryOptimizationIgnored}"
        )

        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (missing.isEmpty()) {
            manager.cancel(ALERT_NOTIFICATION_ID)
            StabilityForegroundService.updatePermissionWarningIfRunning(appContext, null)
            Log.i(SearchGatePermissionAlertLog.TAG, "permissions recovered alertCancelled=true")
            return health
        }

        val warning = buildWarning(missing)
        if (!notificationsEnabled) {
            val foregroundUpdateRequested = StabilityForegroundService
                .updatePermissionWarningIfRunning(appContext, warning)
            Log.w(
                SearchGatePermissionAlertLog.TAG,
                "ordinary notification blocked reason=notification_permission_missing inAppRedState=true foregroundNotificationUpdateRequested=$foregroundUpdateRequested deliveryNotGuaranteed=true"
            )
            return health
        }

        ensureChannel(manager)
        val submitted = runCatching {
            manager.notify(ALERT_NOTIFICATION_ID, buildNotification(appContext, warning))
            true
        }.getOrElse {
            Log.w(
                SearchGatePermissionAlertLog.TAG,
                "permission alert submit failed error=${it.javaClass.simpleName}"
            )
            false
        }
        StabilityForegroundService.updatePermissionWarningIfRunning(appContext, warning)
        Log.i(
            SearchGatePermissionAlertLog.TAG,
            "permission alert notificationSubmitted=$submitted deliveryNotAssumed=true missing=$signature"
        )
        return health
    }

    private fun buildWarning(missing: Set<CriticalPermissionIssue>): String =
        if (missing.size == 1) {
            "${missing.first().displayName}已关闭，请重新开启以恢复限制功能。"
        } else {
            "部分关键权限已关闭：${missing.joinToString("、") { it.displayName }}。请打开检索门修复。"
        }

    private fun ensureChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "权限与运行状态",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "关键权限或运行状态异常时提醒"
            }
        )
    }

    private fun buildNotification(context: Context, warning: String): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            ALERT_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(context, ALERT_CHANNEL_ID)
        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("检索门权限异常")
            .setContentText(warning)
            .setStyle(Notification.BigTextStyle().bigText(warning))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private const val PREFS_NAME = "searchgate_permission_alert"
    private const val KEY_LAST_SIGNATURE = "lastCriticalPermissionSignature"
    private const val ALERT_CHANNEL_ID = "searchgate_permission_status"
    private const val ALERT_NOTIFICATION_ID = 1002
}
