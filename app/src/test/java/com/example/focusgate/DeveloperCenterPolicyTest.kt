package com.example.focusgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperCenterPolicyTest {
    @Test
    fun entryVisibilityFollowsPersistentMode() {
        assertTrue(DeveloperCenterPolicy.isEntryVisible(true))
        assertFalse(DeveloperCenterPolicy.isEntryVisible(false))
    }

    @Test
    fun snapshotSeparatesAppTypesAndDailyLocks() {
        val snapshot = DeveloperCenterPolicy.snapshot(
            state = state(),
            configs = listOf(
                config("com.xingin.xhs", TargetAppType.HYBRID_STUDY_ENTERTAINMENT, TargetAppSource.BUILT_IN),
                config("tv.danmaku.bili", TargetAppType.HYBRID_STUDY_ENTERTAINMENT, TargetAppSource.BUILT_IN),
                config("com.ss.android.ugc.aweme", TargetAppType.ENTERTAINMENT_ONLY, TargetAppSource.BUILT_IN),
                config("com.example.user", TargetAppType.ENTERTAINMENT_ONLY, TargetAppSource.USER_ADDED, "2026-07-12", "2026-07-13")
            ),
            limits = DeveloperQuotaLimits(10, 15),
            developerModeEnabled = true,
            globalGuardEnabled = true,
            today = "2026-07-12",
            nowMillis = 1_500L
        )
        assertEquals(4, snapshot.targetCount)
        assertEquals(3, snapshot.builtInCount)
        assertEquals(1, snapshot.userAddedCount)
        assertEquals(1, snapshot.todayAddedCount)
        assertEquals(0, snapshot.removableTodayCount)
        assertEquals(2, snapshot.hybridCount)
        assertEquals(2, snapshot.entertainmentOnlyCount)
        assertTrue(snapshot.entertainmentActive)
        assertEquals(2, snapshot.remainingCount)
    }

    @Test
    fun disabledTargetsAreNotCountedInStatus() {
        val enabled = config("com.example.enabled", TargetAppType.ENTERTAINMENT_ONLY, TargetAppSource.USER_ADDED)
        val disabled = config("com.example.disabled", TargetAppType.ENTERTAINMENT_ONLY, TargetAppSource.USER_ADDED).copy(isEnabled = false)
        val snapshot = DeveloperCenterPolicy.snapshot(
            state(), listOf(enabled, disabled), DeveloperQuotaLimits(10, 15), true, true, "2026-07-12", 3_000L
        )
        assertEquals(1, snapshot.targetCount)
        assertFalse(snapshot.entertainmentActive)
    }

    @Test
    fun compactPickerOnlyClaimsCentralVerticalGesturesAfterTouchSlop() {
        assertFalse(CompactPickerGesturePolicy.isInteractiveStart(5f, 100))
        assertTrue(CompactPickerGesturePolicy.isInteractiveStart(50f, 100))
        assertEquals(PickerGestureOwner.UNDECIDED, CompactPickerGesturePolicy.resolve(1f, 4f, 8))
        assertEquals(PickerGestureOwner.PICKER, CompactPickerGesturePolicy.resolve(2f, 12f, 8))
        assertEquals(PickerGestureOwner.PAGE, CompactPickerGesturePolicy.resolve(12f, 2f, 8))
    }

    private fun config(
        packageName: String,
        type: TargetAppType,
        source: TargetAppSource,
        addedDate: String? = null,
        removableAfter: String? = null
    ) = TargetAppConfig(packageName, packageName, type, true, type == TargetAppType.HYBRID_STUDY_ENTERTAINMENT, true, source, addedDate, removableAfter)

    private fun state() = GuardState(
        targetPackages = emptySet(), dailyControlPlanDate = null, dailyControlledPackages = emptySet(),
        searchGraceUntil = 0L, searchSessionPackage = null, searchStartedAt = 0L,
        entertainmentStartedAt = 1_000L, entertainmentUntil = 2_000L, entertainmentPackage = "com.xingin.xhs",
        entertainmentDate = "2026-07-12", entertainmentPlanDate = "2026-07-12", todayQuotaDate = "2026-07-12",
        todayQuotaConfirmed = true, defaultEntertainmentDailyLimit = 3, defaultEntertainmentDurationMs = 300_000L,
        entertainmentDailyLimit = 3, entertainmentDurationMs = 300_000L, dailyEntertainmentCount = 1,
        lastTargetPackage = "com.xingin.xhs", lastKeyword = null, lastPlatform = null,
        lastDeepLinkResult = DeepLinkResult.NOT_STARTED, lastInterceptTime = 0L, repeatedScrollThreshold = 3,
        biliRecommendationJumpLimit = 3, guardSettingsDate = null, entertainmentSettingsConfigured = true,
        promptFlowState = PromptFlowState.IDLE, promptPackage = null, promptUpdatedAt = 0L,
        searchReturnCheckPackage = null, searchReturnCheckUntil = 0L
    )
}
