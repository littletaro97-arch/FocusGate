package com.example.focusgate

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

private enum class FloatingEndState {
    IDLE,
    PRESSING,
    LONG_PRESS_PROGRESS,
    DRAGGING,
    TRIGGERED,
    CANCELLED
}

class OverlayController(
    private val service: FocusAccessibilityService,
    private val repository: GuardRepository
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var blockingView: View? = null
    private var blockingPackage: String? = null
    private var entertainmentControlView: View? = null
    private var entertainmentHoldRunnable: Runnable? = null
    private var lastEntertainmentHintAt = 0L

    fun isShowing(): Boolean = blockingView != null
    fun isShowingFor(targetPackage: String): Boolean = blockingView != null && blockingPackage == targetPackage

    fun dismiss(clearPromptState: Boolean = true) {
        blockingView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        blockingView = null
        val previousPackage = blockingPackage
        blockingPackage = null
        if (clearPromptState) {
            repository.markPromptClosedByAction(previousPackage)
        }
    }

    fun dismissEntertainmentControl() {
        entertainmentHoldRunnable?.let { mainHandler.removeCallbacks(it) }
        entertainmentHoldRunnable = null
        entertainmentControlView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        entertainmentControlView = null
    }

    fun showIntercept(targetPackage: String, pageKind: PageKind) {
        if (!Settings.canDrawOverlays(service)) {
            Log.w(FocusGateLog.TAG, "skip overlay because permission missing")
            return
        }
        dismissEntertainmentControl()
        if (isShowing()) {
            Log.i(FocusGateLog.TAG, "skip intercept because overlay showing")
            return
        }

        val platformName = displayName(targetPackage)
        val state = repository.state()
        val appConfig = repository.targetAppConfig(targetPackage) ?: TargetAppConfig(
            packageName = targetPackage,
            appName = platformName,
            appType = TargetAppType.ENTERTAINMENT_ONLY,
            isEnabled = true,
            allowStudyLookup = false,
            allowEntertainment = true,
            source = TargetAppSource.USER_ADDED
        )
        val entertainmentMinutes = (state.entertainmentDurationMs / 60_000L).coerceAtLeast(1L)
        val remainingCount = (state.entertainmentDailyLimit - state.dailyEntertainmentCount).coerceAtLeast(0)
        val root = baseLayout()
        root.addView(title("你打开了：$platformName"))
        root.addView(body(if (appConfig.allowStudyLookup) {
            "先明确查找目的，再进入搜索页。当前页面判断：$pageKind"
        } else {
            "这是纯限制类 App，不提供资料查找入口。当前页面判断：$pageKind"
        }))

        if (appConfig.allowStudyLookup) {
            root.addView(primaryButton("资料查找") {
                Log.i(FocusGateLog.TAG, "prompt action=search target=$targetPackage appType=${appConfig.appType}")
                repository.markSearchRequested(targetPackage)
                service.suppressTargetEvents(targetPackage, 1_000L)
                dismiss(clearPromptState = false)
                val intent = Intent(service, KeywordActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(KeywordActivity.EXTRA_TARGET_PACKAGE, targetPackage)
                }
                service.startActivity(intent)
            })
        }

        val canUseEntertainment = repository.canUseEntertainment()
        val entertainmentLabel = when {
            !repository.hasTodayEntertainmentPlan() -> "今天还未设置娱乐计划"
            canUseEntertainment -> "娱乐 ${entertainmentMinutes} 分钟（剩余 ${remainingCount} 次）"
            else -> "今日娱乐次数已用完"
        }
        root.addView(primaryButton(entertainmentLabel) {
            Log.i(FocusGateLog.TAG, "prompt action=entertainment target=$targetPackage")
            if (repository.startEntertainment(targetPackage)) {
                Log.i(FocusGateLog.TAG, "entertainment allowed until = ${repository.state().entertainmentUntil}")
                dismiss(clearPromptState = false)
            }
        }.apply { isEnabled = canUseEntertainment })

        root.addView(secondaryButton("返回桌面") {
            Log.i(FocusGateLog.TAG, "prompt action=home target=$targetPackage")
            dismiss()
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        })

        Log.i(
            SearchGateAppTypeLog.TAG,
            "packageName=${appConfig.packageName} appName=${appConfig.appName} appType=${appConfig.appType} allowStudyLookup=${appConfig.allowStudyLookup} allowEntertainment=${appConfig.allowEntertainment} isEntertainmentActive=false remainingEntertainmentMillis=${repository.remainingEntertainmentMs()} todayQuotaRemaining=$remainingCount shouldShowPrompt=true promptButtons=${if (appConfig.allowStudyLookup) "study,entertainment,home" else "entertainment,home"} hideStudyReason=${if (appConfig.allowStudyLookup) "-" else "appType=${appConfig.appType}"}"
        )

        add(root, targetPackage)
    }

    fun showDailyEntertainmentPlanSetup(targetPackage: String) {
        if (!Settings.canDrawOverlays(service) || isShowing()) return
        dismissEntertainmentControl()

        val platformName = displayName(targetPackage)
        val availablePackages = repository.targetPackages().toList().sortedBy { displayName(it) }
        val selectedPackages = availablePackages.toMutableSet()
        val state = repository.state()
        val quotaLocked = repository.hasTodayEntertainmentPlan()
        var dailyLimit = if (quotaLocked) state.entertainmentDailyLimit else state.defaultEntertainmentDailyLimit
        var durationMinutes = if (quotaLocked) {
            (state.entertainmentDurationMs / 60_000L).toInt()
        } else {
            (state.defaultEntertainmentDurationMs / 60_000L).toInt()
        }

        val root = baseLayout()
        val countText = body("")
        val durationText = body("")

        fun refresh() {
            countText.text = "今天允许娱乐次数：$dailyLimit 次"
            durationText.text = "每次娱乐时长：$durationMinutes 分钟"
        }

        root.addView(title("设置今天的控制规则"))
        root.addView(body(if (quotaLocked) {
            "今天的娱乐额度已经确认，只能选择今天要控制的应用。"
        } else {
            "第一次打开 $platformName 前先确认今天的控制应用和娱乐额度。今天确认后不能再次修改。"
        }))
        availablePackages.forEach { packageName ->
            root.addView(CheckBox(service).apply {
                text = "${displayName(packageName)}\n$packageName"
                textSize = 16f
                setTextColor(Color.rgb(25, 31, 36))
                isChecked = selectedPackages.contains(packageName)
                setPadding(0, dp(4), 0, dp(4))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedPackages.add(packageName)
                    } else {
                        selectedPackages.remove(packageName)
                    }
                }
            })
        }
        if (availablePackages.isEmpty()) {
            root.addView(body("当前没有配置检测应用。请回到 FocusGate 主界面添加。"))
        }
        val decreaseCountButton = secondaryButton("-1 次") {
                dailyLimit = (dailyLimit - 1).coerceAtLeast(0)
                refresh()
            }
        val increaseCountButton = secondaryButton("+1 次") {
                dailyLimit = (dailyLimit + 1).coerceAtMost(GuardRepository.MAX_DAILY_ENTERTAINMENT_LIMIT)
                refresh()
            }
        decreaseCountButton.isEnabled = !quotaLocked
        increaseCountButton.isEnabled = !quotaLocked
        root.addView(countText)
        root.addView(horizontalButtons(decreaseCountButton, increaseCountButton))
        root.addView(durationText)
        val decreaseDurationButton = secondaryButton("-5 分钟") {
                durationMinutes = (durationMinutes - 5).coerceAtLeast(GuardRepository.MIN_ENTERTAINMENT_MINUTES)
                refresh()
            }
        val increaseDurationButton = secondaryButton("+5 分钟") {
                durationMinutes = (durationMinutes + 5).coerceAtMost(GuardRepository.MAX_ENTERTAINMENT_MINUTES)
                refresh()
            }
        decreaseDurationButton.isEnabled = !quotaLocked
        increaseDurationButton.isEnabled = !quotaLocked
        root.addView(horizontalButtons(decreaseDurationButton, increaseDurationButton))
        root.addView(primaryButton("确认今天规则") {
            repository.setTodayControlledPackages(selectedPackages)
            if (!quotaLocked) {
                repository.confirmTodayEntertainmentPlan(dailyLimit, durationMinutes)
            }
            dismiss()
            if (selectedPackages.contains(targetPackage)) {
                showIntercept(targetPackage, PageKind.UNKNOWN)
            }
        })
        root.addView(secondaryButton("返回桌面") {
            dismiss()
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        })

        refresh()
        add(root, targetPackage)
    }

    fun showEntertainmentControl(targetPackage: String, remainingMs: Long) {
        if (!Settings.canDrawOverlays(service) || isShowing() || entertainmentControlView != null) return

        val circleSize = dp(60)
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        val edgeMargin = dp(8)
        val maxX = (screenWidth - circleSize - edgeMargin).coerceAtLeast(0)
        val maxY = (screenHeight - circleSize - edgeMargin).coerceAtLeast(0)
        val defaultX = maxX
        val defaultY = dp(96).coerceAtMost(maxY)
        val savedPosition = repository.entertainmentControlPosition(defaultX, defaultY, maxX, maxY)
        val circle = EntertainmentEndCircleView(service)
        var pressStartedAt = 0L
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var triggered = false
        val dragSlop = dp(8).toFloat()
        val dragCancelExtra = dp(12).toFloat()
        var interactionState = FloatingEndState.IDLE
        var pendingX = savedPosition.first
        var pendingY = savedPosition.second
        var frameCallbackPending = false
        var dragStartedAt = 0L
        var dragUpdateCount = 0
        var dragUpdateTotalNanos = 0L

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            circleSize,
            circleSize,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedPosition.first
            y = savedPosition.second
        }

        fun clampX(value: Int): Int = value.coerceIn(0, maxX)
        fun clampY(value: Int): Int = value.coerceIn(0, maxY)

        fun updatePosition(x: Int, y: Int) {
            params.x = clampX(x)
            params.y = clampY(y)
            val startedAtNanos = System.nanoTime()
            runCatching { windowManager.updateViewLayout(circle, params) }
            val costNanos = System.nanoTime() - startedAtNanos
            if (interactionState == FloatingEndState.DRAGGING) {
                dragUpdateCount += 1
                dragUpdateTotalNanos += costNanos
                if (costNanos > FLOAT_UPDATE_WARN_NS) {
                    Log.w(SearchGateFloatPerfLog.TAG, "slow updateViewLayout costMs=${costNanos / 1_000_000.0} x=${params.x} y=${params.y}")
                }
            }
        }

        fun requestDragPosition(x: Int, y: Int) {
            pendingX = clampX(x)
            pendingY = clampY(y)
            if (pendingX == params.x && pendingY == params.y) return
            if (frameCallbackPending) return
            frameCallbackPending = true
            Choreographer.getInstance().postFrameCallback {
                frameCallbackPending = false
                if (interactionState == FloatingEndState.DRAGGING) {
                    updatePosition(pendingX, pendingY)
                }
            }
        }

        fun clearHold(reason: String? = null) {
            entertainmentHoldRunnable?.let { mainHandler.removeCallbacks(it) }
            entertainmentHoldRunnable = null
            pressStartedAt = 0L
            circle.clearHoldProgress()
            circle.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
            if (reason != null) {
                Log.i(SearchGateFloatPerfLog.TAG, "long press cancelled reason=$reason state=$interactionState")
            }
        }

        fun showShortHint() {
            val now = System.currentTimeMillis()
            if (now - lastEntertainmentHintAt < ENTERTAINMENT_HINT_DEBOUNCE_MS) return
            lastEntertainmentHintAt = now
            Toast.makeText(service, "长按结束娱乐", Toast.LENGTH_SHORT).show()
        }

        fun triggerEnd() {
            if (triggered) return
            triggered = true
            interactionState = FloatingEndState.TRIGGERED
            entertainmentHoldRunnable?.let { mainHandler.removeCallbacks(it) }
            entertainmentHoldRunnable = null
            circle.setHoldProgress(1f)
            Toast.makeText(service, "已结束娱乐", Toast.LENGTH_SHORT).show()
            Log.i(FocusGateLog.TAG, "entertainment control action=long_press_end target=$targetPackage")
            Log.i(SearchGateFloatPerfLog.TAG, "long press triggered end target=$targetPackage")
            repository.endEntertainment(PromptFlowState.ENTERTAINMENT_ENDED_BY_USER, targetPackage)
            circle.animate().alpha(0f).setDuration(100L).withEndAction {
                dismissEntertainmentControl()
                showIntercept(targetPackage, PageKind.UNKNOWN)
            }.start()
        }

        fun scheduleHoldProgress() {
            pressStartedAt = System.currentTimeMillis()
            val starter = Runnable {
                if (triggered || dragging || interactionState != FloatingEndState.PRESSING || pressStartedAt == 0L) return@Runnable
                interactionState = FloatingEndState.LONG_PRESS_PROGRESS
                Log.i(SearchGateFloatPerfLog.TAG, "long press progress started target=$targetPackage")
                circle.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120L).start()
                val progressRunnable = object : Runnable {
                    override fun run() {
                        if (triggered || dragging || pressStartedAt == 0L) return
                        val elapsed = System.currentTimeMillis() - pressStartedAt
                        circle.setHoldProgress(elapsed.toFloat() / ENTERTAINMENT_END_HOLD_MS.toFloat())
                        if (elapsed >= ENTERTAINMENT_END_HOLD_MS) {
                            triggerEnd()
                        } else {
                            mainHandler.postDelayed(this, HOLD_PROGRESS_TICK_MS)
                        }
                    }
                }
                entertainmentHoldRunnable = progressRunnable
                mainHandler.post(progressRunnable)
            }
            entertainmentHoldRunnable = starter
            mainHandler.postDelayed(starter, HOLD_PROGRESS_START_DELAY_MS)
        }

        fun snapToEdge() {
            val snapX = if (params.x + circleSize / 2 < screenWidth / 2) 0 else maxX
            updatePosition(snapX, params.y)
            repository.saveEntertainmentControlPosition(params.x, params.y)
            val averageMoveCostMs = if (dragUpdateCount == 0) {
                0.0
            } else {
                (dragUpdateTotalNanos / dragUpdateCount) / 1_000_000.0
            }
            Log.i(SearchGateFloatPerfLog.TAG, "drag ended target=$targetPackage durationMs=${System.currentTimeMillis() - dragStartedAt} updateCalls=$dragUpdateCount avgUpdateMs=$averageMoveCostMs savedOnce=true x=${params.x} y=${params.y}")
            Log.i(FocusGateLog.TAG, "entertainment control dragged target=$targetPackage x=${params.x} y=${params.y}")
        }

        circle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    triggered = false
                    dragging = false
                    interactionState = FloatingEndState.PRESSING
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    pendingX = params.x
                    pendingY = params.y
                    dragUpdateCount = 0
                    dragUpdateTotalNanos = 0L
                    scheduleHoldProgress()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > dragSlop || abs(dy) > dragSlop)) {
                        dragging = true
                        interactionState = FloatingEndState.DRAGGING
                        dragStartedAt = System.currentTimeMillis()
                        clearHold(reason = "drag started")
                        Log.i(SearchGateFloatPerfLog.TAG, "drag started target=$targetPackage x=${params.x} y=${params.y}")
                    }
                    if (dragging) {
                        requestDragPosition(startX + dx.toInt(), startY + dy.toInt())
                    } else {
                        val outside = event.x < -dragCancelExtra ||
                            event.y < -dragCancelExtra ||
                            event.x > view.width + dragCancelExtra ||
                            event.y > view.height + dragCancelExtra
                        if (outside) {
                            interactionState = FloatingEndState.CANCELLED
                            clearHold(reason = "pointer outside")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (triggered) {
                        return@setOnTouchListener true
                    }
                    val wasDragging = dragging
                    dragging = false
                    if (frameCallbackPending && wasDragging) {
                        updatePosition(pendingX, pendingY)
                    }
                    interactionState = if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        FloatingEndState.CANCELLED
                    } else {
                        FloatingEndState.IDLE
                    }
                    clearHold(reason = if (wasDragging) null else "touch ended before trigger")
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (wasDragging) {
                            snapToEdge()
                        } else {
                            view.performClick()
                            showShortHint()
                        }
                    }
                    true
                }
                else -> true
            }
        }

        runCatching {
            windowManager.addView(circle, params)
            entertainmentControlView = circle
            Log.i(FocusGateLog.TAG, "entertainment control circle shown target=$targetPackage remainingMs=$remainingMs x=${params.x} y=${params.y}")
        }.onFailure {
            Log.w(FocusGateLog.TAG, "show entertainment control failed: ${it.javaClass.simpleName}")
        }
    }

    fun showDeepLinkFailure(targetPackage: String) {
        if (!Settings.canDrawOverlays(service)) {
            Log.w(FocusGateLog.TAG, "skip failure overlay because permission missing")
            return
        }
        dismiss()
        dismissEntertainmentControl()

        val platformName = displayName(targetPackage)
        val root = baseLayout()
        root.addView(title("无法确认已进入搜索页"))
        root.addView(body("$platformName 可能没有打开搜索结果页。Deep Link 不稳定，第一版不会静默当作成功。"))
        root.addView(primaryButton("允许手动打开搜索") {
            Log.i(FocusGateLog.TAG, "prompt action=manual_search target=$targetPackage")
            repository.allowManualOpen(targetPackage)
            DeepLinkLauncher.openPackage(service, targetPackage)
            dismiss(clearPromptState = false)
        })
        root.addView(secondaryButton("返回桌面") {
            Log.i(FocusGateLog.TAG, "prompt action=home_after_deeplink_failure target=$targetPackage")
            dismiss()
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        })

        add(root, targetPackage)
    }

    private fun add(view: View, targetPackage: String) {
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            keyCode == KeyEvent.KEYCODE_BACK &&
                (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        runCatching {
            windowManager.addView(view, params)
            blockingView = view
            blockingPackage = targetPackage
            repository.markPromptShowing(targetPackage)
            view.requestFocus()
            Log.i(FocusGateLog.TAG, "overlay shown target=$targetPackage")
        }.onFailure {
            Log.w(FocusGateLog.TAG, "show overlay failed: ${it.javaClass.simpleName}")
            blockingView = null
            blockingPackage = null
        }
    }

    private fun baseLayout(): LinearLayout = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(24), dp(24), dp(24))
        setBackgroundColor(Color.rgb(247, 247, 242))
    }

    private fun title(text: String): TextView = TextView(service).apply {
        this.text = text
        setTextColor(Color.rgb(25, 31, 36))
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(12))
    }

    private fun body(text: String): TextView = TextView(service).apply {
        this.text = text
        setTextColor(Color.rgb(55, 64, 70))
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(20))
    }

    private fun primaryButton(text: String, action: () -> Unit): Button = Button(service).apply {
        this.text = text
        textSize = 17f
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = buttonParams()
    }

    private fun secondaryButton(text: String, action: () -> Unit): Button = primaryButton(text, action)

    private fun horizontalButtons(left: Button, right: Button): LinearLayout =
        LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(left, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                setMargins(0, dp(6), dp(6), dp(6))
            })
            addView(right, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                setMargins(dp(6), dp(6), 0, dp(6))
            })
        }

    private fun buttonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(56)
        ).apply {
            setMargins(0, dp(8), 0, dp(8))
        }

    private fun displayName(packageName: String): String =
        repository.appDisplayName(packageName)

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()

    companion object {
        private const val ENTERTAINMENT_END_HOLD_MS = 1_500L
        private const val HOLD_PROGRESS_TICK_MS = 30L
        private const val HOLD_PROGRESS_START_DELAY_MS = 160L
        private const val ENTERTAINMENT_HINT_DEBOUNCE_MS = 1_000L
        private const val FLOAT_UPDATE_WARN_NS = 8_000_000L
    }
}
