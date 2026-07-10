package com.example.focusgate

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    private lateinit var repository: GuardRepository
    private lateinit var overlayController: OverlayController
    private var lastBlockingOverlayShownAt = 0L
    private var lastBlockingOverlayTargetPackage: String? = null
    private var suppressedTargetPackage: String? = null
    private var suppressTargetEventsUntil = 0L
    private val repeatedScrollGuards = mutableMapOf<String, RepeatedScrollGuard>()
    private val biliRecommendationGuard = BiliRecommendationGuard()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastForegroundPackage: String? = null
    private var lastDouyinPageState = DouyinPageState.DOUYIN_UNKNOWN
    private var lastDouyinPageChangedAt = 0L
    private var lastDouyinAppSwitchAt = 0L
    private var lastDouyinActivityClass: String? = null
    private var lastPermissionHealthCheckAt = 0L
    private val returnRecheckRunnables = mutableMapOf<String, Runnable>()
    private val douyinReturnRecheckRunnables = mutableListOf<Runnable>()

    override fun onServiceConnected() {
        repository = GuardRepository(this)
        overlayController = OverlayController(this, repository)
        Log.i(FocusGateLog.TAG, "accessibility service connected")
        StabilityForegroundService.startIfAllowed(this)
        mainHandler.postDelayed(
            { PermissionAlertCoordinator.evaluate(this, "accessibility_connected") },
            PERMISSION_SERVICE_SETTLE_MS
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            handleAccessibilityEvent(event)
        }.onFailure {
            Log.e(FocusGateLog.TAG, "accessibility event handling failed", it)
        }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::repository.isInitialized || event == null) return

        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == packageName) {
            Log.i(FocusGateLog.TAG, "skip intercept because FocusGate window is focused overlayShowing=${::overlayController.isInitialized && overlayController.isShowing()}")
            return
        }
        if (
            ::overlayController.isInitialized &&
            !overlayController.isShowing() &&
            isSelfActivityFocused()
        ) {
            Log.i(FocusGateLog.TAG, "skip intercept because FocusGate activity is focused")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastPermissionHealthCheckAt >= PERMISSION_HEALTH_CHECK_INTERVAL_MS) {
            lastPermissionHealthCheckAt = now
            PermissionAlertCoordinator.evaluate(this, "accessibility_event")
        }
        if (lastForegroundPackage != foregroundPackage) {
            lastForegroundPackage = foregroundPackage
            if (foregroundPackage == TargetPlatform.DOUYIN.packageName) {
                lastDouyinAppSwitchAt = now
            }
        }
        if (foregroundPackage == TargetPlatform.DOUYIN.packageName) {
            lastDouyinActivityClass = event.className?.toString()
        }
        if (!repository.isGlobalGuardEnabled()) {
            Log.i(SearchGateDevModeLog.TAG, "skip intercept because developer global guard switch is disabled")
            return
        }
        var state = repository.state()
        if (!state.targetPackages.contains(foregroundPackage)) {
            if (overlayController.isShowing()) {
                overlayController.dismiss()
            }
            overlayController.dismissEntertainmentControl()
            repeatedScrollGuards.values.forEach { it.reset() }
            return
        }
        val targetConfig = repository.targetAppConfig(foregroundPackage)

        Log.i(FocusGateLog.TAG, "event type=${event.eventType}, package=$foregroundPackage")

        if (suppressedTargetPackage == foregroundPackage && now < suppressTargetEventsUntil) {
            Log.i(FocusGateLog.TAG, "skip intercept because target handoff is suppressed until=$suppressTargetEventsUntil")
            return
        }
        if (overlayController.isShowing()) {
            Log.i(FocusGateLog.TAG, "skip intercept because overlay showing for target=${overlayController.isShowingFor(foregroundPackage)}")
            return
        }

        if (guardActiveEntertainmentBeforeDecision(foregroundPackage, PageKind.UNKNOWN, now, state, "early guard before debounce and page detection")) {
            return
        }

        val returnCheckActive = repository.isSearchReturnCheckActive(foregroundPackage, now)
        if (targetConfig?.allowStudyLookup == false &&
            (state.searchSessionPackage == foregroundPackage ||
                state.searchReturnCheckPackage == foregroundPackage ||
                TimePolicy.isWithinSearchGrace(state, foregroundPackage, now))
        ) {
            repository.clearSearchSession()
            state = repository.state()
            Log.i(
                SearchGateAppTypeLog.TAG,
                "packageName=${targetConfig.packageName} appName=${targetConfig.appName} appType=${targetConfig.appType} allowStudyLookup=false allowEntertainment=${targetConfig.allowEntertainment} clearedLegacyStudyLookup=true reason=entertainment-only target must not keep study exemption"
            )
        }
        val debounced = TimePolicy.isDebounced(state, foregroundPackage, now)
        if (debounced && !returnCheckActive) {
            if (foregroundPackage == TargetPlatform.DOUYIN.packageName) {
                val pageKind = PageDetector.detect(foregroundPackage, rootInActiveWindow, state.lastKeyword)
                val douyinPageState = PageDetector.detectDouyinPage(rootInActiveWindow, state.lastKeyword)
                observeDouyinPageChange(douyinPageState, now)
                logDouyinDebug(
                    event = event,
                    packageName = foregroundPackage,
                    state = state,
                    now = now,
                    pageKind = pageKind,
                    douyinPageState = effectiveDouyinState(state, foregroundPackage, pageKind, douyinPageState),
                    shouldPrompt = false,
                    reason = "debounce blocked duplicate prompt",
                    debounced = true,
                    cooldownActive = false
                )
            }
            Log.i(FocusGateLog.TAG, "skip intercept because debounce")
            return
        }
        if (!repository.hasTodayControlPlan()) {
            Log.i(FocusGateLog.TAG, "show daily guard plan setup")
            overlayController.showDailyEntertainmentPlanSetup(foregroundPackage)
            return
        }
        if (!repository.isControlledToday(foregroundPackage)) {
            Log.i(FocusGateLog.TAG, "skip intercept because package is not controlled today")
            return
        }
        if (!repository.hasTodayEntertainmentPlan()) {
            Log.i(FocusGateLog.TAG, "show daily entertainment quota setup")
            overlayController.showDailyEntertainmentPlanSetup(foregroundPackage)
            return
        }
        val pageKind = PageDetector.detect(foregroundPackage, rootInActiveWindow, state.lastKeyword)
        val biliPageState = if (foregroundPackage == TargetPlatform.BILI.packageName) {
            PageDetector.detectBiliPage(rootInActiveWindow, state.lastKeyword)
        } else {
            null
        }
        val douyinPageState = if (foregroundPackage == TargetPlatform.DOUYIN.packageName) {
            PageDetector.detectDouyinPage(rootInActiveWindow, state.lastKeyword)
        } else {
            null
        }
        if (douyinPageState != null) {
            observeDouyinPageChange(douyinPageState, now)
        }
        repository.logDecisionSnapshot(foregroundPackage, pageKind, now)
        if (biliPageState != null) {
            val decision = if (pageKind == PageKind.CONTENT_DETAIL) {
                BiliRecommendationDecision(
                    pageState = biliPageState,
                    recommendationJumps = 0,
                    shouldIntercept = false,
                    reason = "pending video detail decision",
                    lastPageChangedAt = biliRecommendationGuard.lastPageChangedAt()
                )
            } else {
                observeBiliPage(
                    pageState = biliPageState,
                    state = state,
                    now = now,
                    allowIntercept = false
                )
            }
            logBiliDebug(event, state, now, pageKind, biliPageState, decision, debounced)
        }
        if (douyinPageState != null) {
            logDouyinDebug(
                event = event,
                packageName = foregroundPackage,
                state = state,
                now = now,
                pageKind = pageKind,
                douyinPageState = effectiveDouyinState(state, foregroundPackage, pageKind, douyinPageState),
                shouldPrompt = false,
                reason = "event observed before decision",
                debounced = debounced,
                cooldownActive = returnCheckActive
            )
        }

        val searchReturnCheckAllowed = targetConfig?.allowStudyLookup != false
        if (searchReturnCheckAllowed && returnCheckActive && handleSearchReturnCheck(foregroundPackage, pageKind, now, state, douyinPageState)) {
            return
        }

        if (shouldInterruptRepeatedScroll(event, foregroundPackage, now, state, pageKind)) {
            repository.endEntertainment(targetPackage = foregroundPackage)
            repository.clearSearchSession()
            repository.markScrollIntervention(foregroundPackage, now)
            showIntercept(foregroundPackage, pageKind)
            return
        }
        if (guardActiveEntertainmentBeforeDecision(foregroundPackage, pageKind, now, state, "guard after repeated scroll before prompt branches")) {
            return
        }
        overlayController.dismissEntertainmentControl()

        if (
            foregroundPackage == TargetPlatform.BILI.packageName &&
            state.searchSessionPackage != foregroundPackage &&
            !TimePolicy.isWithinSearchGrace(state, foregroundPackage, now)
        ) {
            Log.i(FocusGateLog.TAG, "intercept bili foreground after page logging page=$pageKind biliPage=$biliPageState")
            showIntercept(foregroundPackage, pageKind)
            return
        }

        if (targetConfig?.allowStudyLookup == false) {
            val session = repository.entertainmentSessionStatus(foregroundPackage, now)
            val remainingQuota = (state.entertainmentDailyLimit - state.dailyEntertainmentCount).coerceAtLeast(0)
            Log.i(
                SearchGateAppTypeLog.TAG,
                "packageName=${targetConfig.packageName} appName=${targetConfig.appName} appType=${targetConfig.appType} allowStudyLookup=${targetConfig.allowStudyLookup} allowEntertainment=${targetConfig.allowEntertainment} isEntertainmentActive=${session.isEntertainmentActive} remainingEntertainmentMillis=${session.remainingMillis} todayQuotaRemaining=$remainingQuota shouldShowPrompt=true promptButtons=entertainment,home hideStudyReason=entertainment-only target"
            )
            showIntercept(foregroundPackage, pageKind)
            return
        }

        if (state.searchSessionPackage == foregroundPackage && state.lastDeepLinkResult == DeepLinkResult.STARTED) {
            if (pageKind == PageKind.SEARCH_RESULTS || pageKind == PageKind.CONTENT_DETAIL) {
                repository.markSearchVerified()
                repository.refreshVerifiedSearchSession(now)
                if (foregroundPackage == TargetPlatform.BILI.packageName) {
                    if (pageKind == PageKind.SEARCH_RESULTS) {
                        observeBiliPage(BiliPageState.SEARCH_RESULT, state, now, allowIntercept = false)
                    } else {
                        observeBiliPage(biliPageState ?: BiliPageState.VIDEO_DETAIL, state, now, allowIntercept = false)
                    }
                }
                Log.i(FocusGateLog.TAG, "skip intercept because deep link verified search flow")
                return
            }
            if (pageKind == PageKind.HOME && now - state.searchStartedAt >= GuardRepository.DEEP_LINK_VERIFY_MS) {
                repository.markStartedButNotVerified()
                repository.markIntercept(foregroundPackage, now)
                Log.i(FocusGateLog.TAG, "deep link result = STARTED_BUT_NOT_VERIFIED")
                overlayController.showDeepLinkFailure(foregroundPackage)
                return
            }
            if (pageKind == PageKind.UNKNOWN && now >= state.searchGraceUntil) {
                repository.clearSearchSession()
                Log.i(FocusGateLog.TAG, "deep link unknown after grace; clear search session and show prompt")
                showIntercept(foregroundPackage, pageKind)
                return
            }
            Log.i(FocusGateLog.TAG, "skip intercept because waiting for deep link verification")
            return
        }

        if (state.searchSessionPackage == foregroundPackage && state.lastDeepLinkResult == DeepLinkResult.VERIFIED_SEARCH) {
            when (pageKind) {
                PageKind.HOME -> {
                    repository.clearSearchSession()
                    biliRecommendationGuard.reset()
                    showIntercept(foregroundPackage, pageKind)
                }
                PageKind.SEARCH_RESULTS -> {
                    if (foregroundPackage == TargetPlatform.BILI.packageName) {
                        observeBiliPage(BiliPageState.SEARCH_RESULT, state, now, allowIntercept = false)
                    }
                    repository.refreshVerifiedSearchSession(now)
                    Log.i(FocusGateLog.TAG, "skip intercept because verified search session")
                }
                PageKind.CONTENT_DETAIL -> {
                    if (shouldInterruptBiliRecommendationJump(foregroundPackage, biliPageState ?: BiliPageState.VIDEO_DETAIL, state, now, event)) {
                        repository.clearSearchSession()
                        showIntercept(foregroundPackage, pageKind)
                        return
                    }
                    repository.refreshVerifiedSearchSession(now)
                    Log.i(FocusGateLog.TAG, "skip intercept because verified search session")
                }
                PageKind.UNKNOWN -> {
                    if (
                        foregroundPackage == TargetPlatform.XHS.packageName &&
                        repository.isSearchReturnCheckActive(foregroundPackage, now) &&
                        now - state.searchStartedAt >= GuardRepository.SEARCH_RETURN_UNKNOWN_RECHECK_MS
                    ) {
                        repository.clearSearchSession()
                        Log.i(FocusGateLog.TAG, "xhs verified search returned as UNKNOWN; show prompt instead of long allow")
                        showIntercept(foregroundPackage, pageKind)
                    } else {
                        Log.i(FocusGateLog.TAG, "skip intercept because verified search session unknown page")
                    }
                }
            }
            return
        }

        if (
            state.searchSessionPackage == foregroundPackage &&
            state.lastDeepLinkResult == DeepLinkResult.STARTED_BUT_NOT_VERIFIED
        ) {
            when (pageKind) {
                PageKind.HOME -> {
                    repository.clearSearchSession()
                    biliRecommendationGuard.reset()
                    showIntercept(foregroundPackage, pageKind)
                }
                PageKind.SEARCH_RESULTS,
                PageKind.CONTENT_DETAIL,
                PageKind.UNKNOWN -> {
                    repository.refreshSearchSession(now)
                    Log.i(FocusGateLog.TAG, "skip intercept because unverified search session")
                }
            }
            return
        }

        if (TimePolicy.isWithinSearchGrace(state, foregroundPackage, now)) {
            if (pageKind == PageKind.HOME) {
                showIntercept(foregroundPackage, pageKind)
            } else {
                Log.i(FocusGateLog.TAG, "skip intercept because search grace")
            }
            return
        }

        if (pageKind == PageKind.SEARCH_RESULTS) {
            if (foregroundPackage == TargetPlatform.BILI.packageName) {
                observeBiliPage(BiliPageState.SEARCH_RESULT, state, now, allowIntercept = false)
            }
            repository.markSearchPageObserved(foregroundPackage, now)
            Log.i(FocusGateLog.TAG, "skip intercept because search results")
            return
        }

        if (douyinPageState != null) {
            logDouyinDebug(
                event = event,
                packageName = foregroundPackage,
                state = state,
                now = now,
                pageKind = pageKind,
                douyinPageState = effectiveDouyinState(state, foregroundPackage, pageKind, douyinPageState),
                shouldPrompt = true,
                reason = "default target decision requires prompt",
                debounced = false,
                cooldownActive = false
            )
        }
        showIntercept(foregroundPackage, pageKind)
    }

    override fun onInterrupt() {
        Log.w(FocusGateLog.TAG, "accessibility service interrupted")
        cancelPendingRechecks()
        PermissionAlertCoordinator.evaluate(this, "accessibility_interrupted", includeForegroundService = false)
        if (::overlayController.isInitialized) {
            overlayController.dismiss()
            overlayController.dismissEntertainmentControl()
        }
    }

    override fun onDestroy() {
        Log.w(FocusGateLog.TAG, "accessibility service destroyed")
        cancelPendingRechecks()
        if (::overlayController.isInitialized) {
            overlayController.dismiss()
            overlayController.dismissEntertainmentControl()
        }
        PermissionAlertCoordinator.evaluate(this, "accessibility_destroyed", includeForegroundService = false)
        super.onDestroy()
    }

    private fun showIntercept(packageName: String, pageKind: PageKind) {
        val nowMillis = System.currentTimeMillis()
        val state = repository.state()
        val session = repository.entertainmentSessionStatus(packageName, nowMillis)
        if (session.isEntertainmentActive) {
            repository.targetAppConfig(packageName)?.let { config ->
                val remainingQuota = (state.entertainmentDailyLimit - state.dailyEntertainmentCount).coerceAtLeast(0)
                Log.i(
                    SearchGateAppTypeLog.TAG,
                    "packageName=${config.packageName} appName=${config.appName} appType=${config.appType} allowStudyLookup=${config.allowStudyLookup} allowEntertainment=${config.allowEntertainment} isEntertainmentActive=true remainingEntertainmentMillis=${session.remainingMillis} todayQuotaRemaining=$remainingQuota shouldShowPrompt=false promptButtons=none hideStudyReason=${if (config.allowStudyLookup) "-" else "appType=${config.appType}"}"
                )
            }
            logTimerDecision(
                packageName = packageName,
                pageKind = pageKind,
                state = state,
                session = session,
                allowPrompt = false,
                reason = "blocked actual showIntercept because entertainment active"
            )
            overlayController.showEntertainmentControl(packageName, session.remainingMillis)
            return
        }
        logTimerDecision(
            packageName = packageName,
            pageKind = pageKind,
            state = state,
            session = session,
            allowPrompt = true,
            reason = "showIntercept allowed"
        )
        repository.markIntercept(packageName)
        Log.i(FocusGateLog.TAG, "show overlay reason = HOME_OR_UNKNOWN")
        overlayController.showIntercept(packageName, pageKind)
        lastBlockingOverlayShownAt = nowMillis
        lastBlockingOverlayTargetPackage = packageName
    }

    fun suppressTargetEvents(packageName: String, durationMs: Long) {
        suppressedTargetPackage = packageName
        suppressTargetEventsUntil = System.currentTimeMillis() + durationMs
        Log.i(FocusGateLog.TAG, "suppress target events package=$packageName durationMs=$durationMs")
    }

    private fun guardActiveEntertainmentBeforeDecision(
        packageName: String,
        pageKind: PageKind,
        nowMillis: Long,
        state: GuardState,
        reason: String
    ): Boolean {
        val session = repository.entertainmentSessionStatus(packageName, nowMillis)
        if (session.isEntertainmentActive) {
            logTimerDecision(
                packageName = packageName,
                pageKind = pageKind,
                state = state,
                session = session,
                allowPrompt = false,
                reason = "$reason; entertainment active"
            )
            overlayController.showEntertainmentControl(
                targetPackage = packageName,
                remainingMs = session.remainingMillis
            )
            return true
        }
        if (session.anomaly != null) {
            logTimerDecision(
                packageName = packageName,
                pageKind = pageKind,
                state = state,
                session = session,
                allowPrompt = true,
                reason = "$reason; timer anomaly=${session.anomaly}"
            )
        }
        return false
    }

    private fun logTimerDecision(
        packageName: String,
        pageKind: PageKind,
        state: GuardState,
        session: EntertainmentSessionStatus,
        allowPrompt: Boolean,
        reason: String
    ) {
        Log.i(
            SearchGateTimerLog.TAG,
            "nowMillis=${session.nowMillis} entertainmentStartAtMillis=${session.entertainmentStartAtMillis} entertainmentEndAtMillis=${session.entertainmentEndAtMillis} entertainmentDurationMillis=${session.entertainmentDurationMillis} remainingMillis=${session.remainingMillis} isEntertainmentActive=${session.isEntertainmentActive} sessionPackage=${session.packageName ?: "-"} target=$packageName page=$pageKind promptState=${state.promptFlowState} allowPrompt=$allowPrompt reason=$reason anomaly=${session.anomaly ?: "-"}"
        )
    }

    private fun handleSearchReturnCheck(
        packageName: String,
        pageKind: PageKind,
        now: Long,
        state: GuardState,
        douyinPageState: DouyinPageState? = null
    ): Boolean {
        if (packageName == TargetPlatform.DOUYIN.packageName) {
            return handleDouyinSearchReturnCheck(
                packageName = packageName,
                pageKind = pageKind,
                douyinPageState = douyinPageState ?: PageDetector.detectDouyinPage(rootInActiveWindow, state.lastKeyword),
                now = now,
                state = state
            )
        }
        Log.i(FocusGateLog.TAG, "search return check package=$packageName page=$pageKind")
        val session = repository.entertainmentSessionStatus(packageName, now)
        if (session.isEntertainmentActive) {
            repository.clearSearchReturnCheck()
            overlayController.showEntertainmentControl(
                targetPackage = packageName,
                remainingMs = session.remainingMillis
            )
            logTimerDecision(
                packageName = packageName,
                pageKind = pageKind,
                state = state,
                session = session,
                allowPrompt = false,
                reason = "search return check skipped because entertainment active"
            )
            Log.i(FocusGateLog.TAG, "search return check skipped because entertainment remains active")
            return true
        }
        return when (pageKind) {
            PageKind.SEARCH_RESULTS,
            PageKind.CONTENT_DETAIL -> {
                repository.markSearchVerified()
                repository.refreshVerifiedSearchSession(now)
                scheduleReturnRecheck(packageName)
                Log.i(FocusGateLog.TAG, "search return check sees search/content page; keep search session")
                true
            }
            PageKind.HOME -> {
                repository.clearSearchSession()
                Log.i(FocusGateLog.TAG, "search return check sees home; show prompt")
                showIntercept(packageName, pageKind)
                true
            }
            PageKind.UNKNOWN -> {
                val waitedMs = now - state.searchStartedAt
                if (waitedMs >= GuardRepository.SEARCH_RETURN_UNKNOWN_RECHECK_MS) {
                    repository.clearSearchSession()
                    Log.i(FocusGateLog.TAG, "search return check unknown waitedMs=$waitedMs; show prompt")
                    showIntercept(packageName, pageKind)
                    true
                } else {
                    scheduleReturnRecheck(packageName)
                    Log.i(FocusGateLog.TAG, "search return check waits for stable page waitedMs=$waitedMs")
                    true
                }
            }
        }
    }

    private fun handleDouyinSearchReturnCheck(
        packageName: String,
        pageKind: PageKind,
        douyinPageState: DouyinPageState,
        now: Long,
        state: GuardState
    ): Boolean {
        val effectiveState = effectiveDouyinState(state, packageName, pageKind, douyinPageState)
        logDouyinDebug(
            event = null,
            packageName = packageName,
            state = state,
            now = now,
            pageKind = pageKind,
            douyinPageState = effectiveState,
            shouldPrompt = false,
            reason = "return check evaluating",
            debounced = false,
            cooldownActive = true
        )
        val session = repository.entertainmentSessionStatus(packageName, now)
        if (session.isEntertainmentActive) {
            repository.clearSearchReturnCheck()
            overlayController.showEntertainmentControl(
                targetPackage = packageName,
                remainingMs = session.remainingMillis
            )
            logTimerDecision(
                packageName = packageName,
                pageKind = pageKind,
                state = state,
                session = session,
                allowPrompt = false,
                reason = "douyin return check skipped because entertainment active"
            )
            logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, false, "return check skipped because entertainment remains active", false, true)
            return true
        }

        return when (effectiveState) {
            DouyinPageState.DOUYIN_SEARCH_PAGE,
            DouyinPageState.DOUYIN_SEARCH_RESULT,
            DouyinPageState.DOUYIN_VIDEO_FROM_SEARCH,
            DouyinPageState.DOUYIN_VIDEO_DETAIL,
            DouyinPageState.DOUYIN_COMMENT_OPEN,
            DouyinPageState.DOUYIN_USER_PROFILE,
            DouyinPageState.DOUYIN_WEBVIEW,
            DouyinPageState.DOUYIN_SHOP_OR_LIVE -> {
                repository.markSearchVerified()
                repository.refreshVerifiedSearchSession(now)
                scheduleDouyinReturnRechecks(packageName)
                logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, false, "study lookup still active; keep exemption and schedule bounded recheck", false, true)
                true
            }
            DouyinPageState.DOUYIN_HOME_RECOMMEND,
            DouyinPageState.DOUYIN_RETURNED_FROM_STUDY_LOOKUP -> {
                repository.clearSearchSession()
                logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, true, "returned to Douyin home after study lookup; clear exemption and prompt", false, true)
                showIntercept(packageName, pageKind)
                true
            }
            DouyinPageState.DOUYIN_RECHECK_PENDING,
            DouyinPageState.DOUYIN_UNKNOWN -> {
                scheduleDouyinReturnRechecks(packageName)
                logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, false, "unknown Douyin page; wait for bounded recheck instead of blind prompt", false, true)
                true
            }
            DouyinPageState.DOUYIN_PROMPT_SHOWING,
            DouyinPageState.DOUYIN_ENTERTAINMENT_ACTIVE,
            DouyinPageState.DOUYIN_COOLDOWN,
            DouyinPageState.DOUYIN_QUOTA_EXHAUSTED -> {
                logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, false, "state blocks duplicate prompt", false, true)
                true
            }
        }
    }

    private fun scheduleReturnRecheck(packageName: String) {
        returnRecheckRunnables.remove(packageName)?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            returnRecheckRunnables.remove(packageName)
            val root = rootInActiveWindow
            val currentPackage = root?.packageName?.toString()
            if (currentPackage != packageName || overlayController.isShowing()) return@Runnable
            val now = System.currentTimeMillis()
            val state = repository.state()
            if (!repository.isSearchReturnCheckActive(packageName, now)) return@Runnable
            val pageKind = PageDetector.detect(packageName, root, state.lastKeyword)
            repository.logDecisionSnapshot(packageName, pageKind, now)
            handleSearchReturnCheck(packageName, pageKind, now, state)
        }
        returnRecheckRunnables[packageName] = runnable
        mainHandler.postDelayed(runnable, RETURN_RECHECK_DELAY_MS)
    }

    private fun scheduleDouyinReturnRechecks(packageName: String) {
        douyinReturnRecheckRunnables.forEach { mainHandler.removeCallbacks(it) }
        douyinReturnRecheckRunnables.clear()
        DOUYIN_RECHECK_DELAYS_MS.forEachIndexed { index, delayMs ->
            val runnable = object : Runnable {
                override fun run() {
                    douyinReturnRecheckRunnables.remove(this)
                    runDouyinReturnRecheck(packageName, index + 1)
                }
            }
            douyinReturnRecheckRunnables += runnable
            mainHandler.postDelayed(runnable, delayMs)
        }
        Log.i(SearchGateDouyinLog.TAG, "scheduled bounded Douyin return recheck package=$packageName delays=${DOUYIN_RECHECK_DELAYS_MS.joinToString()}")
    }

    private fun cancelPendingRechecks() {
        returnRecheckRunnables.values.forEach { mainHandler.removeCallbacks(it) }
        returnRecheckRunnables.clear()
        douyinReturnRecheckRunnables.forEach { mainHandler.removeCallbacks(it) }
        douyinReturnRecheckRunnables.clear()
    }

    private fun runDouyinReturnRecheck(packageName: String, attempt: Int) {
        val root = rootInActiveWindow
        val currentPackage = root?.packageName?.toString()
        if (currentPackage != packageName || overlayController.isShowing()) return
        val now = System.currentTimeMillis()
        val state = repository.state()
        if (!repository.isSearchReturnCheckActive(packageName, now)) return

        val pageKind = PageDetector.detect(packageName, root, state.lastKeyword)
        val detectedState = PageDetector.detectDouyinPage(root, state.lastKeyword)
        observeDouyinPageChange(detectedState, now)
        val effectiveState = effectiveDouyinState(state, packageName, pageKind, detectedState)
        repository.logDecisionSnapshot(packageName, pageKind, now)

        val session = repository.entertainmentSessionStatus(packageName, now)
        if (session.isEntertainmentActive) {
            repository.clearSearchReturnCheck()
            overlayController.showEntertainmentControl(
                targetPackage = packageName,
                remainingMs = session.remainingMillis
            )
            logTimerDecision(
                packageName = packageName,
                pageKind = pageKind,
                state = state,
                session = session,
                allowPrompt = false,
                reason = "douyin bounded recheck skipped because entertainment active"
            )
            logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, false, "recheck#$attempt skipped because entertainment remains active", false, true)
            return
        }

        when (effectiveState) {
            DouyinPageState.DOUYIN_HOME_RECOMMEND,
            DouyinPageState.DOUYIN_RETURNED_FROM_STUDY_LOOKUP -> {
                repository.clearSearchSession()
                logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, true, "recheck#$attempt sees home after study; clear exemption and prompt", false, true)
                showIntercept(packageName, pageKind)
            }
            DouyinPageState.DOUYIN_SEARCH_PAGE,
            DouyinPageState.DOUYIN_SEARCH_RESULT,
            DouyinPageState.DOUYIN_VIDEO_FROM_SEARCH,
            DouyinPageState.DOUYIN_VIDEO_DETAIL,
            DouyinPageState.DOUYIN_COMMENT_OPEN,
            DouyinPageState.DOUYIN_USER_PROFILE,
            DouyinPageState.DOUYIN_WEBVIEW,
            DouyinPageState.DOUYIN_SHOP_OR_LIVE -> {
                repository.markSearchVerified()
                repository.refreshVerifiedSearchSession(now)
                logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, false, "recheck#$attempt still in study/search related page", false, true)
            }
            DouyinPageState.DOUYIN_RECHECK_PENDING,
            DouyinPageState.DOUYIN_UNKNOWN -> {
                if (attempt >= DOUYIN_RECHECK_DELAYS_MS.size) {
                    repository.clearSearchSession()
                    logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, false, "recheck#$attempt final unknown; clear study exemption without blind prompt", false, true)
                } else {
                    logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, false, "recheck#$attempt unknown; wait for next bounded recheck", false, true)
                }
            }
            DouyinPageState.DOUYIN_PROMPT_SHOWING,
            DouyinPageState.DOUYIN_ENTERTAINMENT_ACTIVE,
            DouyinPageState.DOUYIN_COOLDOWN,
            DouyinPageState.DOUYIN_QUOTA_EXHAUSTED -> {
                logDouyinDebug(null, packageName, state, now, pageKind, effectiveState, false, "recheck#$attempt state blocks duplicate prompt", false, true)
            }
        }
    }

    private fun shouldInterruptRepeatedScroll(
        event: AccessibilityEvent,
        packageName: String,
        now: Long,
        state: GuardState,
        pageKind: PageKind
    ): Boolean {
        if (!usesRepeatedScrollGuard(packageName)) return false
        val session = repository.entertainmentSessionStatus(packageName, now)
        if (session.isEntertainmentActive) {
            logTimerDecision(
                packageName = packageName,
                pageKind = pageKind,
                state = state,
                session = session,
                allowPrompt = false,
                reason = "repeated scroll guard suppressed during entertainment"
            )
            return false
        }
        if (repository.isInScrollInterventionCooldown(packageName, now)) return false
        if (state.searchSessionPackage == packageName) return false
        if (TimePolicy.isWithinSearchGrace(state, packageName, now)) return false
        if (pageKind == PageKind.SEARCH_RESULTS) return false

        val count = scrollGuardFor(packageName).observe(event, now)
        if (count <= 0) return false

        val threshold = repository.repeatedScrollThreshold()
        Log.i(FocusGateLog.TAG, "repeated scroll count = $count, package = $packageName")
        return count >= threshold
    }

    private fun shouldInterruptBiliRecommendationJump(
        packageName: String,
        pageState: BiliPageState,
        state: GuardState,
        now: Long,
        event: AccessibilityEvent
    ): Boolean {
        if (packageName != TargetPlatform.BILI.packageName) return false
        val decision = observeBiliPage(pageState, state, now, allowIntercept = true)
        logBiliDebug(event, state, now, PageKind.CONTENT_DETAIL, pageState, decision, false)
        Log.i(FocusGateLog.TAG, "bili recommendation jump count = ${decision.recommendationJumps}, reason=${decision.reason}")
        if (!decision.shouldIntercept) return false

        biliRecommendationGuard.reset()
        Log.i(FocusGateLog.TAG, "interrupt because bili recommendation jumps exceeded")
        return true
    }

    private fun observeBiliPage(
        pageState: BiliPageState,
        state: GuardState,
        now: Long,
        allowIntercept: Boolean
    ): BiliRecommendationDecision {
        val isStudyLookupActive = state.searchSessionPackage == TargetPlatform.BILI.packageName ||
            TimePolicy.isWithinSearchGrace(state, TargetPlatform.BILI.packageName, now)
        val isEntertainmentActive = TimePolicy.isWithinEntertainmentWindow(state, TargetPlatform.BILI.packageName, now)
        return biliRecommendationGuard.observe(
            pageState = pageState,
            signature = PageDetector.pageSignature(rootInActiveWindow),
            jumpLimit = if (allowIntercept) repository.biliRecommendationJumpLimit() else Int.MAX_VALUE,
            isStudyLookupActive = isStudyLookupActive,
            isEntertainmentActive = isEntertainmentActive,
            now = now
        )
    }

    private fun logBiliDebug(
        event: AccessibilityEvent,
        state: GuardState,
        now: Long,
        pageKind: PageKind,
        biliPageState: BiliPageState,
        decision: BiliRecommendationDecision,
        debounced: Boolean
    ) {
        val isEntertainmentActive = TimePolicy.isWithinEntertainmentWindow(state, TargetPlatform.BILI.packageName, now)
        val isStudyLookupActive = state.searchSessionPackage == TargetPlatform.BILI.packageName ||
            TimePolicy.isWithinSearchGrace(state, TargetPlatform.BILI.packageName, now)
        Log.i(
            SearchGateBiliLog.TAG,
            "package=${event.packageName} activity=${event.className} eventType=${event.eventType} pageKind=$pageKind biliState=$biliPageState keywords=${PageDetector.keywordSummary(rootInActiveWindow)} text=${PageDetector.textSummary(rootInActiveWindow)} recommendJump=${decision.shouldIntercept} reason=${decision.reason} jumps=${decision.recommendationJumps}/${repository.biliRecommendationJumpLimit()} entertainment=$isEntertainmentActive studyLookup=$isStudyLookupActive searchSession=${state.searchSessionPackage}/${state.lastDeepLinkResult} lastPageChangedAt=${decision.lastPageChangedAt} lastPromptAt=${state.lastInterceptTime} debounced=$debounced"
        )
    }

    private fun observeDouyinPageChange(pageState: DouyinPageState, now: Long) {
        if (pageState != lastDouyinPageState) {
            lastDouyinPageState = pageState
            lastDouyinPageChangedAt = now
        }
    }

    private fun effectiveDouyinState(
        state: GuardState,
        packageName: String,
        pageKind: PageKind,
        pageState: DouyinPageState
    ): DouyinPageState {
        val studyLookupActive = state.searchSessionPackage == packageName ||
            state.searchReturnCheckPackage == packageName
        return if (
            packageName == TargetPlatform.DOUYIN.packageName &&
            studyLookupActive &&
            (
                pageKind == PageKind.HOME ||
                    pageState == DouyinPageState.DOUYIN_HOME_RECOMMEND ||
                    (isDouyinMainActivity(lastDouyinActivityClass) && pageState !in DOUYIN_STUDY_PAGE_STATES)
                )
        ) {
            DouyinPageState.DOUYIN_RETURNED_FROM_STUDY_LOOKUP
        } else if (
            packageName == TargetPlatform.DOUYIN.packageName &&
            studyLookupActive &&
            pageState == DouyinPageState.DOUYIN_VIDEO_DETAIL
        ) {
            DouyinPageState.DOUYIN_VIDEO_FROM_SEARCH
        } else {
            pageState
        }
    }

    private fun isDouyinMainActivity(activityClass: String?): Boolean =
        activityClass?.contains(".main.", ignoreCase = true) == true

    private fun logDouyinDebug(
        event: AccessibilityEvent?,
        packageName: String,
        state: GuardState,
        now: Long,
        pageKind: PageKind,
        douyinPageState: DouyinPageState,
        shouldPrompt: Boolean,
        reason: String,
        debounced: Boolean,
        cooldownActive: Boolean
    ) {
        val session = repository.entertainmentSessionStatus(packageName, now)
        val isEntertainmentActive = session.isEntertainmentActive
        val remainingEntertainmentMs = session.remainingMillis
        val isStudyLookupActive = state.searchSessionPackage == packageName ||
            TimePolicy.isWithinSearchGrace(state, packageName, now) ||
            repository.isSearchReturnCheckActive(packageName, now)
        val returnedFromStudy = douyinPageState == DouyinPageState.DOUYIN_RETURNED_FROM_STUDY_LOOKUP
        val quotaExhausted = state.todayQuotaConfirmed &&
            state.entertainmentDailyLimit > 0 &&
            state.dailyEntertainmentCount >= state.entertainmentDailyLimit
        Log.i(
            SearchGateDouyinLog.TAG,
            "nowMillis=$now package=${event?.packageName ?: packageName} activity=${event?.className ?: lastDouyinActivityClass ?: "-"} eventType=${event?.eventType ?: "recheck"} pageKind=$pageKind douyinState=$douyinPageState signals=${PageDetector.douyinSignalSummary(rootInActiveWindow, state.lastKeyword)} text=${PageDetector.textSummary(rootInActiveWindow)} keywords=${PageDetector.keywordSummary(rootInActiveWindow)} studyLookup=$isStudyLookupActive returnedFromStudy=$returnedFromStudy recheckPending=${repository.isSearchReturnCheckActive(packageName, now)} entertainment=$isEntertainmentActive remainingEntertainmentMs=$remainingEntertainmentMs quotaExhausted=$quotaExhausted shouldPrompt=$shouldPrompt reason=$reason lastPromptAt=${state.lastInterceptTime} lastAppSwitchAt=$lastDouyinAppSwitchAt lastDouyinPageChangedAt=$lastDouyinPageChangedAt debounced=$debounced cooldownActive=$cooldownActive scrollCooldown=${repository.isInScrollInterventionCooldown(packageName, now)} promptState=${state.promptFlowState} searchSession=${state.searchSessionPackage}/${state.lastDeepLinkResult} timerAnomaly=${session.anomaly ?: "-"}"
        )
    }

    private fun hasTargetWindowBehindOverlay(targetPackages: Set<String>): Boolean {
        val visiblePackages = windows
            .mapNotNull { it.root?.packageName?.toString() }
            .toSet()
        if (visiblePackages.isEmpty() || visiblePackages == setOf(packageName)) return true
        return visiblePackages.any { it in targetPackages || it == lastBlockingOverlayTargetPackage }
    }

    private fun isSelfActivityFocused(): Boolean =
        windows.any { window ->
            window.isFocused && window.root?.packageName?.toString() == packageName
        }

    private fun usesRepeatedScrollGuard(packageName: String): Boolean =
        packageName == TargetPlatform.DOUYIN.packageName || packageName == TargetPlatform.XHS.packageName

    private fun scrollGuardFor(packageName: String): RepeatedScrollGuard =
        repeatedScrollGuards.getOrPut(packageName) {
            RepeatedScrollGuard(
                windowMs = REPEATED_SCROLL_WINDOW_MS,
                minIntervalMs = REPEATED_SCROLL_MIN_INTERVAL_MS
            )
        }

    companion object {
        private const val REPEATED_SCROLL_WINDOW_MS = 45_000L
        private const val REPEATED_SCROLL_MIN_INTERVAL_MS = 650L
        private const val RETURN_RECHECK_DELAY_MS = 900L
        private const val PERMISSION_HEALTH_CHECK_INTERVAL_MS = 10_000L
        private const val PERMISSION_SERVICE_SETTLE_MS = 500L
        private val DOUYIN_RECHECK_DELAYS_MS = longArrayOf(300L, 800L, 1_500L, 2_500L)
        private val DOUYIN_STUDY_PAGE_STATES = setOf(
            DouyinPageState.DOUYIN_SEARCH_PAGE,
            DouyinPageState.DOUYIN_SEARCH_RESULT,
            DouyinPageState.DOUYIN_VIDEO_FROM_SEARCH,
            DouyinPageState.DOUYIN_COMMENT_OPEN,
            DouyinPageState.DOUYIN_USER_PROFILE,
            DouyinPageState.DOUYIN_WEBVIEW,
            DouyinPageState.DOUYIN_SHOP_OR_LIVE
        )
    }
}
