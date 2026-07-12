package com.example.focusgate

data class DeveloperCenterSnapshot(
    val developerModeEnabled: Boolean,
    val globalGuardEnabled: Boolean,
    val todayQuotaConfirmed: Boolean,
    val dailyLimit: Int,
    val usedCount: Int,
    val durationMinutes: Int,
    val entertainmentActive: Boolean,
    val entertainmentStartedAt: Long,
    val entertainmentUntil: Long,
    val currentAppName: String?,
    val lastTargetPackage: String?,
    val currentAppType: String?,
    val currentRestricted: Boolean,
    val pageRecognition: String,
    val promptShowing: Boolean,
    val debounceState: String,
    val promptState: String,
    val promptPackage: String?,
    val searchState: String,
    val targetCount: Int,
    val builtInCount: Int,
    val userAddedCount: Int,
    val todayAddedCount: Int,
    val removableTodayCount: Int,
    val entertainmentOnlyCount: Int,
    val hybridCount: Int,
    val limits: DeveloperQuotaLimits
) {
    val remainingCount: Int get() = (dailyLimit - usedCount).coerceAtLeast(0)
}

object DeveloperCenterPolicy {
    fun isEntryVisible(developerModeEnabled: Boolean): Boolean = developerModeEnabled

    fun snapshot(
        state: GuardState,
        configs: List<TargetAppConfig>,
        limits: DeveloperQuotaLimits,
        developerModeEnabled: Boolean,
        globalGuardEnabled: Boolean,
        today: String,
        nowMillis: Long
    ): DeveloperCenterSnapshot {
        val enabled = configs.filter(TargetAppConfig::isEnabled)
        val currentConfig = configs.firstOrNull { it.packageName == state.lastTargetPackage }
        return DeveloperCenterSnapshot(
            developerModeEnabled = developerModeEnabled,
            globalGuardEnabled = globalGuardEnabled,
            todayQuotaConfirmed = state.todayQuotaConfirmed,
            dailyLimit = state.entertainmentDailyLimit,
            usedCount = state.dailyEntertainmentCount,
            durationMinutes = (state.entertainmentDurationMs / 60_000L).toInt(),
            entertainmentActive = state.entertainmentPackage != null && nowMillis < state.entertainmentUntil,
            entertainmentStartedAt = state.entertainmentStartedAt,
            entertainmentUntil = state.entertainmentUntil,
            currentAppName = currentConfig?.appName,
            lastTargetPackage = state.lastTargetPackage,
            currentAppType = currentConfig?.appType?.name,
            currentRestricted = currentConfig?.isEnabled == true,
            pageRecognition = state.lastKeyword ?: state.lastDeepLinkResult.name,
            promptShowing = state.promptPackage != null,
            debounceState = if (state.lastInterceptTime > 0L) "最近触发 ${state.lastInterceptTime}" else "空闲",
            promptState = state.promptFlowState.name,
            promptPackage = state.promptPackage,
            searchState = "${state.searchSessionPackage ?: "-"} / ${state.lastDeepLinkResult.name}",
            targetCount = enabled.size,
            builtInCount = enabled.count { it.source == TargetAppSource.BUILT_IN },
            userAddedCount = enabled.count { it.source == TargetAppSource.USER_ADDED },
            todayAddedCount = enabled.count { it.source == TargetAppSource.USER_ADDED && it.addedDate == today },
            removableTodayCount = enabled.count { TargetAppLockPolicy.canRemoveUserTarget(it, today) },
            entertainmentOnlyCount = enabled.count { it.appType == TargetAppType.ENTERTAINMENT_ONLY },
            hybridCount = enabled.count { it.appType == TargetAppType.HYBRID_STUDY_ENTERTAINMENT },
            limits = limits
        )
    }
}
