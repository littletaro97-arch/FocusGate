package com.example.focusgate

/**
 * Screen-independent sizing rules for the daily-plan overlay. These are deliberately
 * caps and minimum clearances, not a fixed screen-height layout.
 */
object DailyPlanOverlayLayoutPolicy {
    const val SELECTED_APP_MAX_HEIGHT_DP = 180
    const val QUOTA_WHEEL_HEIGHT_DP = 112
    const val QUOTA_COLUMN_COUNT = 2
    const val MIN_BOTTOM_BAR_CLEARANCE_DP = 148

    fun selectedAppsViewportHeightDp(contentHeightDp: Int): Int =
        contentHeightDp.coerceAtLeast(0).coerceAtMost(SELECTED_APP_MAX_HEIGHT_DP)

    fun selectedAppsNeedInternalScroll(contentHeightDp: Int): Boolean =
        contentHeightDp > SELECTED_APP_MAX_HEIGHT_DP

    fun contentBottomClearanceDp(bottomBarHeightDp: Int): Int =
        maxOf(bottomBarHeightDp, MIN_BOTTOM_BAR_CLEARANCE_DP)
}
