package com.example.focusgate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class StabilityForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: GuardRepository
    private var countdownRunnable: Runnable? = null
    private var permissionWarning: String? = null

    override fun onCreate() {
        super.onCreate()
        repository = GuardRepository(this)
        running = true
        ensureChannel()
        Log.i(FocusGateLog.TAG, "stability foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(FocusGateLog.TAG, "stability foreground service stopped by user")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PERMISSION_WARNING) {
            permissionWarning = intent.getStringExtra(EXTRA_PERMISSION_WARNING)?.takeIf { it.isNotBlank() }
        }

        return runCatching {
            startForeground(NOTIFICATION_ID, notification())
            scheduleCountdownIfNeeded()
            Log.i(FocusGateLog.TAG, "stability foreground service started")
            START_STICKY
        }.getOrElse {
            Log.w(FocusGateLog.TAG, "stability foreground service failed: ${it.javaClass.simpleName}")
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        running = false
        Log.w(FocusGateLog.TAG, "stability foreground service destroyed")
        PermissionAlertCoordinator.evaluate(this, "foreground_service_destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SearchGate 稳定运行",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示 SearchGate 正在保持无障碍检测状态"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val state = repository.state()
        val now = System.currentTimeMillis()
        val remainingMs = (state.entertainmentUntil - now).coerceAtLeast(0L)
        val entertainmentActive = state.entertainmentPackage != null && remainingMs > 0L
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, StabilityForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "停止",
            stopIntent
        ).build()

        val title = if (permissionWarning != null) {
            "检索门：关键权限异常"
        } else if (entertainmentActive) {
            "检索门：娱乐时间进行中"
        } else {
            "SearchGate 正在检测目标应用"
        }
        val content = permissionWarning ?: if (entertainmentActive) {
            "剩余 ${((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)} 分钟，结束后将重新恢复限制。"
        } else {
            "用于提高无障碍监听稳定性，可在通知中停止。"
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(false)
            .addAction(stopAction)
            .build()
    }

    private fun scheduleCountdownIfNeeded() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null

        val state = repository.state()
        val remainingMs = (state.entertainmentUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        if (state.entertainmentPackage == null || remainingMs <= 0L) return

        val runnable = object : Runnable {
            override fun run() {
                val refreshed = repository.state()
                val remaining = (refreshed.entertainmentUntil - System.currentTimeMillis()).coerceAtLeast(0L)
                runCatching {
                    startForeground(NOTIFICATION_ID, notification())
                }.onFailure {
                    Log.w(FocusGateLog.TAG, "refresh countdown notification failed: ${it.javaClass.simpleName}")
                }
                if (refreshed.entertainmentPackage != null && remaining > 0L) {
                    handler.postDelayed(this, COUNTDOWN_UPDATE_MS)
                } else {
                    countdownRunnable = null
                }
            }
        }
        countdownRunnable = runnable
        handler.postDelayed(runnable, COUNTDOWN_UPDATE_MS.coerceAtMost(remainingMs))
    }

    companion object {
        private const val CHANNEL_ID = "searchgate_stability"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.example.focusgate.action.STOP_STABILITY"
        private const val ACTION_SYNC = "com.example.focusgate.action.SYNC_ENTERTAINMENT"
        private const val ACTION_PERMISSION_WARNING = "com.example.focusgate.action.PERMISSION_WARNING"
        private const val EXTRA_PERMISSION_WARNING = "permissionWarning"
        private const val COUNTDOWN_UPDATE_MS = 30_000L
        @Volatile private var running = false

        fun isRunning(): Boolean = running

        fun startIfAllowed(context: Context) {
            start(context, null)
        }

        fun syncEntertainmentState(context: Context) {
            start(context, ACTION_SYNC)
        }

        fun updatePermissionWarningIfRunning(context: Context, warning: String?): Boolean {
            if (!running) return false
            val intent = Intent(context, StabilityForegroundService::class.java)
                .setAction(ACTION_PERMISSION_WARNING)
                .putExtra(EXTRA_PERMISSION_WARNING, warning.orEmpty())
            return runCatching {
                context.startService(intent)
                true
            }.getOrElse {
                Log.w(
                    SearchGatePermissionAlertLog.TAG,
                    "foreground notification update request failed error=${it.javaClass.simpleName}"
                )
                false
            }
        }

        private fun start(context: Context, action: String?) {
            if (!PermissionUtils.canPostNotifications(context)) {
                Log.w(FocusGateLog.TAG, "skip stability service because notification permission is missing")
                return
            }
            val intent = Intent(context, StabilityForegroundService::class.java).apply {
                if (action != null) setAction(action)
            }
            runCatching {
                context.startForegroundService(intent)
            }.onFailure {
                Log.w(FocusGateLog.TAG, "start stability service failed: ${it.javaClass.simpleName}")
            }
        }
    }
}
